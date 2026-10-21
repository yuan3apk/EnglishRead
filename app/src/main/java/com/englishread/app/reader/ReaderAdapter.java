package com.englishread.app.reader;

import android.graphics.Rect;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.view.LayoutInflater;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.englishread.app.R;
import com.englishread.app.model.Paragraph;
import com.englishread.app.parse.Tokenizer;
import com.google.android.material.color.MaterialColors;

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

        /** 点在空白上（正文外、句与句的缝里）：呼出/收起菜单。 */
        void onBlankTap();
    }

    private final List<Paragraph> items = new ArrayList<>();
    private final Listener listener;
    /** 哪些段的译文当前是展开的，用 id 记，复用后不会串。 */
    private final java.util.Set<Long> expanded = new java.util.HashSet<>();
    /** 正在请求中的段落 id：显示「翻译中」，并挡住重复点。 */
    private final java.util.Set<Long> pending = new java.util.HashSet<>();

    private int textSizeSp = 18;
    private float lineSpacingMult = 1.5f;
    private boolean showMarker = true;
    private int paraSpacingDp = 16;
    private float letterSpacingEm;
    private int indentChars;

    /** 列表本体：高亮更新要能直接拿到可见的 ViewHolder，不必整项重绑。 */
    @Nullable
    private RecyclerView recycler;
    /** 当前朗读到的那一段与段内字符区间；-1 / 空区间表示没有高亮。 */
    private long hlParaId = -1;
    private int hlStart;
    private int hlEnd;

    public ReaderAdapter(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView rv) {
        recycler = rv;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView rv) {
        recycler = null;
    }

    /** 设置面板改完调这个：整表重绑，字号行距才会立刻生效。 */
    public void applyDisplay(ReaderPrefs prefs) {
        this.textSizeSp = prefs.textSize();
        this.lineSpacingMult = prefs.lineSpacing();
        this.showMarker = prefs.showMarker();
        this.paraSpacingDp = prefs.paraSpacing();
        this.letterSpacingEm = prefs.letterSpacing();
        this.indentChars = prefs.indent();
        notifyDataSetChanged();
    }

    public void submit(List<Paragraph> paragraphs) {
        items.clear();
        items.addAll(paragraphs);
        expanded.clear();
        pending.clear();
        // 默认不展开：进来时译文一律收着，只有用户点了「段」的那一段才显出来。
        // 之前这里会把所有「已译」的段一次性摊开，等于一进章节就满屏译文。
        notifyDataSetChanged();
    }

    /** 标记一组段落「翻译中」，整批重绑一次即可。 */
    public void setPending(List<Paragraph> batch, boolean inFlight) {
        for (Paragraph p : batch) {
            if (inFlight) {
                pending.add(p.id);
            } else {
                pending.remove(p.id);
            }
        }
        for (Paragraph p : batch) {
            int i = indexOf(p.id);
            if (i >= 0) {
                notifyItemChanged(i);
            }
        }
    }

    /** 译文回写后局部刷新这一段，不用整表重绑。 */
    public void updateParagraph(Paragraph p) {
        int i = indexOf(p.id);
        if (i < 0) {
            return;
        }
        pending.remove(p.id);
        // 译文回来后自动展开——用户点「段」的意图就是要看译文
        if (p.transStatus == Paragraph.STATUS_DONE && p.trans != null) {
            expanded.add(p.id);
        }
        notifyItemChanged(i);
    }

    /** 已有译文时的展开/收起：不碰网络，纯显隐。 */
    public void toggleExpand(Paragraph p) {
        if (expanded.contains(p.id)) {
            expanded.remove(p.id);
        } else {
            expanded.add(p.id);
        }
        int i = indexOf(p.id);
        if (i >= 0) {
            notifyItemChanged(i);
        }
    }

    /** 某一段在列表里的位置；不在当前章节返回 -1。 */
    public int positionOf(long paraId) {
        return indexOf(paraId);
    }

    /**
     * 听书逐词高亮：{@code paraId} 段的 [start, end) 字符区间上底色。
     *
     * <p>走「改 span」而不是 {@code notifyItemChanged}：逐词回调每秒好几次，
     * 整项重绑会把每次高亮都变成一次布局。背景色/前景色 span 不改变排版，
     * 只触发重绘。词卡与「段」按钮不受影响（它们不在 {@code para_text} 里）。
     */
    public void setHighlight(long paraId, int start, int end) {
        if (paraId <= 0 || end <= start) {
            clearHighlight();
            return;
        }
        if (paraId == hlParaId && start == hlStart && end == hlEnd) {
            return;
        }
        long old = hlParaId;
        hlParaId = paraId;
        hlStart = start;
        hlEnd = end;
        if (old != paraId) {
            repaint(old);
        }
        repaint(paraId);
    }

    public void clearHighlight() {
        if (hlParaId <= 0) {
            return;
        }
        long old = hlParaId;
        hlParaId = -1;
        hlStart = 0;
        hlEnd = 0;
        repaint(old);
    }

    private void repaint(long paraId) {
        if (paraId <= 0 || recycler == null) {
            return;
        }
        int i = indexOf(paraId);
        if (i < 0) {
            return;
        }
        RecyclerView.ViewHolder vh = recycler.findViewHolderForAdapterPosition(i);
        if (vh instanceof VH) {
            paintHighlight(((VH) vh).text, items.get(i));
        }
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
        bindContent(h, p);

        h.gestures.setTokens(p.tokenStarts, p.tokenEnds, p.tokenTexts);
        h.text.setOnTouchListener(h.gestures);
        h.marker.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTranslateToggle(p);
            }
        });
        if (showMarker) {
            expandTouchTarget(h.marker);
        }
        h.retry.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTranslateRetry(p);
            }
        });
    }

    /** 真正的排版：正文样式、缩进、段距、译文与各种提示的显隐。 */
    private void bindContent(VH h, Paragraph p) {
        // 只放原文，任何装饰都不许拼进来
        h.text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        // 衬线在这里显式设：写在 style / textAppearance 里都会被主题那套无衬线盖掉
        // （实测正文一直是 Roboto，换了两种写法都一样）。正文是英文，衬线才是「书」。
        h.text.setTypeface(android.graphics.Typeface.SERIF);
        h.text.setLineSpacing(0f, lineSpacingMult);
        h.text.setLetterSpacing(letterSpacingEm);
        applySpans(h.text, p);
        applyParaSpacing(h.itemView);
        ensureTokens(p);

        // 设置里可以整体隐藏翻译入口（隐藏后就没有段落翻译）
        h.marker.setVisibility(showMarker ? View.VISIBLE : View.GONE);

        boolean showTrans = expanded.contains(p.id)
                && p.transStatus == Paragraph.STATUS_DONE
                && p.trans != null;
        h.trans.setText(showTrans ? p.trans : null);
        h.trans.setVisibility(showTrans ? View.VISIBLE : View.GONE);

        // 三种状态互斥：翻译中 > 失败重试 > 无提示。都叠着会让人不知道点哪个
        boolean inFlight = pending.contains(p.id) || p.transStatus == Paragraph.STATUS_QUEUED;
        boolean failed = !inFlight && p.transStatus == Paragraph.STATUS_FAILED;
        h.progress.setText(R.string.reader_translating);
        h.progress.setVisibility(inFlight ? View.VISIBLE : View.GONE);
        h.retry.setVisibility(failed ? View.VISIBLE : View.GONE);
        // 翻译中再点「段」不该再发一次请求
        h.marker.setEnabled(!inFlight);
    }

    /**
     * 正文的全部装饰都在这里，而且**只加 span、不改字符串**。
     *
     * <p>首行缩进用 {@code LeadingMarginSpan}（不是塞空格）：塞空格会同时污染词偏移表、
     * TTS 朗读与复制，正是 D1 要防的。听书高亮同理由外部 {@link #setHighlight} 维护，
     * 这里只在绑定时把当前区间补上。
     */
    private void applySpans(TextView tv, Paragraph p) {
        String text = p.text == null ? "" : p.text;
        SpannableString s = new SpannableString(text);
        if (indentChars > 0 && s.length() > 0) {
            float px = indentChars * tv.getPaint().getTextSize();
            s.setSpan(new LeadingMarginSpan.Standard((int) px, 0),
                    0, s.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        }
        if (p.id == hlParaId) {
            addHighlightSpans(s, tv);
        }
        tv.setText(s, TextView.BufferType.SPANNABLE);
    }

    /** 高亮区间：底色 + 前景色都取主题 token，浅色/深色下文字都读得清。 */
    private void addHighlightSpans(Spannable s, TextView tv) {
        int start = Math.max(0, hlStart);
        int end = Math.min(hlEnd, s.length());
        if (end <= start) {
            return;
        }
        s.setSpan(new BackgroundColorSpan(highlightBackground(tv)), start, end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new ForegroundColorSpan(highlightForeground(tv)), start, end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static int highlightBackground(TextView tv) {
        return MaterialColors.getColor(tv,
                com.google.android.material.R.attr.colorPrimaryContainer,
                androidx.core.content.ContextCompat.getColor(tv.getContext(), R.color.highlight_word));
    }

    private static int highlightForeground(TextView tv) {
        return MaterialColors.getColor(tv,
                com.google.android.material.R.attr.colorOnPrimaryContainer,
                androidx.core.content.ContextCompat.getColor(tv.getContext(), R.color.text_primary));
    }

    /**
     * 只改 span 的重绘路径（可见项）。文本没变，所以不触发排版——
     * 逐词高亮每秒好几次，走 {@code notifyItemChanged} 会一直重新 measure。
     */
    private void paintHighlight(TextView tv, Paragraph p) {
        CharSequence cs = tv.getText();
        if (!(cs instanceof Spannable) || p.text == null || !p.text.contentEquals(cs)) {
            // 文本已被别处换过（或不是 Spannable）：退回整套绑定，保证状态一致
            applySpans(tv, p);
            return;
        }
        Spannable sp = (Spannable) cs;
        removeHighlightSpans(sp);
        if (p.id == hlParaId) {
            addHighlightSpans(sp, tv);
        }
    }

    private static void removeHighlightSpans(Spannable sp) {
        for (BackgroundColorSpan s : sp.getSpans(0, sp.length(), BackgroundColorSpan.class)) {
            sp.removeSpan(s);
        }
        for (ForegroundColorSpan s : sp.getSpans(0, sp.length(), ForegroundColorSpan.class)) {
            sp.removeSpan(s);
        }
    }

    /**
     * 段距。作用在 item 的 `layout_marginBottom` 上——
     * 那条缝同时是长段落的「呼出菜单」点击区，所以改了段距菜单入口也跟着变宽变窄，
     * 但永远不会消失（{@link ReaderPrefs#PARA_SPACINGS} 最小档也不是 0）。
     */
    private void applyParaSpacing(View item) {
        ViewGroup.LayoutParams lp = item.getLayoutParams();
        if (!(lp instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
        int px = (int) (paraSpacingDp * item.getResources().getDisplayMetrics().density);
        if (mlp.bottomMargin == px) {
            return; // 复用时不改就不用惊动 requestLayout
        }
        mlp.bottomMargin = px;
        item.setLayoutParams(mlp);
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
        public final TextView text;        final TextView trans;
        final TextView retry;
        final TextView progress;
        final TextView marker;
        final ParagraphGestures gestures;

        VH(View v, ReaderAdapter adapter) {
            super(v);
            text = v.findViewById(R.id.para_text);
            trans = v.findViewById(R.id.para_trans);
            retry = v.findViewById(R.id.para_retry);
            progress = v.findViewById(R.id.para_progress);
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

                @Override
                public void onBlankTap() {
                    if (adapter.listener != null) {
                        adapter.listener.onBlankTap();
                    }
                }
            });
        }
    }
}
