package com.englishread.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.englishread.app.App;
import com.englishread.app.R;
import com.englishread.app.data.Db;
import com.englishread.app.data.DictSource;
import com.englishread.app.data.ParagraphDao;
import com.englishread.app.model.Chapter;
import com.englishread.app.model.Paragraph;
import com.englishread.app.model.WordEntry;
import com.englishread.app.parse.Tokenizer;
import com.englishread.app.reader.ReaderAdapter;
import com.englishread.app.reader.TranslationPopup;

import java.util.List;

/**
 * 阅读器：单章连续滚动。
 *
 * <p>M2 范围：读段落、点词查本地词典、点「段」展开译文。
 * 段落译文要等 M3 的 LLM，现在只给「未配置」提示，不假装在翻译。
 */
public class ReaderActivity extends AppCompatActivity implements ReaderAdapter.Listener {

    public static final String EXTRA_BOOK_ID = "book_id";

    private RecyclerView list;
    private TextView chapterTitle;
    private ReaderAdapter adapter;
    private ParagraphDao dao;
    private DictSource dict;
    private TranslationPopup popup;

    private long bookId;
    private List<Chapter> chapters;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_reader);

        bookId = getIntent().getLongExtra(EXTRA_BOOK_ID, -1);
        if (bookId < 0) {
            finish();
            return;
        }

        dao = new ParagraphDao(Db.of(this));
        dict = new DictSource(this);

        list = findViewById(R.id.para_list);
        chapterTitle = findViewById(R.id.chapter_title);
        findViewById(R.id.btn_prev).setOnClickListener(v -> stepChapter(-1));
        findViewById(R.id.btn_next).setOnClickListener(v -> stepChapter(1));

        adapter = new ReaderAdapter(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        popup = new TranslationPopup(this, new TranslationPopup.Listener() {
            @Override
            public void onSpeak(String word) {
                // M4 接 TTS，现在给个可见反馈而不是静默
                Toast.makeText(ReaderActivity.this, word, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAddVocab(WordEntry entry) {
                // M5 接生词本
                Toast.makeText(ReaderActivity.this, R.string.vocab_added, Toast.LENGTH_SHORT).show();
            }
        });

        View root = findViewById(R.id.reader);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        loadChapters();
    }

    private void loadChapters() {
        ((App) getApplication()).io().execute(() -> {
            List<Chapter> cs = dao.chapters(bookId);
            runOnUiThread(() -> {
                if (cs.isEmpty()) {
                    finish();
                    return;
                }
                chapters = cs;
                openChapter(0);
            });
        });
    }

    private void stepChapter(int delta) {
        if (chapters == null) {
            return;
        }
        int target = chapters.indexOf(currentChapter()) + delta;
        if (target >= 0 && target < chapters.size()) {
            openChapter(target);
        }
    }

    private Chapter currentChapter() {
        if (chapters == null || chapters.isEmpty()) {
            return null;
        }
        return chapters.get(currentIdx);
    }

    private int currentIdx;

    private void openChapter(int idx) {
        if (chapters == null || idx < 0 || idx >= chapters.size()) {
            return;
        }
        currentIdx = idx;
        Chapter ch = chapters.get(idx);
        chapterTitle.setText(ch.title);
        // 到头就禁用，别让用户点了没反应还以为卡了
        findViewById(R.id.btn_prev).setEnabled(idx > 0);
        findViewById(R.id.btn_next).setEnabled(idx < chapters.size() - 1);

        ((App) getApplication()).io().execute(() -> {
            List<Paragraph> ps = dao.paragraphs(ch.id);
            runOnUiThread(() -> {
                adapter.submit(ps);
                list.scrollToPosition(0);
            });
        });
    }

    // ---- ReaderAdapter.Listener ----

    @Override
    public void onTranslateToggle(Paragraph p) {
        // M3 之前没有 LLM：给可操作提示，不假装在翻译
        Toast.makeText(this, R.string.reader_need_api_key, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onTranslateRetry(Paragraph p) {
        p.transStatus = Paragraph.STATUS_NONE;
        dao.saveTranslation(p.id, p.trans, Paragraph.STATUS_NONE);
        adapter.updateParagraph(p);
    }

    @Override
    public void onWordTap(Paragraph p, String word, int start, int end) {
        showWordCard(p, word, start, end);
    }

    @Override
    public void onWordLongPress(Paragraph p, String word, int start, int end) {
        // M5 接生词本 + M4 接朗读。在那之前至少让长按有可见反馈，不是按了没反应。
        showWordCard(p, word, start, end);
    }

    /** 查词走 IO 线程，弹窗回主线程；两条手势共用这一条路径。 */
    private void showWordCard(Paragraph p, String word, int start, int end) {
        int[] anchor = wordRect(p, start, end);
        if (anchor == null) {
            return; // 段落还没画出来，宁可不弹
        }
        String key = Tokenizer.lookupKey(word);
        ((App) getApplication()).io().execute(() -> {
            WordEntry entry = dict.lookup(key);
            runOnUiThread(() -> popup.show(findViewById(R.id.reader), anchor, word, entry));
        });
    }

    /**
     * 词的矩形：[x, y, w, h]，屏幕坐标。
     * getPrimaryHorizontal 返回的是 Layout 坐标，要扣掉 TextView 的内边距才对得上。
     */
    private int[] wordRect(Paragraph p, int start, int end) {
        RecyclerView.ViewHolder vh = list.findViewHolderForAdapterPosition(p.idx);
        if (!(vh instanceof ReaderAdapter.VH)) {
            return null;
        }
        TextView tv = ((ReaderAdapter.VH) vh).text;
        android.text.Layout layout = tv.getLayout();
        if (layout == null) {
            return null;
        }
        int line = layout.getLineForOffset(start);
        int left = (int) layout.getPrimaryHorizontal(start) + tv.getTotalPaddingLeft();
        int right = (int) layout.getPrimaryHorizontal(end) + tv.getTotalPaddingLeft();
        int top = layout.getLineTop(line) + tv.getTotalPaddingTop();
        int bottom = layout.getLineBottom(line) + tv.getTotalPaddingTop();

        int[] loc = new int[2];
        tv.getLocationOnScreen(loc);
        return new int[]{loc[0] + left, loc[1] + top, right - left, bottom - top};
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (popup != null) {
            popup.dismiss();
        }
    }
}
