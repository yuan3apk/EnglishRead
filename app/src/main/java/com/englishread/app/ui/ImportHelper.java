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
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * 导入：本地文件（SAF）或 URL 直链 → 拷进私有目录 → 解析 → 入库。
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

    /** 一本书超过这个体积基本是给错了地址（也顺手挡住把磁盘写满）。 */
    private static final long MAX_BYTES = 100L * 1024 * 1024;

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
                String title = save(copied, titleFromName(displayName));
                main.post(() -> cb.onImported(title));
            } catch (Exception e) {
                deleteQuietly(copied);
                fail(cb, e);
            }
        });
    }

    /**
     * URL 导入：下载到 books/ 后走跟本地导入一模一样的解析入库路径。
     * 格式靠 URL 后缀或响应 Content-Type 认，两者都看不出就拒绝——猜错等于把网页当书。
     */
    public void importUrl(String url, Callback cb) {
        io.execute(() -> {
            File copied = null;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(15_000);
                c.setReadTimeout(30_000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", "EnglishRead"); // 有的站点没有 UA 直接 403
                try {
                    int code = c.getResponseCode();
                    if (code / 100 != 2) {
                        throw new IOException("HTTP " + code);
                    }
                    String name = pickName(url, c.getContentType());
                    if (name == null) {
                        throw new IOException("看不出是 EPUB 还是 TXT，请用以 .epub / .txt 结尾的直链");
                    }
                    copied = new File(booksDir(), UUID.randomUUID() + nameExt(name));
                    try (InputStream in = c.getInputStream()) {
                        writeTo(in, copied);
                    }
                    String title = save(copied, titleFromName(name));
                    main.post(() -> cb.onImported(title));
                } finally {
                    c.disconnect();
                }
            } catch (Exception e) {
                deleteQuietly(copied); // 下载到一半失败，别留半个文件占地方
                fail(cb, e);
            }
        });
    }

    /** URL 能认出的书名（含扩展名）；认不出返回 null。纯函数，单测在 {@code UrlImportNameTest}。 */
    static String pickName(String url, String contentType) {
        String n = fileNameFromUrl(url);
        if (BookParser.isSupported(n)) {
            return n;
        }
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        // 网页必须挡住：HTML 当纯文本入库只会得到一锅标签
        if (ct.contains("html")) {
            return null;
        }
        String host = hostOf(url);
        if (host.isEmpty()) {
            return null;
        }
        if (ct.contains("epub")) {
            return host + ".epub";
        }
        if (ct.startsWith("text/")) {
            return host + ".txt";
        }
        return null;
    }

    /** URL 最后一段（已解码、已去掉 query/fragment）；解析不了给空串。 */
    static String fileNameFromUrl(String url) {
        try {
            String path = new URL(url).getPath();
            return URLDecoder.decode(path.substring(path.lastIndexOf('/') + 1), "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static String hostOf(String url) {
        try {
            return new URL(url).getHost();
        } catch (MalformedURLException e) {
            return "";
        }
    }

    /** 解析 + 入库，成功返回书名。失败抛异常，半成品文件由调用方删。 */
    private String save(File file, String fallbackTitle) throws IOException {
        File coverDir = new File(app.getFilesDir(), "covers");
        if (!coverDir.exists() && !coverDir.mkdirs()) {
            throw new IOException("无法创建封面目录");
        }
        ParsedBook pb = BookParser.parse(file, fallbackTitle, coverDir);
        new BookDao(Db.of(app)).insert(pb, file.getAbsolutePath(), pb.coverPath);
        return pb.title;
    }

    private void postFail(Callback cb, String reason) {
        main.post(() -> cb.onFailed(reason));
    }

    private void fail(Callback cb, Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        postFail(cb, app.getString(R.string.library_import_failed, msg));
    }

    private File booksDir() throws IOException {
        File dir = new File(app.getFilesDir(), "books");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建书籍目录");
        }
        return dir;
    }

    private File copyToPrivate(Uri uri, String displayName) throws IOException {
        File out = new File(booksDir(), UUID.randomUUID() + nameExt(displayName));
        try (InputStream in = app.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IOException("打不开这个文件");
            }
            writeTo(in, out);
        }
        return out;
    }

    /** 落盘 + 体积上限；半成品在这个函数里就删掉，调用方拿到的要么完整要么没有。 */
    private static void writeTo(InputStream in, File out) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[16 * 1024];
            int n;
            long total = 0;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > MAX_BYTES) {
                    throw new IOException("文件超过 100 MB");
                }
                fos.write(buf, 0, n);
            }
        } catch (IOException e) {
            deleteQuietly(out);
            throw e;
        }
    }

    private static void deleteQuietly(File f) {
        if (f != null && f.exists() && !f.delete()) {
            f.deleteOnExit();
        }
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
