package com.englishread.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.englishread.app.reader.ReaderHitTest;

import org.junit.Test;

/** 点词命中：命中词、词间空白、段尾、行尾标点。 */
public class ReaderHitTestTest {

    /** "time, it was" —— 词边界与真实段落里的标点一致。 */
    private static final String TEXT = "time, it was";
    private static final int[] STARTS = {0, 6, 9};
    private static final int[] ENDS = {4, 8, 12};

    private static void hit(int offset, int expectStart, int expectEnd) {
        ReaderHitTest.Hit h = ReaderHitTest.resolve(offset, STARTS, ENDS);
        assertEquals("offset " + offset + " 起点", expectStart, h.start);
        assertEquals("offset " + offset + " 终点", expectEnd, h.end);
    }

    @Test
    public void wordStartAndEndHit() {
        hit(0, 0, 4);   // t
        hit(3, 0, 4);   // e，词内最后一个字符
        hit(6, 6, 8);   // i
        hit(9, 9, 12);  // w
        hit(11, 9, 12); // s
    }

    @Test
    public void gapBetweenWordsMisses() {
        hit(4, -1, -1);  // 逗号
        hit(5, -1, -1);  // 空格
        hit(8, -1, -1);  // 空格
    }

    @Test
    public void pastEndOfParagraphMisses() {
        hit(12, -1, -1);  // 行末之后
        hit(999, -1, -1);
        hit(-1, -1, -1);
    }

    @Test
    public void emptyTokensMiss() {
        assertFalse(ReaderHitTest.resolve(0, new int[0], new int[0]).isWord());
        assertFalse(ReaderHitTest.resolve(0, null, null).isWord());
    }

    @Test
    public void raggedArraysMiss() {
        // 长度不一致是调用方的 bug，宁可什么都不返回也不要越界
        assertFalse(ReaderHitTest.resolve(0, STARTS, new int[]{4, 8}).isWord());
    }

    @Test
    public void wordResultIsExactRange() {
        ReaderHitTest.Hit h = ReaderHitTest.resolve(1, STARTS, ENDS);
        assertTrue(h.isWord());
        assertEquals(TEXT.substring(h.start, h.end), "time");
    }
}
