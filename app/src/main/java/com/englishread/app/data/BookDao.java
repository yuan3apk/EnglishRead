package com.englishread.app.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import com.englishread.app.model.Book;
import com.englishread.app.parse.ParsedBook;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** 书架与整本入库。 */
public class BookDao {

    private final Db db;

    public BookDao(Db db) {
        this.db = db;
    }

    public List<Book> listAll() {
        List<Book> out = new ArrayList<>();
        String sql = "SELECT id, title, author, format, cover_path, progress_pct, last_opened_at"
                + " FROM book ORDER BY last_opened_at DESC, added_at DESC";
        try (Cursor c = db.getReadableDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) {
                Book b = new Book();
                b.id = c.getLong(0);
                b.title = c.getString(1);
                b.author = c.getString(2);
                b.format = c.getString(3);
                b.coverPath = c.getString(4);
                b.progressPct = c.getFloat(5);
                b.lastOpenedAt = c.getLong(6);
                out.add(b);
            }
        }
        return out;
    }

    /**
     * 整本入库：一个事务写完 book / chapter / paragraph。
     * 大书按章写入会更快出首屏，但那样失败就留下半本书，v1 选择要么全有要么全无。
     */
    public long insert(ParsedBook pb, String srcPath, String coverPath) {
        SQLiteDatabase d = db.getWritableDatabase();
        d.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put("title", pb.title);
            v.put("author", pb.author);
            v.put("format", pb.format);
            v.put("src_path", srcPath);
            v.put("cover_path", coverPath);
            v.put("added_at", System.currentTimeMillis());
            v.put("progress_pct", 0f);
            long bookId = d.insertOrThrow("book", null, v);

            for (int i = 0; i < pb.chapters.size(); i++) {
                ParsedBook.Chapter ch = pb.chapters.get(i);
                ContentValues cv = new ContentValues();
                cv.put("book_id", bookId);
                cv.put("idx", i);
                cv.put("title", ch.title);
                cv.put("href", ch.href);
                cv.put("para_count", ch.paragraphs.size());
                long chapterId = d.insertOrThrow("chapter", null, cv);

                SQLiteStatement st = d.compileStatement(
                        "INSERT INTO paragraph (chapter_id, idx, text) VALUES (?, ?, ?)");
                try {
                    for (int p = 0; p < ch.paragraphs.size(); p++) {
                        st.bindLong(1, chapterId);
                        st.bindLong(2, p);
                        st.bindString(3, ch.paragraphs.get(p));
                        st.executeInsert();
                    }
                } finally {
                    st.close();
                }
            }

            ContentValues upd = new ContentValues();
            upd.put("chapter_count", pb.chapters.size());
            d.update("book", upd, "id = ?", new String[]{String.valueOf(bookId)});

            d.setTransactionSuccessful();
            return bookId;
        } finally {
            d.endTransaction();
        }
    }

    /** 删书连带删掉私有目录里的源文件与封面，否则删得越多磁盘越满。 */
    public void delete(long bookId) {
        SQLiteDatabase d = db.getWritableDatabase();
        List<String> files = new ArrayList<>();

        try (Cursor c = d.rawQuery(
                "SELECT src_path, cover_path FROM book WHERE id = ?",
                new String[]{String.valueOf(bookId)})) {
            if (c.moveToFirst()) {
                if (!c.isNull(0)) {
                    files.add(c.getString(0));
                }
                if (!c.isNull(1)) {
                    files.add(c.getString(1));
                }
            }
        }

        d.beginTransaction();
        try {
            d.execSQL("DELETE FROM paragraph WHERE chapter_id IN"
                    + " (SELECT id FROM chapter WHERE book_id = ?)", new Object[]{bookId});
            d.execSQL("DELETE FROM chapter WHERE book_id = ?", new Object[]{bookId});
            d.execSQL("DELETE FROM reading_pos WHERE book_id = ?", new Object[]{bookId});
            d.execSQL("DELETE FROM book WHERE id = ?", new Object[]{bookId});
            d.setTransactionSuccessful();
        } finally {
            d.endTransaction();
        }

        // 提交后才删文件：事务回滚时书还在，文件不能先没
        for (String path : files) {
            File f = new File(path);
            if (f.exists() && !f.delete()) {
                f.deleteOnExit();
            }
        }
    }
}
