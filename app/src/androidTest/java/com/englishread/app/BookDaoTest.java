package com.englishread.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.englishread.app.data.BookDao;
import com.englishread.app.data.Db;
import com.englishread.app.model.Book;
import com.englishread.app.parse.ParsedBook;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/** 入库/列表/删除的库层行为。用真 SQLite，不 mock。 */
@RunWith(AndroidJUnit4.class)
public class BookDaoTest {

    private BookDao dao;
    private File src;
    private File cover;

    @Before
    public void setUp() throws Exception {
        dao = new BookDao(Db.of(InstrumentationRegistry.getInstrumentation().getTargetContext()));
        File dir = InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir();
        src = write(new File(dir, "src-" + System.nanoTime() + ".txt"), "x");
        cover = write(new File(dir, "cover-" + System.nanoTime() + ".jpg"), "y");
        cleanUp();
    }

    @After
    public void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        for (Book b : dao.listAll()) {
            if (b.title != null && b.title.startsWith("DaoTest")) {
                dao.delete(b.id);
            }
        }
    }

    private static File write(File f, String content) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(content.getBytes("UTF-8"));
        }
        return f;
    }

    private ParsedBook sample() {
        ParsedBook pb = ParsedBook.of("txt");
        pb.title = "DaoTest Book";
        pb.author = "Tester";
        ParsedBook.Chapter c = new ParsedBook.Chapter("Chapter 1", null);
        c.paragraphs.add("First paragraph.");
        c.paragraphs.add("Second paragraph.");
        pb.chapters.add(c);
        return pb;
    }

    @Test
    public void insertWritesBookChapterAndParagraphs() {
        long id = dao.insert(sample(), src.getAbsolutePath(), cover.getAbsolutePath());

        Book found = null;
        for (Book b : dao.listAll()) {
            if (b.id == id) {
                found = b;
            }
        }
        assertNotNull(found);
        assertEquals("DaoTest Book", found.title);
        assertEquals("Tester", found.author);
        assertEquals("txt", found.format);

        android.database.Cursor c = Db.of(
                        InstrumentationRegistry.getInstrumentation().getTargetContext())
                .getReadableDatabase()
                .rawQuery("SELECT COUNT(*) FROM paragraph p JOIN chapter ch ON p.chapter_id = ch.id"
                        + " WHERE ch.book_id = ?", new String[]{String.valueOf(id)});
        c.moveToFirst();
        assertEquals(2, c.getInt(0));
        c.close();
    }

    @Test
    public void deleteRemovesRowsAndFiles() {
        long id = dao.insert(sample(), src.getAbsolutePath(), cover.getAbsolutePath());
        assertTrue(src.exists());
        assertTrue(cover.exists());

        dao.delete(id);

        assertFalse("源文件应被删掉", src.exists());
        assertFalse("封面应被删掉", cover.exists());
        for (Book b : dao.listAll()) {
            assertTrue("行应被删掉", b.id != id);
        }
    }

    @Test
    public void listAllIsOrderedByLastOpened() {
        long a = dao.insert(sample(), src.getAbsolutePath(), null);
        ParsedBook second = sample();
        second.title = "DaoTest Second";
        long b = dao.insert(second, src.getAbsolutePath(), null);

        List<Book> all = dao.listAll();
        int ia = -1;
        int ib = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id == a) {
                ia = i;
            }
            if (all.get(i).id == b) {
                ib = i;
            }
        }
        // last_opened_at 都为 NULL，按 added_at DESC 后插入的在前
        assertTrue(ia >= 0 && ib >= 0);
        assertTrue("后导入的应排在前面", ib < ia);
    }
}
