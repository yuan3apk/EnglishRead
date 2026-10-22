package com.englishread.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.englishread.app.App;
import com.englishread.app.R;
import com.englishread.app.data.Db;
import com.englishread.app.data.VocabDao;
import com.englishread.app.model.Book;
import com.englishread.app.model.VocabEntry;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 生词本：列表 + 标记已掌握 + 按书筛选。
 *
 * <p>每次筛选或勾选都整表重查一遍——几百行、单线程 IO 上几毫秒，
 * 比维护「本地删行 + 数据库同步」的两套状态省心得多。
 */
public class VocabActivity extends AppCompatActivity {

    private VocabDao dao;
    private VocabAdapter adapter;
    private RecyclerView listView;
    private TextView empty;
    /** 空态整块（图标 + 文案）：只切文案的话，列表有内容时那个图标会一直浮在上面。 */
    private View emptyBox;
    /** 按书筛选：按钮显示当前选中的书，点开是单选弹窗（原来是个原生 Spinner）。 */
    private MaterialButton bookFilter;
    private Chip onlyUnmastered;

    /** 0 = 全部书 */
    private long filterBookId;
    /** 下拉里可选的书（只含生词本里出现过的）。 */
    private final List<Book> books = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vocab);

        MaterialToolbar bar = findViewById(R.id.vocab_toolbar);
        bar.setNavigationOnClickListener(v -> finish());

        View root = findViewById(R.id.vocab_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        dao = new VocabDao(Db.of(this));
        empty = findViewById(R.id.vocab_empty);
        emptyBox = findViewById(R.id.vocab_empty_box);
        listView = findViewById(R.id.vocab_list);
        bookFilter = findViewById(R.id.vocab_book_filter);
        onlyUnmastered = findViewById(R.id.vocab_only_unmastered);

        adapter = new VocabAdapter(this::markMastered);
        listView.setLayoutManager(new LinearLayoutManager(this));
        listView.setAdapter(adapter);

        bookFilter.setOnClickListener(v -> showBookPicker());

        onlyUnmastered.setChecked(false);
        onlyUnmastered.setOnCheckedChangeListener((b, checked) -> load());

        load();
    }

    /** 按书筛选：单选弹窗比 Spinner 好按、也好读，选项只有「全部 + 有生词的书」。 */
    private void showBookPicker() {
        List<String> names = new ArrayList<>();
        names.add(getString(R.string.vocab_filter_all));
        int checked = 0;
        for (int i = 0; i < books.size(); i++) {
            Book b = books.get(i);
            names.add(b.title == null ? "" : b.title);
            if (b.id == filterBookId) {
                checked = i + 1;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.vocab_filter_title)
                .setSingleChoiceItems(names.toArray(new CharSequence[0]), checked, (d, which) -> {
                    d.dismiss();
                    long target = which <= 0 ? 0 : books.get(which - 1).id;
                    if (target == filterBookId) {
                        return;
                    }
                    filterBookId = target;
                    bookFilter.setText(names.get(which));
                    load();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /** 勾「已掌握」：先写库再整表重查，界面上不会出现「勾了又没勾」的中间态。 */
    private void markMastered(VocabEntry entry, boolean mastered) {
        ((App) getApplication()).io().execute(() -> {
            dao.setMastered(entry.id, mastered);
            runOnUiThread(this::load);
        });
    }

    private void load() {
        final long bookId = filterBookId;
        final boolean unmastered = onlyUnmastered.isChecked();
        ((App) getApplication()).io().execute(() -> {
            List<VocabEntry> rows = dao.list(bookId, unmastered);
            List<Book> withVocab = dao.books();
            boolean filtered = bookId != 0 || unmastered;
            runOnUiThread(() -> {
                adapter.replace(rows);
                boolean none = rows.isEmpty();
                // 空列表有两种意思：真的一条都没有，还是被筛选滤光了——文案必须分开
                empty.setText(filtered ? R.string.vocab_empty_filtered : R.string.vocab_empty);
                emptyBox.setVisibility(none ? View.VISIBLE : View.GONE);
                listView.setVisibility(none ? View.GONE : View.VISIBLE);
                syncBooks(withVocab);
            });
        });
    }

    /**
     * 刷新可选书目：第一项「全部书」，后面只列生词本里有词的书。
     *
     * <p>当前筛选的书不见了（最后一个词标成已掌握、或书被删）就退回「全部」重查，
     * 否则页面会停在「选中一本没有词的书」的空列表上，用户还以为生词本丢了。
     */
    private void syncBooks(List<Book> fresh) {
        books.clear();
        books.addAll(fresh);

        String label = getString(R.string.vocab_filter_all);
        boolean found = false;
        for (Book b : books) {
            if (b.id == filterBookId) {
                label = b.title == null ? "" : b.title;
                found = true;
            }
        }
        bookFilter.setText(label);

        if (!found && filterBookId != 0) {
            filterBookId = 0;
            bookFilter.setText(R.string.vocab_filter_all);
            load();
        }
    }
}
