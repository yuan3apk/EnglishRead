package com.englishread.app;

import static org.junit.Assert.assertEquals;

import com.englishread.app.data.ReadingPosDao;
import com.englishread.app.model.Chapter;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/** 书架上的百分比：算错不是崩溃，是「读到一半却显示 0%」，比崩溃更难发现。 */
public class ProgressPctTest {

    private static Chapter chapter(String title, int paraCount) {
        Chapter c = new Chapter();
        c.title = title;
        c.paraCount = paraCount;
        return c;
    }

    @Test
    public void firstSentenceIsNotZeroPercent() {
        // 读到第一句就已经算读了 1 段，否则最后一章永远到不了 100%
        assertEquals(0.25f, ReadingPosDao.progressPct(
                Arrays.asList(chapter("a", 2), chapter("b", 2)), 0, 1), 0.0001f);
    }

    @Test
    public void countsWholeChaptersBeforeCurrent() {
        assertEquals(0.75f, ReadingPosDao.progressPct(
                Arrays.asList(chapter("a", 2), chapter("b", 2)), 1, 1), 0.0001f);
    }

    @Test
    public void lastSentenceOfLastChapterIsOneHundredPercent() {
        assertEquals(1f, ReadingPosDao.progressPct(
                Arrays.asList(chapter("a", 3), chapter("b", 5)), 1, 5), 0.0001f);
    }

    @Test
    public void neverOvershootsWhenParagraphCountIsStale() {
        // 章内段数记大了（老书）也不能出现 120%
        assertEquals(1f, ReadingPosDao.progressPct(Collections.singletonList(chapter("a", 3)), 0, 99),
                0.0001f);
    }

    @Test
    public void emptyBookIsZeroNotDivideByZero() {
        assertEquals(0f, ReadingPosDao.progressPct(
                Arrays.asList(chapter("a", 0), chapter("b", 0)), 1, 1), 0.0001f);
    }
}
