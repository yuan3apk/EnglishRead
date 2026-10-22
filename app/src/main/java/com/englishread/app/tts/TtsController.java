package com.englishread.app.tts;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import com.englishread.app.R;
import com.englishread.app.data.Db;
import com.englishread.app.data.ParagraphDao;
import com.englishread.app.model.Chapter;
import com.englishread.app.model.Paragraph;
import com.englishread.app.reader.SentenceIndex;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 听书的引擎与队列。整个播放状态都在这里，{@link TtsService} 只负责前台服务与通知。
 *
 * <p>发音单元是**一句**：正文入库时已经是一句话一段，这里再按 {@link SentenceIndex}
 * 做一层兜底（老书的整段、超长句），所以「下一句」正好是一段。
 *
 * <p>一次只往引擎里塞一句（{@code QUEUE_FLUSH}）：这样 {@code onDone} 就是唯一的前进信号，
 * 不会出现「队列里还有五句，暂停后它们还接着念」的问题；句间那几十毫秒的空隙
 * 反而让朗读更像人。多句预排是性能优化，等有需要再说。
 *
 * <p>所有状态只在主线程改；读库走自己的单线程池——{@code App.io()} 是共享单线程，
 * 翻译请求在网络上有 20 秒超时，排在它后面会让章节切换卡半分钟。
 */
public class TtsController {

    private static final String TAG = "TtsController";

    public static final int STATE_IDLE = 0;
    public static final int STATE_PLAYING = 1;
    public static final int STATE_PAUSED = 2;

    public static final int ERR_NO_ENGINE = 1;
    public static final int ERR_NO_ENGLISH = 2;
    public static final int ERR_SPEAK = 3;

    private static final int INIT_FRESH = 0;
    private static final int INIT_CREATING = 2;
    private static final int INIT_READY = 1;
    private static final int INIT_FAILED = -1;

    /**
     * 引擎初始化看门狗。厂商 ROM 会把后台的文字转语音进程冻住（ColorOS 实测：
     * {@code am_freeze com.oplus.ttsaccessibilityengine}），这时 bind 可能迟迟不回 onInit。
     * 没有这道超时，界面会永远停在「准备朗读」且不报错。
     */
    private static final long INIT_TIMEOUT_MS = 8_000;

    public interface Listener {
        void onState(int state);

        /** 开始念新的一句（或换章）。文案、高亮都从 {@link #snapshot()} 取。 */
        void onMoved();

        /** 逐词高亮：{@code paraId} 段内的 [start, end)。引擎不支持时是整句区间。 */
        void onHighlight(long paraId, int start, int end);

        void onError(int code);

        /** 整本书读完（不是用户按停止）。 */
        void onFinished();
    }

    /** 引擎错误码 → 给人看的一句话。听书和词卡共用，省得两处文案走散。 */
    public static String errorText(Context c, int code) {
        switch (code) {
            case ERR_NO_ENGINE:
                return c.getString(R.string.tts_no_engine);
            case ERR_NO_ENGLISH:
                return c.getString(R.string.tts_no_english);
            default:
                return c.getString(R.string.tts_speak_failed);
        }
    }

    /** 给界面/通知用的一次性快照。字段多但都是标量，别在这里放列表。 */
    public static final class Snapshot {
        public int state = STATE_IDLE;
        public long bookId = -1;
        public int chapterIdx;
        public String chapterTitle = "";
        public int paraIdx;
        public long paraId = -1;
        public int start;
        public int end;
        public float rate = 1f;
        public String preview = "";
    }

