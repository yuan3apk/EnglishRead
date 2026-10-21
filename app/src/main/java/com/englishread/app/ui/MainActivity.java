package com.englishread.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.englishread.app.App;
import com.englishread.app.R;
import com.englishread.app.data.BookDao;
import com.englishread.app.data.Db;
import com.englishread.app.model.Book;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/** 书架首页：列书、导入、删除。 */
public class MainActivity extends AppCompatActivity {

    private RecyclerView list;
    private View empty;
    private LibraryAdapter adapter;
    private BookDao dao;
    private ImportHelper importHelper;

    private final ActivityResultLauncher<String[]> openDoc =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onPicked);

    /** 两条导入路径共用一套提示与刷新，别各写一份。 */
    private final ImportHelper.Callback onImportDone = new ImportHelper.Callback() {
        @Override
        public void onImported(String title) {
            toast("已导入：" + title);
            reload();
        }

        @Override
        public void onFailed(String reason) {
            toast(reason);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        dao = new BookDao(Db.of(this));
        importHelper = new ImportHelper(this, ((App) getApplication()).io());

        View main = findViewById(R.id.main);
        empty = findViewById(R.id.empty);
        list = findViewById(R.id.book_list);

        adapter = new LibraryAdapter(new ArrayList<>(), this::openBook, this::confirmDelete);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        findViewById(R.id.fab_import).setOnClickListener(v -> showImportSource());

        ViewCompat.setOnApplyWindowInsetsListener(main, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    /** 导入入口：先选来源。本地走 SAF，URL 走下载。 */
    private void showImportSource() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.action_import)
                .setItems(new CharSequence[]{
                        getString(R.string.import_from_file), getString(R.string.import_from_url)},
                        (d, which) -> {
                            if (which == 0) {
                                openDoc.launch(ImportHelper.mimeTypes());
                            } else {
                                askForUrl();
                            }
                        })
                .show();
    }

    private void askForUrl() {
        View form = getLayoutInflater().inflate(R.layout.dialog_import_url, null);
        EditText field = form.findViewById(R.id.url_input);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.import_from_url)
                .setView(form)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton("导入", null) // 真监听在 show() 后挂：地址不合法时不能关对话框
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String url = field.getText().toString().trim();
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                toast(getString(R.string.import_url_invalid));
                return;
            }
            dialog.dismiss();
            toast(getString(R.string.library_downloading));
            importHelper.importUrl(url, onImportDone);
        });
    }

    private void onPicked(Uri uri) {
        if (uri == null) {
            return; // 用户取消了，不算失败
        }
        // 选一次就够，不必持久化授予——文件已拷进私有目录
        toast(getString(R.string.library_importing));
        importHelper.importUri(uri, onImportDone);
    }

    /** 书都在本地，几百本也只是一次全表扫，不值得上分页或 CursorLoader。 */
    private void reload() {
        ((App) getApplication()).io().execute(() -> {
            List<Book> books = dao.listAll();
            runOnUiThread(() -> {
                adapter.replace(books);
                boolean none = books.isEmpty();
                empty.setVisibility(none ? View.VISIBLE : View.GONE);
                list.setVisibility(none ? View.GONE : View.VISIBLE);
            });
        });
    }

    private void openBook(Book book) {
        startActivity(new Intent(this, ReaderActivity.class)
                .putExtra(ReaderActivity.EXTRA_BOOK_ID, book.id));
    }

    private void confirmDelete(Book book) {
        new AlertDialog.Builder(this)
                .setTitle(book.title)
                .setMessage("从书架删除这本书？译文和进度一并删除。")
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton("删除", (d, w) -> deleteBook(book.id))
                .show();
    }

    private void deleteBook(long bookId) {
        ((App) getApplication()).io().execute(() -> {
            dao.delete(bookId);
            runOnUiThread(this::reload);
        });
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
