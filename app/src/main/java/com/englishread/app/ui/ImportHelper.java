package com.englishread.app.ui;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import com.englishread.app.R;
import com.englishread.app.data.BookDao;
import com.englishread.app.data.Db;
import com.englishread.app.parse.BookParser;
import com.englishread.app.parse.ParsedBook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * SAF 导入：选文件 → 拷进私有目录 → 解析 → 入库。
 *
 * <p>全流程在 IO 线程，回调切主线程。任何一步失败都删掉半成品文件，
 * 保证「要么书架多一本书，要么什么都没变」。
 */
public final class ImportHelper {

    /** 导入结果回调，一律在主线程。 */
    public interface Callback {
        void onImported(String title);

        void onFailed(String reason);
    }

    private final Context app;
    private final ExecutorService io;
    private final Handler main = new Handler(Looper.getMainLooper());

    public ImportHelper(Context c, ExecutorService io) {
        this.app = c.getApplicationContext();
        this.io = io;
    }

    /** 供 SAF 用的 mime 过滤。 */
    public static String[] mimeTypes() {
        return new String[]{"application/epub+zip", "text/plain", "application/octet-stream"};
    }

    public void importUri(Uri uri, Callback cb) {
        io.execute(() -> {
            File copied = null;
            try {
                String displayName = queryDisplayName(uri);
                if (!BookParser.isSupported(displayName)) {
                    postFail(cb, app.getString(R.string.library_import_failed, "仅支持 EPUB / TXT"));
                    return;
                }

                copied = copyToPrivate(uri, displayName);
                File coverDir = new File(app.getFilesDir(), "covers");
                if (!coverDir.exists() && !coverDir.mkdirs()) {
                    throw new IOException("无法创建封面目录");
                }

                ParsedBook pb = BookParser.parse(copied, titleFromName(displayName), coverDir);
                new BookDao(Db.of(app)).insert(pb, copied.getAbsolutePath(), pb.coverPath);

                String title = pb.title;
                main.post(() -> cb.onImported(title));
            } catch (Exception e) {
                // 失败别留垃圾文件，否则重试会堆一堆
                if (copied != null && copied.exists() && !copied.delete()) {
                    copied.deleteOnExit();
                }
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                postFail(cb, app.getString(R.string.library_import_failed, msg));
            }
        });
    }

    private void postFail(Callback cb, String reason) {
        main.post(() -> cb.onFailed(reason));
    }

    private File copyToPrivate(Uri uri, String displayName) throws IOException {
        File dir = new File(app.getFilesDir(), "books");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建书籍目录");
        }
        String ext = nameExt(displayName);
        File out = new File(dir, UUID.randomUUID() + ext);
        try (InputStream in = app.getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(out)) {
            if (in == null) {
                throw new IOException("打不开这个文件");
            }
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                fos.write(buf, 0, n);
            }
        }
        return out;
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor c = app.getContentResolver()
                .query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String n = c.getString(0);
                if (n != null && !n.trim().isEmpty()) {
                    return n;
                }
            }
        } catch (Exception ignored) {
            // 有些 provider 不实现 query，退回 mime 猜扩展名
        }
        String mime = app.getContentResolver().getType(uri);
        String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        return "book" + (ext == null ? "" : "." + ext);
    }

    private static String nameExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot).toLowerCase() : ".txt";
    }

    private static String titleFromName(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
