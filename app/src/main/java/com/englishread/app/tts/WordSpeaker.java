package com.englishread.app.tts;

import android.content.Context;
import android.media.AudioAttributes;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

/**
 * 单词朗读：只念一个词的 TTS 壳子。
 *
 * <p>为什么不复用 {@link TtsController}：那套是「听书」的完整状态机——前台服务、
 * 常驻通知、逐词高亮、章节队列。词卡只想说一句两秒钟的话，用它得顺带拉起一条通知，
 * 退出时还要收尾一堆状态，不值当。
 *
 * <p>代价是这里要自己处理一件事：**引擎初始化是异步的**。第一次点喇叭时引擎还没连上，
 * 直接 speak 会静默失败（用户以为按钮是坏的）。所以未就绪时先把词记下来，
 * {@code onInit} 回来再补念——点一次就出声。
 */
public class WordSpeaker {

    private static final String TAG = "WordSpeaker";
    /** 同一个 id 配合 QUEUE_FLUSH：连点同一个词是「重念」，不是排队念两遍。 */
    private static final String UTTERANCE_ID = "word";
    private static final float MIN_RATE = 0.5f;
    private static final float MAX_RATE = 2f;

    public interface Listener {
        /**
         * 引擎起不来。{@code code} 用 {@link TtsController#ERR_NO_ENGINE} 或
         * {@link TtsController#ERR_NO_ENGLISH}，调用方照着弹提示。
         */
        void onWordSpeakerError(int code);
    }

    private final Context app;
    private final Listener listener;

    private TextToSpeech tts;
    private boolean ready;
    private boolean closed;

    /** 引擎还没起来时点的那一下。只留最后一个词——连点几个词，念最后那个才符合直觉。 */
    private String pending;
    private float pendingRate = 1f;

    public WordSpeaker(Context c, Listener listener) {
        this.app = c.getApplicationContext();
        this.listener = listener;
    }

    /**
     * 念一个词。引擎没起好就排队，起好立刻补念；已经关了则什么都不做。
     *
     * @param rate 语速，跟听书用同一档，念词和念正文的手感才一致
     */
    public void speak(String word, float rate) {
        if (closed || word == null || word.isEmpty()) {
            return;
        }
        if (ready) {
            utter(word, rate);
            return;
        }
        pending = word;
        pendingRate = rate;
        if (tts == null) {
            create();
        }
    }

    /** 页面销毁时调。之后 {@link #speak} 一律无声返回。 */
    public void close() {
        closed = true;
        pending = null;
        ready = false;
        if (tts != null) {
            // 还在连的引擎 shutdown() 会静默取消那条连接，不用先判断状态
            tts.stop();
            tts.shutdown();
            tts = null;
        }
    }

    private void create() {
        tts = new TextToSpeech(app, status -> onInit(status));
    }

    /** 在 TTS 的初始化回调（主线程）里跑。 */
    private void onInit(int status) {
        if (closed) {
            // 页都关了引擎才连上：直接放掉，别留一条 binder 连接
            if (tts != null) {
                tts.shutdown();
                tts = null;
            }
            return;
        }
        if (status != TextToSpeech.SUCCESS) {
            fail(TtsController.ERR_NO_ENGINE, "init failed: " + status);
            return;
        }
        int lang = tts.setLanguage(Locale.US);
        if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
            fail(TtsController.ERR_NO_ENGLISH, "english voice unavailable: " + lang);
            return;
        }
        tts.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
        ready = true;
        Log.i(TAG, "engine ready, pkg=" + tts.getDefaultEngine() + " lang=" + lang);

        if (pending != null) {
            String w = pending;
            float r = pendingRate;
            pending = null;
            utter(w, r);
        }
    }

    private void utter(String word, float rate) {
        tts.setSpeechRate(Math.max(MIN_RATE, Math.min(MAX_RATE, rate)));
        int r = tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID);
        if (r != TextToSpeech.SUCCESS) {
            fail(TtsController.ERR_SPEAK, "speak returned " + r + " for " + word);
        }
    }

    /**
     * 失败就彻底放弃这个引擎：下次点喇叭重新连一次，比留着一个半死的实例更可能好。
     * 提示只弹一次（pending 清空后不会重复）。
     */
    private void fail(int code, String why) {
        Log.w(TAG, why);
        pending = null;
        ready = false;
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
        if (listener != null) {
            listener.onWordSpeakerError(code);
        }
    }
}
