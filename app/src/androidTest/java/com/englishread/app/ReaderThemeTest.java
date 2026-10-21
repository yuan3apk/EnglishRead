package com.englishread.app;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.englishread.app.data.BookDao;
import com.englishread.app.data.Db;
import com.englishread.app.model.Book;
import com.englishread.app.parse.ParsedBook;
import com.englishread.app.reader.ReaderPrefs;
import com.englishread.app.ui.ReaderActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/**
 * 阅读器的明暗设置必须只作用于阅读器自己。
 *
 * <p>回归的是这个 bug：改完阅读器主题回书架，书架也跟着黑了。原因是用了进程级的
 * {@code setDefaultNightMode()}，它会把已创建的 Activity 一起重建成夜间资源。
 *
 * <p>两条断言各守一半：阅读器自己的 uiMode 真的变了（说明 local 模式生效），
 * 而 {@code getDefaultNightMode()} 还是未指定（说明没碰全局）。
 */
@RunWith(AndroidJUnit4.class)
public class ReaderThemeTest {

    private Context ctx;
    private BookDao books;
    private long bookId;
    private ReaderPrefs prefs;

    @Before
    public void setUp() throws Exception {
        ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        books = new BookDao(Db.of(ctx));
        prefs = new ReaderPrefs(ctx);
        cleanUp();

        File src = File.createTempFile("theme-" + System.nanoTime(), ".txt", ctx.getCacheDir());
        ParsedBook pb = ParsedBook.of("txt");
        pb.title = "ReaderThemeTest Book";
        ParsedBook.Chapter c = new ParsedBook.Chapter("Chapter 1", null);
        c.paragraphs.add("A short line so the reader has something to render.");
        pb.chapters.add(c);
        bookId = books.insert(pb, src.getAbsolutePath(), null);
    }

    @After
    public void tearDown() {
        prefs.setDark(null);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_UNSPECIFIED);
        cleanUp();
    }

    private void cleanUp() {
        for (Book b : books.listAll()) {
            if (b.title != null && b.title.startsWith("ReaderThemeTest")) {
                books.delete(b.id);
            }
        }
    }

    @Test
    public void darkReaderKeepsAppDefaultUntouched() {
        assertReaderGets(Configuration.UI_MODE_NIGHT_YES, Boolean.TRUE);
    }

    @Test
    public void lightReaderKeepsAppDefaultUntouched() {
        assertReaderGets(Configuration.UI_MODE_NIGHT_NO, Boolean.FALSE);
    }

    /** @param expectedNightMode 期望阅读器看到的 uiMode（YES/NO） */
    private void assertReaderGets(int expectedNightMode, Boolean dark) {
        prefs.setDark(dark);

        Intent i = new Intent(ctx, ReaderActivity.class)
                .putExtra(ReaderActivity.EXTRA_BOOK_ID, bookId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try (ActivityScenario<ReaderActivity> scenario = ActivityScenario.launch(i)) {
            assertEquals("阅读器不该改全局夜间模式，否则书架会跟着变",
                    AppCompatDelegate.MODE_NIGHT_UNSPECIFIED,
                    AppCompatDelegate.getDefaultNightMode());

            scenario.onActivity(a -> assertEquals("阅读器自己的 uiMode 应已是目标明暗",
                    expectedNightMode,
                    a.getResources().getConfiguration().uiMode
                            & Configuration.UI_MODE_NIGHT_MASK));
        }
    }
}
