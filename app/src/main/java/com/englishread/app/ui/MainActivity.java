package com.englishread.app.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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

        adapter = new LibraryAdapter(new ArrayList<>(), this::confirmDelete);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        findViewById(R.id.fab_import).setOnClickListener(v -> openDoc.launch(ImportHelper.mimeTypes()));

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

    private void onPicked(Uri uri) {
        if (uri == null) {
            return; // 用户取消了，不算失败
        }
        // 选一次就够，不必持久化授予——文件已拷进私有目录
        toast(getString(R.string.library_importing));
        importHelper.importUri(uri, new ImportHelper.Callback() {
            @Override
            public void onImported(String title) {
                toast("已导入：" + title);
                reload();
            }

            @Override
            public void onFailed(String reason) {
                toast(reason);
            }
        });
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

    private void confirmDelete(Book book) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(book.title)
                .setMessage("从书架删除这本书？译文和进度一并删除。")
                .setNegativeButton(android.R.string.cancel, null)
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
