package com.englishread.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.englishread.app.reader.SentenceIndex;

import java.util.List;

/**
 * 朗读片段的偏移表。算错不会崩，只会让逐词高亮落错位置/念半句，
 * 所以用最便宜的 JUnit 把不变量钉住。
 */
public class SentenceIndexTest {

    @Test
    public void oneSentenceIsOneChunk() {
        String text = "It was the best of times.";
        List<int[]> chunks = SentenceIndex.chunks(text);
        assertEquals(1, chunks.size());
        assertEquals(text, slice(text, chunks.get(0)));
    }

    @Test
    public void eachSentenceIsItsOwnChunk() {
        // 一句话一段时这里只有一个，老书的整段会切成多个——「上一句/下一句」按段跳
        String text = "Hi. Bye!";
        List<int[]> chunks = SentenceIndex.chunks(text);
        assertEquals(2, chunks.size());
        assertEquals("Hi.", slice(text, chunks.get(0)));
        assertEquals("Bye!", slice(text, chunks.get(1)));
    }

    @Test
    public void decimalAndUrlAreNotSentenceEnds() {
        String text = "Pi is 3.14 exactly.";
        List<int[]> chunks = SentenceIndex.chunks(text);
        assertEquals(1, chunks.size());
        assertEquals(text, slice(text, chunks.get(0)));
    }

    @Test
    public void surroundingWhitespaceIsTrimmedOffTheRange() {
        String text = "  Hello there.  ";
        List<int[]> chunks = SentenceIndex.chunks(text);
        assertEquals(1, chunks.size());
        // 区间必须指向「Hello there.」，带上前导空白会让高亮从行首开始
        assertEquals("Hello there.", slice(text, chunks.get(0)));
    }

    @Test
    public void chineseFullStopEndsAChunk() {
        String text = "这是第一句。这是第二句。";
        List<int[]> chunks = SentenceIndex.chunks(text);
        assertEquals(2, chunks.size());
        assertEquals("这是第一句。", slice(text, chunks.get(0)));
    }

    @Test
    public void overlongSentenceIsSplitUnderTheLimit() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 120; i++) {
            sb.append("word ");
        }
        String text = sb.toString().trim();
        assertTrue("测试前提：这段要比上限长", text.length() > SentenceIndex.MAX_CHARS);

        List<int[]> chunks = SentenceIndex.chunks(text);
        assertTrue("超长句必须切成多段", chunks.size() > 1);

        int lastEnd = -1;
        for (int[] r : chunks) {
            int len = r[1] - r[0];
            assertTrue("片段不能超过上限：" + len, len <= SentenceIndex.MAX_CHARS);
            assertTrue("片段不能为空", len > 0);
            assertTrue("片段必须按顺序、不重叠", r[0] >= lastEnd);
            lastEnd = r[1];
        }
        // 首尾必须覆盖原文：少了尾巴等于那句话没念
        assertEquals(0, chunks.get(0)[0]);
        assertEquals(text.length(), chunks.get(chunks.size() - 1)[1]);
    }

    @Test
    public void blankTextHasNoChunks() {
        assertTrue(SentenceIndex.chunks(null).isEmpty());
        assertTrue(SentenceIndex.chunks("").isEmpty());
        assertTrue(SentenceIndex.chunks("   ").isEmpty());
    }

    private static String slice(String text, int[] range) {
        return text.substring(range[0], range[1]);
    }
}
