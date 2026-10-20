package com.englishread.app.reader;

/**
 * 点词命中：只判「命中某个词」或「什么都不做」两种结果。
 *
 * <p>见 HANDOFF 2.1 / D1：段落 TextView 的 text 只有原文，「段」按钮是独立 View，
 * 所以这里既没有 gutterPx 也没有 marker 区间。空白、标点、行尾一律不猜。
 *
 * <p>横坐标 → 字符偏移是 {@code Layout.getOffsetForHorizontal} 的活，留在调用方；
 * 这里只吃偏移和词边界，纯数学、可直接单测。
 */
public final class ReaderHitTest {

    /** 命中的词在段落里的字符区间；miss 时 start/end 都是 -1。 */
    public static final class Hit {
        public final int start;
        public final int end;

        private Hit(int start, int end) {
            this.start = start;
            this.end = end;
        }

        public static Hit word(int start, int end) {
            return new Hit(start, end);
        }

        public static Hit none() {
            return new Hit(-1, -1);
        }

        public boolean isWord() {
            return start >= 0;
        }
    }

    private ReaderHitTest() {
    }

    /**
     * @param offset     点击处在本段内的字符偏移（调用方用 Layout 算好）
     * @param tokenStarts 词起点，升序
     * @param tokenEnds   词终点（不含），与 tokenStarts 一一对应且升序
     */
    public static Hit resolve(int offset, int[] tokenStarts, int[] tokenEnds) {
        if (tokenStarts == null || tokenEnds == null || tokenStarts.length == 0
                || tokenStarts.length != tokenEnds.length) {
            return Hit.none();
        }
        int i = floorIndex(tokenStarts, offset);
        if (i >= 0 && offset < tokenEnds[i]) {
            return Hit.word(tokenStarts[i], tokenEnds[i]);
        }
        return Hit.none();
    }

    /** 最后一个 <= key 的下标，没有则 -1。 */
    static int floorIndex(int[] sorted, int key) {
        int lo = 0;
        int hi = sorted.length - 1;
        int ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (sorted[mid] <= key) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ans;
    }
}
