package com.englishread.app.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.englishread.app.model.Book;
import com.englishread.app.model.VocabEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * 生词本。去重键是 lemma（unique index `idx_vocab_word`）：同词的不同变形只留第一次那条，
 * 先入为主的那条带着语境更值钱，后来的不覆盖。
 */
public class VocabDao {

    private final Db db;

    public VocabDao(Db db) {
        this.db = db;
    }

    /**
     * 加入生词本。
     *
     * @return 新行 id；这个词已经在生词本里返回 -1
     */
    public long add(VocabEntry v) {
        ContentValues cv = new ContentValues();
        cv.put("word", v.word);
        // lemma 是唯一键：SQLite 的唯一索引允许多个 NULL，调用方必须保证非空
        cv.put("lemma", v.lemma == null || v.lemma.isEmpty() ? v.word : v.lemma);
        cv.put("phon", v.phon);
        cv.put("translation", v.translation);
        cv.put("sentence", v.sentence);
        cv.put("book_id", v.bookId);
        cv.put("chapter_id", v.chapterId);
        cv.put("para_id", v.paraId);
        cv.put("created_at", v.createdAt > 0 ? v.createdAt : System.currentTimeMillis());
        cv.put("mastered", v.mastered ? 1 : 0);
        return db.getWritableDatabase()
                .insertWithOnConflict("vocab", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    /**
     * @param bookId         0 = 全部书
     * @param onlyUnmastered true = 只看没掌握的
     */
    public List<VocabEntry> list(long bookId, boolean onlyUnmastered) {
        List<String> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (bookId != 0) {
            where.append(" AND v.book_id = ?");
            args.add(String.valueOf(bookId));
        }
        if (onlyUnmastered) {
            where.append(" AND v.mastered = 0");
        }
        String sql = "SELECT v.id, v.word, v.lemma, v.phon, v.translation, v.sentence,"
                + " v.book_id, v.chapter_id, v.para_id, v.created_at, v.mastered, b.title"
                + " FROM vocab v LEFT JOIN book b ON b.id = v.book_id"
                + where
                + " ORDER BY v.created_at DESC, v.id DESC";
        List<VocabEntry> out = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().rawQuery(sql, args.toArray(new String[0]))) {
            while (c.moveToNext()) {
                VocabEntry v = new VocabEntry();
                v.id = c.getLong(0);
                v.word = c.getString(1);
                v.lemma = c.getString(2);
                v.phon = c.isNull(3) ? null : c.getString(3);
                v.translation = c.isNull(4) ? null : c.getString(4);
                v.sentence = c.isNull(5) ? null : c.getString(5);
                v.bookId = c.getLong(6);
                v.chapterId = c.getLong(7);
                v.paraId = c.getLong(8);
                v.createdAt = c.getLong(9);
                v.mastered = c.getInt(10) != 0;
                v.bookTitle = c.isNull(11) ? null : c.getString(11);
                out.add(v);
            }
        }
        return out;
    }

    /** 生词本里出现过的书，给筛选下拉用（只填 id 与 title）。 */
    public List<Book> books() {
        List<Book> out = new ArrayList<>();
        String sql = "SELECT DISTINCT b.id, b.title FROM vocab v JOIN book b ON b.id = v.book_id"
                + " ORDER BY b.title";
        try (Cursor c = db.getReadableDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) {
                Book b = new Book();
                b.id = c.getLong(0);
                b.title = c.getString(1);
                out.add(b);
            }
        }
        return out;
    }

    public void setMastered(long id, boolean mastered) {
        ContentValues cv = new ContentValues();
        cv.put("mastered", mastered ? 1 : 0);
        db.getWritableDatabase().update("vocab", cv, "id = ?",
                new String[]{String.valueOf(id)});
    }
}
