package com.englishread.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.englishread.app.data.Db;
import com.englishread.app.data.VocabDao;
import com.englishread.app.model.Book;
import com.englishread.app.model.VocabEntry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/** 生词本库层：去重、按书/掌握筛选、书目。用真 SQLite，不 mock。 */
@RunWith(AndroidJUnit4.class)
public class VocabDaoTest {

    private VocabDao dao;
    private SQLiteDatabase db;

    @Before
    public void setUp() {
        db = Db.of(InstrumentationRegistry.getInstrumentation().getTargetContext())
                .getWritableDatabase();
        dao = new VocabDao(Db.of(InstrumentationRegistry.getInstrumentation().getTargetContext()));
        cleanUp();
    }

    @After
    public void tearDown() {
        cleanUp();
    }

    /** 只清测试自己造的数据：lemma 与书名都带前缀，真机上的真实生词不受影响。 */
    private void cleanUp() {
        db.execSQL("DELETE FROM vocab WHERE lemma LIKE 'vtest%'");
        db.execSQL("DELETE FROM book WHERE title LIKE 'Vtest%'");
    }

    private long book(String title) {
        ContentValues cv = new ContentValues();
        cv.put("title", title);
        cv.put("format", "txt");
        cv.put("added_at", System.currentTimeMillis());
        return db.insertOrThrow("book", null, cv);
    }

    private static VocabEntry entry(String word, String lemma, long bookId) {
        VocabEntry v = new VocabEntry();
        v.word = word;
        v.lemma = lemma;
        v.translation = "释义";
        v.sentence = "It was the best of times.";
        v.bookId = bookId;
        v.paraId = 7;
        return v;
    }

    @Test
    public void sameLemmaIsStoredOnlyOnce() {
        long b = book("Vtest One");
        assertTrue(dao.add(entry("ran", "vtest-run", b)) > 0);
        // 同一原形的另一个变形：不写第二条，也不覆盖先入为主那条的语境
        assertEquals(-1, dao.add(entry("running", "vtest-run", b)));

        List<VocabEntry> rows = dao.list(b, false);
        assertEquals(1, rows.size());
        assertEquals("ran", rows.get(0).word);
        assertEquals("It was the best of times.", rows.get(0).sentence);
        assertEquals("Vtest One", rows.get(0).bookTitle);
    }

    @Test
    public void listFiltersByBookAndByMastered() {
        long a = book("Vtest A");
        long b = book("Vtest B");
        long idA = dao.add(entry("vtestalpha", "vtest-alpha", a));
        dao.add(entry("vtestbeta", "vtest-beta", b));
        dao.setMastered(idA, true);

        assertEquals(1, dao.list(a, false).size());
        assertEquals(0, dao.list(a, true).size());
        assertEquals(1, dao.list(b, true).size());

        dao.setMastered(idA, false);
        assertEquals(1, dao.list(a, true).size());
    }

    @Test
    public void newestWordComesFirst() {
        long a = book("Vtest A");
        long b = book("Vtest B");
        dao.add(entry("vtestalpha", "vtest-alpha", a));
        dao.add(entry("vtestbeta", "vtest-beta", b));

        List<VocabEntry> all = dao.list(0, false);
        int ia = -1;
        int ib = -1;
        for (int i = 0; i < all.size(); i++) {
            if ("vtestalpha".equals(all.get(i).word)) {
                ia = i;
            }
            if ("vtestbeta".equals(all.get(i).word)) {
                ib = i;
            }
        }
        assertTrue("两条都该在列表里", ia >= 0 && ib >= 0);
        assertTrue("后加的应排在前面", ib < ia);
    }

    @Test
    public void booksListsOnlyBooksThatHaveWords() {
        long a = book("Vtest A");
        long empty = book("Vtest Empty");
        dao.add(entry("vtestalpha", "vtest-alpha", a));

        boolean hasA = false;
        for (Book b : dao.books()) {
            assertTrue("没生词的书不该出现在筛选里", b.id != empty);
            if (b.id == a) {
                hasA = true;
            }
        }
        assertTrue(hasA);
    }
}
