package com.englishread.app.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.englishread.app.model.Chapter;
import com.englishread.app.model.Paragraph;

import java.util.ArrayList;
import java.util.List;

/** 章节列表、整章段落、段落译文回写。 */
public class ParagraphDao {

    private final Db db;

    public ParagraphDao(Db db) {
        this.db = db;
    }

    /** 章节目录，按 spine 顺序。 */
    public List<Chapter> chapters(long bookId) {
        List<Chapter> out = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT id, idx, title, para_count FROM chapter WHERE book_id = ? ORDER BY idx",
                new String[]{String.valueOf(bookId)})) {
            while (c.moveToNext()) {
                Chapter ch = new Chapter();
                ch.id = c.getLong(0);
                ch.idx = c.getInt(1);
                ch.title = c.getString(2);
                ch.paraCount = c.getInt(3);
                ch.bookId = bookId;
                out.add(ch);
            }
        }
        return out;
    }

    /** 首次打开一本书时的第一章。 */
    public Chapter firstChapter(long bookId) {
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT id, idx, title, para_count FROM chapter WHERE book_id = ? ORDER BY idx LIMIT 1",
                new String[]{String.valueOf(bookId)})) {
            if (c.moveToFirst()) {
                Chapter ch = new Chapter();
                ch.id = c.getLong(0);
                ch.idx = c.getInt(1);
                ch.title = c.getString(2);
                ch.paraCount = c.getInt(3);
                ch.bookId = bookId;
                return ch;
            }
        }
        return null;
    }

    public List<Paragraph> paragraphs(long chapterId) {
        List<Paragraph> out = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT id, idx, text, trans, trans_status FROM paragraph"
                        + " WHERE chapter_id = ? ORDER BY idx",
                new String[]{String.valueOf(chapterId)})) {
            while (c.moveToNext()) {
                Paragraph p = new Paragraph();
                p.id = c.getLong(0);
                p.idx = c.getInt(1);
                p.text = c.getString(2);
                p.trans = c.isNull(3) ? null : c.getString(3);
                p.transStatus = c.getInt(4);
                p.chapterId = chapterId;
                out.add(p);
            }
        }
        return out;
    }

    /**
     * 译文回写。trans_status: 0 未译 1 排队 2 已译 3 失败。
     * 已译（3=失败以外）不再覆盖：缓存是控成本的命门，重复请求是钱。
     */
    public void saveTranslation(long paraId, String trans, int status) {
        ContentValues v = new ContentValues();
        v.put("trans", trans);
        v.put("trans_status", status);
        if (status == Paragraph.STATUS_DONE) {
            v.put("trans_at", System.currentTimeMillis());
        }
        db.getWritableDatabase().update("paragraph", v, "id = ?",
                new String[]{String.valueOf(paraId)});
    }

    /** 整章未译的段落数，用于「本章还没译完」的提示。 */
    public int untranslatedCount(long chapterId) {
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM paragraph WHERE chapter_id = ? AND trans_status <> 2",
                new String[]{String.valueOf(chapterId)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public SQLiteDatabase raw() {
        return db.getWritableDatabase();
    }
}
