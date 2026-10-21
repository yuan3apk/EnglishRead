package com.englishread.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.englishread.app.parse.ParsedBook;
import com.englishread.app.parse.Sentences;

import org.junit.Test;

import java.util.List;

/** 一句话 = 一个段落。边界都在这里：无标点、连续标点、小数点、空串。 */
public class SentencesTest {

    @Test
    public void splitsOnPeriodQuestionAndExclamation() {
        List<String> s = Sentences.split("He ran. Did he? He did!");
        assertEquals(3, s.size());
        assertEquals("He ran.", s.get(0));
        assertEquals("Did he?", s.get(1));
        assertEquals("He did!", s.get(2));
    }

    @Test
    public void keepsPunctuationWithItsSentence() {
        List<String> s = Sentences.split("A. B.");
        assertEquals("A.", s.get(0));
        assertEquals("B.", s.get(1));
    }

    @Test
    public void doesNotSplitDecimalOrDottedAbbreviationWithoutSpace() {
        // 点后面没空白就不是句末：3.14、www.example.com 都不能切
        List<String> s = Sentences.split("Pi is 3.14 and the site is www.example.com today.");
        assertEquals(1, s.size());
    }

    @Test
    public void blockWithoutTerminalPunctuationIsOneSentence() {
        List<String> s = Sentences.split("just a fragment with no full stop");
        assertEquals(1, s.size());
    }

    @Test
    public void emptyAndBlankInputsProduceNoSentences() {
        assertTrue(Sentences.split(null).isEmpty());
        assertTrue(Sentences.split("").isEmpty());
        assertTrue(Sentences.split("   ").isEmpty());
    }

    @Test
    public void chineseFullStopsSplitWithoutSpace() {
        List<String> s = Sentences.split("第一句。第二句！第三句？");
        assertEquals(3, s.size());
        assertEquals("第一句。", s.get(0));
        assertEquals("第三句？", s.get(2));
    }

    @Test
    public void doesNotSplitAfterTitles() {
        List<String> s = Sentences.split("Mr. Smith met Mrs. Jones. Dr. Who was late. Prof. X left.");
        assertEquals(3, s.size());
        assertEquals("Mr. Smith met Mrs. Jones.", s.get(0));
        assertEquals("Dr. Who was late.", s.get(1));
    }

    @Test
    public void doesNotSplitAfterDottedInitialism() {
        List<String> s = Sentences.split("He lives in the U.S. now. See e.g. chapter one, i.e. the start.");
        assertEquals(2, s.size());
        assertEquals("He lives in the U.S. now.", s.get(0));
    }

    @Test
    public void doesNotSplitAfterNumberedAbbreviation() {
        List<String> s = Sentences.split("See Fig. 3 and No. 12 on p. 40. Then stop.");
        assertEquals(2, s.size());
        assertEquals("See Fig. 3 and No. 12 on p. 40.", s.get(0));
    }

    @Test
    public void doesNotSplitAfterNameInitials() {
        // J. K. Rowling 是一串单字母缩写，不该被拆成多段
        List<String> s = Sentences.split("J. K. Rowling wrote it. She is famous.");
        assertEquals(2, s.size());
        assertEquals("J. K. Rowling wrote it.", s.get(0));
    }

    @Test
    public void stillSplitsWhenAbbreviationLikeWordEndsSentence() {
        // no 只有首字母大写才当编号缩写，小写 no 后面就是句末
        List<String> s = Sentences.split("He said no. Then he left.");
        assertEquals(2, s.size());
        assertEquals("He said no.", s.get(0));
        // I. 不算缩写
        assertEquals(2, Sentences.split("It was I. Then everything changed.").size());
    }

    @Test
    public void keepsClosingQuoteWithItsSentence() {
        List<String> s = Sentences.split("He said \"Go.\" Then he ran.");
        assertEquals(2, s.size());
        assertEquals("He said \"Go.\"", s.get(0));
        assertEquals("Then he ran.", s.get(1));
    }

    @Test
    public void splitBookTurnsEveryParagraphIntoSentences() {
        ParsedBook pb = ParsedBook.of("txt");
        ParsedBook.Chapter ch = new ParsedBook.Chapter("One", "one.xhtml");
        ch.paragraphs.add("First. Second.");
        ch.paragraphs.add("Third!");
        pb.chapters.add(ch);

        Sentences.splitBook(pb);

        assertEquals(3, pb.chapters.get(0).paragraphs.size());
        assertEquals("First.", pb.chapters.get(0).paragraphs.get(0));
        assertEquals("Third!", pb.chapters.get(0).paragraphs.get(2));
    }
}
