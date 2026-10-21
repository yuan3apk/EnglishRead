package com.englishread.app.trans;

import android.os.Handler;
import android.os.Looper;

import com.englishread.app.data.ParagraphDao;
import com.englishread.app.model.Paragraph;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 段落译文队列：批量请求 + 缓存跳过 + 失败落库 + 回主线程通知。
 *
 * <p>控成本的命门是「一次生成永久复用」（HANDOFF 2.2）：已经有译文的段落调用方不该再送进来；
 * 同一段在飞行中时再点只挂回调，不重复发请求。
 *
 * <p>不做后台预取：进程被杀就停，下次打开章节自然续上（HANDOFF R5）。
 * 预取在当前实现里等于「用户没点也在花钱」，等真有需求再说。
 */
public class TransQueue {

    /** 回主线程的结果。{@code results} 里只有成功的段有译文。 */
    public interface Callback {
        void onResult(List<Paragraph> done, String error);
    }

    private final ParagraphDao dao;
    private final LlmClient client;
    private final Executor io;
    private final Handler main = new Handler(Looper.getMainLooper());

    /** 正在飞行中的段落 id，防止同一段被连点发出两次请求。 */
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    /** 页面销毁时取消，别把译文写回已经关掉的界面。 */
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public TransQueue(ParagraphDao dao, LlmClient client, Executor io) {
        this.dao = dao;
        this.client = client;
        this.io = io;
    }

    /** 这段是不是还欠一次请求（已译的直接复用，不花钱）。 */
    public static boolean needsWork(Paragraph p) {
        return p.transStatus != Paragraph.STATUS_DONE || p.trans == null;
    }

    /**
     * 译一批段落。入参可以超过 {@link TransProtocol#MAX_BATCH}，这里自己切批——
     * 批次上限属于协议约定，不该由每个调用方各记一遍。
     * 回调在主线程；一段都没译成时 {@code done} 为空、{@code error} 有值。
     */
    public void translate(List<Paragraph> batch, Callback cb) {
        if (cancelled.get() || batch == null || batch.isEmpty()) {
            return;
        }
        // 已在飞行中的段落这次不重复请求
        final List<Paragraph> queue = new ArrayList<>();
        for (Paragraph p : batch) {
            if (inFlight.add(p.id)) {
                queue.add(p);
            }
        }
        if (queue.isEmpty()) {
            return;
        }

        io.execute(() -> {
            List<Paragraph> done = new ArrayList<>();
            String error = null;
            try {
                for (int from = 0; from < queue.size(); from += TransProtocol.MAX_BATCH) {
                    if (cancelled.get()) {
                        error = "已取消";
                        break;
                    }
                    int to = Math.min(from + TransProtocol.MAX_BATCH, queue.size());
                    List<Paragraph> chunk = new ArrayList<>(queue.subList(from, to));
                    String e = runBatch(chunk, done);
                    if (e != null) {
                        error = e;
                    }
                }
            } catch (RuntimeException e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            } finally {
                for (Paragraph p : queue) {
                    inFlight.remove(p.id);
                }
            }
            final List<Paragraph> out = done;
            final String err = error;
            main.post(() -> {
                if (!cancelled.get()) {
                    cb.onResult(out, err);
                }
            });
        });
    }

    /**
     * 跑一批（≤ MAX_BATCH）。模型漏段时只把缺的那几段补发一次——
     * 整批重发等于为已经翻好的段落付两次钱。
     *
     * <p>无论成败，没译出来的段落一律落 {@code STATUS_FAILED}：
     * 只有落库了界面上才有「重试」可点，否则失败是静默的。
     */
    private String runBatch(List<Paragraph> chunk, List<Paragraph> done) {
        String error = translateInto(chunk, done);

        // done 是跨批累积的，所以这里必须按 chunk 自己数，不能拿 done.size() 比
        if (error == null && pending(chunk) > 0) {
            error = translateInto(pendingOf(chunk), done);
        }

        for (Paragraph p : chunk) {
            if (needsWork(p)) {
                p.transStatus = Paragraph.STATUS_FAILED;
                if (!cancelled.get()) {
                    dao.saveTranslation(p.id, p.trans, Paragraph.STATUS_FAILED);
                }
            }
        }
        return error != null ? error : (pending(chunk) == chunk.size() ? "翻译失败" : null);
    }

    /** 这一批里还欠译文的段落数。跨批累积，所以只能按 chunk 自己数。 */
    private static int pending(List<Paragraph> chunk) {
        return pendingOf(chunk).size();
    }

    /**
     * 这一批里还没译出来的段落。
     *
     * <p>补发只发这些——整批重发等于为已经翻好的段落付两次钱，
     * 这是 M3 成本控制的核心，所以单独抽出来当纯函数好单测。
     */
    public static List<Paragraph> pendingOf(List<Paragraph> chunk) {
        List<Paragraph> out = new ArrayList<>();
        for (Paragraph p : chunk) {
            if (needsWork(p)) {
                out.add(p);
            }
        }
        return out;
    }

    /** 发一次请求并回写；返回错误文案或 null。 */
    private String translateInto(List<Paragraph> batch, List<Paragraph> done) {
        List<String> texts = new ArrayList<>(batch.size());
        for (Paragraph p : batch) {
            texts.add(p.text);
        }
        LlmClient.Result r = client.translate(texts, cancelled::get);
        if (!r.ok) {
            return r.error;
        }
        for (int i = 0; i < batch.size(); i++) {
            String t = r.translations[i];
            if (t == null) {
                continue;
            }
            Paragraph p = batch.get(i);
            p.trans = t;
            p.transStatus = Paragraph.STATUS_DONE;
            done.add(p);
            if (!cancelled.get()) {
                dao.saveTranslation(p.id, t, Paragraph.STATUS_DONE);
            }
        }
        return null;
    }

    public void cancel() {
        cancelled.set(true);
    }
}
