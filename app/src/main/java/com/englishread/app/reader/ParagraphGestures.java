package com.englishread.app.reader;

import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * 段落正文的点击/长按识别。
 *
 * <p>两个硬约束（HANDOFF 2.1）：
 * 1. {@code onTouch} 返回 false，滚动仍归 RecyclerView；
 * 2. 只判词，点空白/标点/行尾什么都不做——「段」按钮是独立 View，不在这里分流。
 *
 * <p>横坐标 → 字符偏移用 {@link android.text.Layout}，
 * 判定本身交给纯函数 {@link ReaderHitTest}。
 */
public class ParagraphGestures implements View.OnTouchListener {

    public interface Listener {
        void onWordTap(String word, int start, int end);

        void onWordLongPress(String word, int start, int end);

        /** 点到了空白/标点/行尾，没命中词：呼出或收起菜单。 */
        void onBlankTap();
    }

    private final TextView view;
    private final GestureDetector detector;
    private final Listener listener;

    @Nullable
    private int[] starts;
    @Nullable
    private int[] ends;
    @Nullable
    private String[] words;
    /** 本次按下已被长按消费，抬手时不许再当单击。 */
    private boolean consumedByLongPress;

    public ParagraphGestures(TextView view, Listener listener) {
        this.view = view;
        this.listener = listener;
        this.detector = new GestureDetector(view.getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapUp(MotionEvent e) {
                        return handle(e, false);
                    }

                    @Override
                    public void onLongPress(MotionEvent e) {
                        handle(e, true);
                    }
                });
        // 选区与 movement method 会抢事件，必须关掉
        view.setMovementMethod(null);
        view.setTextIsSelectable(false);
        // 正文必须是「可点击」的触摸目标。不消费 DOWN 的话，RecyclerView 会接管整个手势，
        // 正文只收到一个 DOWN、永远等不到 UP/CANCEL——而 GestureDetector 的 500ms 长按计时器
        // 是按下时就启动的，到点照样触发：于是「滑到一半手指停在一个词上」会莫名其妙弹词卡。
        // 注意顺序：setTextIsSelectable(false) 内部会把 clickable 置回 false，所以必须写在它后面。
        view.setClickable(true);
    }

    public void setTokens(@Nullable int[] starts, @Nullable int[] ends,
                          @Nullable String[] words) {
        this.starts = starts;
        this.ends = ends;
        this.words = words;
        this.consumedByLongPress = false;
    }

    private boolean handle(MotionEvent e, boolean longPress) {
        if (starts == null || ends == null || words == null) {
            // 没有词表说明这段还在加载，但空白点击仍应能翻页
            if (!longPress) {
                listener.onBlankTap();
            }
            return false;
        }
        // 长按已经处理过这一次按下，抬手时别再补一个单击
        if (!longPress && consumedByLongPress) {
            consumedByLongPress = false;
            return false;
        }
        float x = e.getX() - view.getTotalPaddingLeft() + view.getScrollX();
        float y = e.getY() - view.getTotalPaddingTop() + view.getScrollY();
        if (y < 0 || y > view.getHeight()) {
            return false;
        }
        android.text.Layout layout = view.getLayout();
        if (layout == null) {
            if (!longPress) {
                listener.onBlankTap();
            }
            return false;
        }
        int line = layout.getLineForVertical((int) y);
        int offset = layout.getOffsetForHorizontal(line, x);

        // getOffsetForHorizontal 返回「最近的字符边界」，不是「点到了哪个字符」：
        // 点在 't' 右半边会返回它后面那个位置。所以用它当起点，再按 x 落在
        // 哪个字符的横向区间里纠正一格。空格/标点/行尾照旧不命中。
        int charUnder = Math.max(0, Math.min(offset, view.getText().length() - 1));
        if (charUnder > 0 && layout.getPrimaryHorizontal(charUnder) > x) {
            charUnder--;
        }

        ReaderHitTest.Hit hit = ReaderHitTest.resolve(charUnder, starts, ends);
        if (!hit.isWord()) {
            // 空白/标点/行尾：不查词，交给分区（左回退/中菜单/右前进）
            if (!longPress) {
                listener.onBlankTap();
            }
            return false;
        }
        int i = indexOfStart(hit.start, starts);
        if (i < 0) {
            if (!longPress) {
                listener.onBlankTap();
            }
            return false;
        }
        if (longPress) {
            consumedByLongPress = true;
            listener.onWordLongPress(words[i], hit.start, hit.end);
        } else {
            listener.onWordTap(words[i], hit.start, hit.end);
        }
        return true;
    }

    private static int indexOfStart(int start, int[] starts) {
        int i = ReaderHitTest.floorIndex(starts, start);
        return i >= 0 && starts[i] == start ? i : -1;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        // 永不消费：RecyclerView 仍要能滚（滚动靠它拦截 MOVE，届时这里会收到 CANCEL）
        detector.onTouchEvent(event);
        return false;
    }
}
