package com.englishread.app.reader;

import android.graphics.Rect;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.englishread.app.R;
import com.englishread.app.model.Paragraph;
import com.englishread.app.parse.Tokenizer;

import java.util.ArrayList;
import java.util.List;

/**
 * 段落列表。一行一段：正文 TextView（纯原文）+ 独立的小号「段」按钮 + 内联译文。
 *
 * <p>硬约束（HANDOFF D1）：{@code para_text} 的 text 从入库到显示全程只含原文。
 * 「段」按钮是兄弟 View，永不拼进字符串——这样 TTS、复制本段、句子偏移表都不用做减法。
 */
public class ReaderAdapter extends RecyclerView.Adapter<ReaderAdapter.VH> {

    public interface Listener {
        /** 点「段」按钮：展开/收起整段译文。 */
        void onTranslateToggle(Paragraph p);

        /** 译文失败后的重试。 */
        void onTranslateRetry(Paragraph p);

        void onWordTap(Paragraph p, String word, int start, int end);

        void onWordLongPress(Paragraph p, String word, int start, int end);
    }

    private final List<Paragraph> items = new ArrayList<>();
    private final Listener listener;
    /** 哪些段的译文当前是展开的，用 id 记，复用后不会串。 */
    private final java.util.Set<Long> expanded = new java.util.HashSet<>();

    public ReaderAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Paragraph> paragraphs) {
        items.clear();
        items.addAll(paragraphs);
        expanded.clear();
        for (Paragraph p : items) {
            if (p.transStatus == Paragraph.STATUS_DONE && p.trans != null) {
                expanded.add(p.id);
            }
        }
        notifyDataSetChanged();
    }

    /** 译文回写后局部刷新这一段，不用整表重绑。 */
    public void updateParagraph(Paragraph p) {
        int i = indexOf(p.id);
        if (i < 0) {
            return;
        }
        if (p.transStatus == Paragraph.STATUS_DONE && p.trans != null) {
            expanded.add(p.id);
        }
        notifyItemChanged(i);
    }

    private int indexOf(long id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id == id) {
                return i;
            }
        }
        return -1;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_paragraph, parent, false);
        return new VH(v, this);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Paragraph p = items.get(position);

        // 只放原文，任何装饰都不许拼进来
        h.text.setText(p.text);
        ensureTokens(p);

        h.gestures.setTokens(p.tokenStarts, p.tokenEnds, p.tokenTexts);
        h.text.setOnTouchListener(h.gestures);

        h.marker.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTranslateToggle(p);
            }
        });
        expandTouchTarget(h.marker);

        boolean showTrans = expanded.contains(p.id)
                && p.transStatus == Paragraph.STATUS_DONE
                && p.trans != null;
        h.trans.setText(showTrans ? p.trans : null);
        h.trans.setVisibility(showTrans ? View.VISIBLE : View.GONE);

        boolean failed = p.transStatus == Paragraph.STATUS_FAILED;
        h.retry.setVisibility(failed ? View.VISIBLE : View.GONE);
        h.retry.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTranslateRetry(p);
            }
        });
    }

    /** 首次绑定时算词区间，之后复用缓存——每段每帧重跑正则不值得。 */
    private static void ensureTokens(Paragraph p) {
        if (p.tokenStarts != null) {
            return;
        }
        int n = Tokenizer.count(p.text);
        p.tokenStarts = new int[n];
        p.tokenEnds = new int[n];
        p.tokenTexts = new String[n];
        Tokenizer.tokenize(p.text, p.tokenStarts, p.tokenEnds, p.tokenTexts);
    }

    /**
     * 视觉 11sp 很小，触摸区撑到 48dp——无障碍最小点击尺寸。
     * 用 TouchDelegate 扩算出来的 rect，不改布局尺寸。
     */
    private static void expandTouchTarget(View marker) {
        final View parent = (View) marker.getParent();
        parent.post(() -> {
            Rect r = new Rect();
            marker.getHitRect(r);
            int minPx = (int) (48 * marker.getResources().getDisplayMetrics().density);
            int dx = Math.max(0, (minPx - r.width()) / 2);
            int dy = Math.max(0, (minPx - r.height()) / 2);
            // 只向上和左右扩，别盖到下一段正文
            r.left -= dx;
            r.right += dx;
            r.top -= dy;
            parent.setTouchDelegate(new TouchDelegate(r, marker));
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class VH extends RecyclerView.ViewHolder {
        public final TextView text;
        final TextView trans;
        final TextView retry;
        final TextView marker;
        final ParagraphGestures gestures;

        VH(View v, ReaderAdapter adapter) {
            super(v);
            text = v.findViewById(R.id.para_text);
            trans = v.findViewById(R.id.para_trans);
            retry = v.findViewById(R.id.para_retry);
            marker = v.findViewById(R.id.btn_translate);
            gestures = new ParagraphGestures(text, new ParagraphGestures.Listener() {
                @Override
                public void onWordTap(String word, int start, int end) {
                    int pos = getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION && adapter.listener != null) {
                        adapter.listener.onWordTap(adapter.items.get(pos), word, start, end);
                    }
                }

                @Override
                public void onWordLongPress(String word, int start, int end) {
                    int pos = getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        adapter.listener.onWordLongPress(adapter.items.get(pos), word, start, end);
                    }
                }
            });
        }
    }
}