    private final Context ctx;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService db = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "englishread-tts-db");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    private TextToSpeech tts;
    private int init = INIT_FRESH;
    private int initError;

    private long bookId = -1;
    private List<Chapter> chapters;
    private int chapterIdx;
    private List<Paragraph> paragraphs;
    private int paraIdx;
    private int chunkIdx;
    private Paragraph currentPara;
    private Paragraph chunkPara;
    private List<int[]> chunkList;
    private String currentUid;
    private int chunkStart;
    private int hlStart;
    private int hlEnd;
    private int state = STATE_IDLE;
    private float rate = 1f;
    private int loadToken;

    /** 引擎还在初始化时收到的播放请求，初始化完成后再执行。 */
    private boolean pendingStart;
    private long pendingBook;
    private int pendingChapter;
    private int pendingPara;
    /** 本次点「听书」已经重试过一次引擎了（冷启动/被冻结时给第二次机会）。 */
    private boolean initRetried;

    public TtsController(Context c, Listener listener) {
        this.ctx = c.getApplicationContext();
        this.listener = listener;
    }

    // ---- 对外命令 ----

    /**
     * 从指定章节/段落开始念。
     *
     * @param paraIdx 段落下标；< 0 表示本章最后一段（「上一章」用）
     */
    public void start(long bookId, int chapterIdx, int paraIdx) {
        pendingStart = true;
        pendingBook = bookId;
        pendingChapter = chapterIdx;
        pendingPara = paraIdx;
        initRetried = false; // 用户又点了一次：给它完整的一次重试机会

        if (init == INIT_READY) {
            pendingStart = false;
            load(bookId, chapterIdx, paraIdx);
            return;
        }
        if (init == INIT_FAILED) {
            // 上次失败多半是没装引擎 / 被冻住。用户可能刚装完或刚回来，重建一次再试
            shutdownEngine();
            init = INIT_FRESH;
        }
        ensureEngine();
    }

    public void pause() {
        if (state != STATE_PLAYING) {
            return;
        }
        stopSpeech();
        setState(STATE_PAUSED);
    }

    public void resume() {
        if (state != STATE_PAUSED) {
            return;
        }
        setState(STATE_PLAYING);
        speakCurrent();
    }

    /** 下一句；到章尾顺延到下一章。 */
    public void next() {
        if (state == STATE_IDLE) {
            return;
        }
        stopSpeech();
        setState(STATE_PLAYING);
        chunkIdx++;
        speakCurrent();
    }

    /** 上一句。段内还有片段就退一个片段，否则退一段（老书的整段只能退到段首）。 */
    public void prev() {
        if (state == STATE_IDLE) {
            return;
        }
        stopSpeech();
        setState(STATE_PLAYING);
        if (chunkIdx > 0) {
            chunkIdx--;
            speakCurrent();
            return;
        }
        if (paraIdx > 0) {
            paraIdx--;
            chunkIdx = 0;
            speakCurrent();
            return;
        }
        if (chapterIdx > 0) {
            load(bookId, chapterIdx - 1, -1);
            return;
        }
        speakCurrent(); // 已经是第一句：从头再念
    }

    public void stop() {
        if (state == STATE_IDLE) {
            return;
        }
        stopSpeech();
        setState(STATE_IDLE);
    }

    public void setRate(float r) {
        rate = Math.max(0.5f, Math.min(2.0f, r));
        if (tts != null) {
            // 只对「下一句」生效：正在念的那句不会中途变速（引擎没有这个能力）
            tts.setSpeechRate(rate);
        }
    }

    public Snapshot snapshot() {
        Snapshot s = new Snapshot();
        s.state = state;
        s.bookId = bookId;
        s.chapterIdx = chapterIdx;
        s.chapterTitle = title();
        s.paraIdx = paraIdx;
        s.start = hlStart;
        s.end = hlEnd;
        s.rate = rate;
        if (currentPara != null) {
            s.paraId = currentPara.id;
            s.preview = preview(currentPara.text);
        }
        return s;
    }

    /** 服务销毁时调用：停声、关引擎、收线程。 */
    public void release() {
        loadToken++;
        pendingStart = false;
        currentUid = null;
        main.removeCallbacks(initWatchdog);
        db.shutdownNow();
        shutdownEngine();
    }

    // ---- 引擎 ----

    private void ensureEngine() {
        if (init == INIT_READY || init == INIT_CREATING) {
            return;
        }
        init = INIT_CREATING;
        try {
            // 用数组兜住实例：回调必须认准「这一轮」的引擎，否则超时重试时
            // 上一轮（已被取消）的 onInit 迟到，会把新引擎标成失败。
            final TextToSpeech[] holder = new TextToSpeech[1];
            holder[0] = new TextToSpeech(ctx, status -> main.post(() -> onInit(holder[0], status)));
            tts = holder[0];
            main.removeCallbacks(initWatchdog);
            main.postDelayed(initWatchdog, INIT_TIMEOUT_MS);
            Log.i(TAG, "engine creating, default=" + tts.getDefaultEngine());
        } catch (RuntimeException e) {
            Log.w(TAG, "engine create failed", e);
            init = INIT_FAILED;
            initError = ERR_NO_ENGINE;
            reportPendingError();
        }
    }

    private final Runnable initWatchdog = this::onInitTimeout;

    private void onInitTimeout() {
        if (init != INIT_CREATING) {
            return; // 已经就绪或已经失败：超时器是上一轮的残留
        }
        if (!initRetried) {
            initRetried = true;
            Log.w(TAG, "engine init timed out, retry once");
            shutdownEngine(); // init==CREATING：shutdown() 会静默取消那条还在连的连接
            init = INIT_FRESH;
            ensureEngine();
            return;
        }
        Log.w(TAG, "engine init timed out twice, give up");
        // 先读默认引擎再 shutdown：shutdownEngine() 会把 tts 置空，日志就只剩 default=null
        logEngineState("engine init timed out (no onInit in " + INIT_TIMEOUT_MS + "ms)");
        shutdownEngine();
        init = INIT_FAILED;
        initError = ERR_NO_ENGINE;
        reportPendingError();
    }

    private void onInit(TextToSpeech engine, int status) {
        if (engine != tts) {
            // 上一轮被取消/替换的引擎。注意：shutdown() 取消的是「还在连」的那条连接，
            // 但框架的异步连接仍可能事后建立（API 36 实测有 "Connected to TTS engine"），
            // 所以这里对迟到的客户端再 shutdown 一次，别每次超时都漏一条 binder 连接。
            engine.shutdown();
            return;
        }
        main.removeCallbacks(initWatchdog);
        if (status != TextToSpeech.SUCCESS) {
            init = INIT_FAILED;
            initError = ERR_NO_ENGINE;
            logEngineState("engine init failed: " + status);
            reportPendingError();
            return;
        }
        int lang = tts.setLanguage(Locale.US);
        if (lang == TextToSpeech.LANG_MISSING_DATA
                || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
            init = INIT_FAILED;
            initError = ERR_NO_ENGLISH;
            Log.w(TAG, "english voice unavailable: " + lang + ", default=" + tts.getDefaultEngine());
            reportPendingError();
            return;
        }
        tts.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
        tts.setSpeechRate(rate);
        tts.setOnUtteranceProgressListener(progress);
        init = INIT_READY;
        Log.i(TAG, "engine ready, pkg=" + tts.getDefaultEngine() + " lang=" + lang);

        if (pendingStart) {
            pendingStart = false;
            load(pendingBook, pendingChapter, pendingPara);
        }
    }

    private void reportPendingError() {
        if (!pendingStart) {
            return; // 用户没点朗读：别在他只是打开书的时候就弹「没有语音引擎」
        }
        pendingStart = false;
        setState(STATE_IDLE);
        listener.onError(initError);
    }

    /**
     * 引擎起不来时把「应用进程究竟看得见什么」一次打全：默认引擎包名、能解析到的引擎个数与包名。
     *
     * <p>这三段拼起来就是分诊结论，别再笼统当成「没装引擎」：
     * <ul>
     *   <li>{@code default=null} + {@code engines=0}：真的没有可用引擎（没装 / 被停用）；</li>
     *   <li>{@code default=包名} + {@code engines=0}：**包可见性**——manifest 缺 TTS_SERVICE 的
     *       {@code <queries>}，应用进程看不见这个包，bind 直接被拦，框架立刻回调
     *       {@code onInit(-1)}（OPPO PJW110 / Android 16 实测，加完声明即恢复）；</li>
     *   <li>{@code engines=N>0} 但 init 仍失败：引擎自身的问题（被冻结 / 崩溃 / 权限）。</li>
     * </ul>
     */
    private void logEngineState(String why) {
        String dflt;
        try {
            dflt = tts == null ? null : tts.getDefaultEngine();
        } catch (RuntimeException e) {
            dflt = "<" + e.getClass().getSimpleName() + ">";
        }
        StringBuilder sb = new StringBuilder(why).append(", default=").append(dflt);
        try {
            List<ResolveInfo> list = ctx.getPackageManager().queryIntentServices(
                    new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0);
            sb.append(", engines=").append(list.size());
            for (ResolveInfo ri : list) {
                if (ri.serviceInfo != null) {
                    sb.append(" [").append(ri.serviceInfo.packageName).append(']');
                }
            }
            if (list.isEmpty()) {
                sb.append(" —— 应用进程看不到任何引擎：没装/被停用，或 manifest 缺 ");
                sb.append("TTS_SERVICE 的 <queries> 声明");
            }
        } catch (RuntimeException e) {
            sb.append(", query engines failed: ").append(e);
        }
        Log.w(TAG, sb.toString());
    }

    /**
     * 按状态收尾，别对着一个没连上的客户端乱调 API。
     *
     * <p>{@code stop()} / {@code shutdown()} 在「客户端还没连上」时会各打一条
     * {@code ... failed: not bound to TTS engine} 的 W，看起来像新故障，其实只是收尾噪音。
     * 源码里 {@code shutdown()} 对「还在连」的那条连接有单独分支（静默 disconnect 后返回），
     * 所以只有两种状态值得调：
     * <ul>
     *   <li>READY：正常 stop + shutdown；</li>
     *   <li>CREATING：只 shutdown，用来取消那条还在连的连接；</li>
     *   <li>FAILED / FRESH：连接已经结束，没有可释放的东西，调用只会刷警告。</li>
     * </ul>
     */
    private void shutdownEngine() {
        if (tts == null) {
            return;
        }
        if (init == INIT_READY) {
            tts.stop();
            tts.shutdown();
        } else if (init == INIT_CREATING) {
            tts.shutdown();
        }
        tts = null;
    }

    // ---- 队列 ----

    private void load(final long wantBook, final int wantChapter, final int wantPara) {
        final int token = ++loadToken;
        Log.i(TAG, "load book=" + wantBook + " ch=" + wantChapter + " para=" + wantPara);
        db.execute(() -> {
            ParagraphDao dao = new ParagraphDao(Db.of(ctx));
            List<Chapter> cs = dao.chapters(wantBook);
            if (cs.isEmpty()) {
                main.post(() -> fail(ERR_SPEAK));
                return;
            }
            int ch = Math.max(0, Math.min(wantChapter, cs.size() - 1));
            List<Paragraph> ps = dao.paragraphs(cs.get(ch).id);
            main.post(() -> {
                if (token != loadToken) {
                    return; // 期间用户又换了章/换了书
                }
                bookId = wantBook;
                chapters = cs;
                chapterIdx = ch;
                paragraphs = ps;
                chunkPara = null;
                chunkList = null;
                chunkIdx = 0;
                paraIdx = wantPara < 0
                        ? Math.max(0, ps.size() - 1)
                        : Math.max(0, Math.min(wantPara, ps.size() - 1));
                setState(STATE_PLAYING);
                speakCurrent();
            });
        });
    }

    /**
     * 念当前 {paragraph, chunk}。空段/空章在这里跳过，章尾自动进下一章；
     * 全书读完走 {@link #finish()}。
     */
    private void speakCurrent() {
        if (init != INIT_READY || tts == null) {
            return;
        }
        while (true) {
            if (paragraphs == null) {
                return;
            }
            if (paraIdx >= paragraphs.size()) {
                if (chapters != null && chapterIdx + 1 < chapters.size()) {
                    load(bookId, chapterIdx + 1, 0);
                } else {
                    finish();
                }
                return;
            }
            Paragraph p = paragraphs.get(paraIdx);
            if (p.text == null || p.text.trim().isEmpty()) {
                paraIdx++;
                chunkIdx = 0;
                continue;
            }
            if (chunkPara != p) {
                chunkPara = p;
                chunkList = SentenceIndex.chunks(p.text);
            }
            if (chunkList.isEmpty() || chunkIdx >= chunkList.size()) {
                paraIdx++;
                chunkIdx = 0;
                continue;
            }

            int[] r = chunkList.get(chunkIdx);
            String utterance = p.text.substring(r[0], r[1]);
            currentPara = p;
            currentUid = uid();
            chunkStart = r[0];
            hlStart = r[0];
            hlEnd = r[1];
            tts.setSpeechRate(rate);
            int result = tts.speak(utterance, TextToSpeech.QUEUE_FLUSH, new Bundle(), currentUid);
            if (result != TextToSpeech.SUCCESS) {
                Log.w(TAG, "speak failed: " + result);
                fail(ERR_SPEAK);
                return;
            }
            setState(STATE_PLAYING);
            listener.onMoved();
            listener.onHighlight(p.id, r[0], r[1]);
            return;
        }
    }

    private void finish() {
        Log.i(TAG, "book finished");
        currentUid = null;
        setState(STATE_IDLE);
        listener.onFinished();
    }

    private void fail(int code) {
        currentUid = null;
        setState(STATE_IDLE);
        listener.onError(code);
    }

    private void stopSpeech() {
        currentUid = null;
        if (tts != null) {
            tts.stop();
        }
    }

    private void setState(int s) {
        if (state == s) {
            return;
        }
        state = s;
        Log.i(TAG, "state -> " + s);
        listener.onState(s);
    }

    private final UtteranceProgressListener progress = new UtteranceProgressListener() {
        @Override
        public void onStart(String utteranceId) {
            // 有些引擎先报 onStart 再报 range；高亮已经按整句给过了，这里不用做事
        }

        @Override
        public void onDone(String utteranceId) {
            main.post(() -> onDoneMain(utteranceId));
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onError(String utteranceId) {
            main.post(() -> onErrorMain(utteranceId));
        }

        @Override
        public void onError(String utteranceId, int errorCode) {
            main.post(() -> onErrorMain(utteranceId));
        }

        @Override
        public void onRangeStart(String utteranceId, int start, int end, int frame) {
            main.post(() -> onRangeMain(utteranceId, start, end));
        }
    };

    private void onDoneMain(String utteranceId) {
        if (state != STATE_PLAYING || utteranceId == null || !utteranceId.equals(currentUid)) {
            return; // 被打断的那句：迟到的回调不能推进队列
        }
        chunkIdx++;
        speakCurrent();
    }

    private void onErrorMain(String utteranceId) {
        if (utteranceId != null && !utteranceId.equals(currentUid)) {
            return;
        }
        Log.w(TAG, "utterance error: " + utteranceId);
        fail(ERR_SPEAK);
    }

    private void onRangeMain(String utteranceId, int start, int end) {
        if (state != STATE_PLAYING || currentPara == null
                || utteranceId == null || !utteranceId.equals(currentUid)) {
            return;
        }
        String text = currentPara.text;
        int s = chunkStart + start;
        int e = chunkStart + end;
        if (text == null || s < 0 || e > text.length() || e <= s) {
            return;
        }
        hlStart = s;
        hlEnd = e;
        listener.onHighlight(currentPara.id, s, e);
    }

    // ---- 小工具 ----

    private String uid() {
        return chapterIdx + ":" + paraIdx + ":" + chunkIdx;
    }

    private String title() {
        if (chapters == null || chapterIdx < 0 || chapterIdx >= chapters.size()) {
            return "";
        }
        String t = chapters.get(chapterIdx).title;
        return t == null ? "" : t;
    }

    private static String preview(String text) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        return t.length() <= 160 ? t : t.substring(0, 160) + "…";
    }
}
