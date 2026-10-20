package com.englishread.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.englishread.app.data.BookDao;
import com.englishread.app.data.Db;
import com.englishread.app.data.DictSource;
import com.englishread.app.data.ParagraphDao;
import com.englishread.app.model.Chapter;
import com.englishread.app.model.Paragraph;
import com.englishread.app.model.WordEntry;
import com.englishread.app.parse.ParsedBook;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

/**
 * 阅读器数据层与内置词典。用真 SQLite 与真 assets/dict.db，不 mock。
 *
 * <p>重点守两条验收（HANDOFF M2）：paragraph.text 与入库原文逐字相同；
 * ran / better / times / well-known 这类变形与连字符词查得到。
 */
@RunWith(AndroidJUnit4.class)
public class ReaderDataTest {

    private BookDao books;
    private ParagraphDao paras;
    private long bookId;

    @Before
    public void setUp() throws Exception {
        var ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        books = new BookDao(Db.of(ctx));
        paras = new ParagraphDao(Db.of(ctx));
        cleanUp();

        File src = File.createTempFile("reader-" + System.nanoTime(), ".txt", ctx.getCacheDir());
        ParsedBook pb = ParsedBook.of("txt");
        pb.title = "ReaderDataTest Book";
        ParsedBook.Chapter c = new ParsedBook.Chapter("Chapter 1", null);
        c.paragraphs.add("It was the best of times, it was the worst of times.");
        c.paragraphs.add("He ran well-known races in 1813.");
        pb.chapters.add(c);
        bookId = books.insert(pb, src.getAbsolutePath(), null);
    }

    @After
    public void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        for (var b : books.listAll()) {
            if (b.title != null && b.title.startsWith("ReaderDataTest")) {
                books.delete(b.id);
            }
        }
    }

    @Test
    public void chaptersAndParagraphsComeBackInOrder() {
        List<Chapter> cs = paras.chapters(bookId);
        assertEquals(1, cs.size());
        assertEquals("Chapter 1", cs.get(0).title);
        assertEquals(2, cs.get(0).paraCount);

        List<Paragraph> ps = paras.paragraphs(cs.get(0).id);
        assertEquals(2, ps.size());
        assertEquals(0, ps.get(0).idx);
        assertEquals(1, ps.get(1).idx);
    }

    @Test
    public void paragraphTextIsStoredVerbatim() {
        Chapter ch = paras.firstChapter(bookId);
        assertNotNull(ch);
        List<Paragraph> ps = paras.paragraphs(ch.id);

        assertEquals("It was the best of times, it was the worst of times.", ps.get(0).text);
        assertEquals("He ran well-known races in 1813.", ps.get(1).text);
        // 「段」按钮是独立 View：正文里绝不能出现它
        for (Paragraph p : ps) {
            assertFalse("正文不该含「段」", p.text.contains("段"));
        }
    }

    @Test
    public void translationRoundTripKeepsTextIntact() {
        Chapter ch = paras.firstChapter(bookId);
        Paragraph p = paras.paragraphs(ch.id).get(0);
        assertEquals(Paragraph.STATUS_NONE, p.transStatus);
        assertNull(p.trans);

        paras.saveTranslation(p.id, "这是最好的时代。", Paragraph.STATUS_DONE);

        Paragraph after = paras.paragraphs(ch.id).get(0);
        assertEquals("这是最好的时代。", after.trans);
        assertEquals(Paragraph.STATUS_DONE, after.transStatus);
        assertEquals("译文回写不该动原文", p.text, after.text);
        assertEquals(1, paras.untranslatedCount(ch.id));
    }

    @Test
    public void dictResolvesInflectionsAndHyphens() {
        DictSource dict = new DictSource(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        assertTrue("内置词典应在 assets 里", DictSource.isBundled(
                InstrumentationRegistry.getInstrumentation().getTargetContext()));

        WordEntry ran = dict.lookup("ran");
        assertNotNull("ran 应查得到", ran);
        assertEquals("run", ran.lemma);
        assertNotNull(ran.translation);

        assertEquals("good", dict.lookup("better").lemma);
        assertEquals("time", dict.lookup("times").lemma);
        assertNotNull("连字符词应查得到", dict.lookup("well-known").translation);

        assertNull("不存在的词返回 null，不返回空壳", dict.lookup("zzzznotaword"));
    }
}
