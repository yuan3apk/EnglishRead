package com.englishread.app;

import static org.junit.Assert.assertTrue;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.englishread.app.data.Db;
import com.englishread.app.data.VocabDao;
import com.englishread.app.model.VocabEntry;
import com.englishread.app.ui.VocabActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 生词本页面进得去、且真的把词列出来了。
 *
 * <p>回归的是这个 bug：Activity 起好了、筛选条也在，但漏了首次 {@code load()}，
 * 于是列表永远是空的——DAO 测试全绿也发现不了，因为它压根没碰界面。
 */
@RunWith(AndroidJUnit4.class)
public class VocabScreenTest {

    private VocabDao dao;

    @Before
    public void setUp() {
        dao = new VocabDao(Db.of(InstrumentationRegistry.getInstrumentation().getTargetContext()));
        cleanUp();
        VocabEntry v = new VocabEntry();
        v.word = "vtestword";
        v.lemma = "vtest-screen";
        v.translation = "测试词";
        v.sentence = "It was the best of times.";
        dao.add(v);
    }

    @After
    public void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        Db.of(InstrumentationRegistry.getInstrumentation().getTargetContext())
                .getWritableDatabase()
                .execSQL("DELETE FROM vocab WHERE lemma LIKE 'vtest%'");
    }

    @Test
    public void screenListsStoredWords() throws Exception {
        try (ActivityScenario<VocabActivity> scenario = ActivityScenario.launch(VocabActivity.class)) {
            AtomicInteger rows = new AtomicInteger();
            // 读库在 IO 线程、回填在主线程：给它最多 3 秒，别用 sleep 猜时序
            for (int i = 0; i < 30 && rows.get() == 0; i++) {
                Thread.sleep(100);
                scenario.onActivity(a -> {
                    RecyclerView rv = a.findViewById(R.id.vocab_list);
                    rows.set(rv == null || rv.getAdapter() == null
                            ? 0 : rv.getAdapter().getItemCount());
                });
            }
            assertTrue("生词本应该列出已存的词，实际 0 行", rows.get() > 0);
        }
    }
}
