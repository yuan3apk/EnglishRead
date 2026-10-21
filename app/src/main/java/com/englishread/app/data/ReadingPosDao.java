package com.englishread.app.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.englishread.app.model.Chapter;

import java.util.List;

/**
 * 阅读进度：{@code reading_pos} 记「读到哪一段的哪一行」，{@code book} 表跟着记百分比与最后打开时间。
 *
 * <p>断点表 + 书架那两个列放一起，调用方只跑一个 DAO：分开写的话保存一次进度要 new 两个 DAO，
 * 而且两处一旦不一致（有断点没百分比）书架就会显示 0%。
 */
public class ReadingPosDao {

    /** 上次读到哪儿。 */
    public static final class Pos {
        public long chapterId;
        public long paraId;
        /** 那一段顶边相对列表顶的偏移，负值 = 已经往上滚过了。 */
        public int offsetPx;
    }

    private final Db db;

    public ReadingPosDao(Db db) {
        this.db = db;
    }

    /** 没读过返回 null。 */
    public Pos load(long bookId) {
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT chapter_id, para_id, in_para_offset FROM reading_pos WHERE book_id = ?",
                new String[]{String.valueOf(bookId)})) {
            if (!c.moveToFirst()) {
                return null;
            }
            Pos p = new Pos();
            p.chapterId = c.getLong(0);
            p.paraId = c.getLong(1);
            p.offsetPx = c.getInt(2);
            return p;
        }
    }

    /**
     * 存断点 + 刷新书架卡片。
     *
     * @param pct 全书百分比，见 {@link #progressPct}
     */
    public void save(long bookId, long chapterId, long paraId, int offsetPx, float pct) {
        SQLiteDatabase d = db.getWritableDatabase();
        long now = System.currentTimeMillis();

        ContentValues v = new ContentValues();
        v.put("book_id", bookId);
        v.put("chapter_id", chapterId);
        v.put("para_id", paraId);
        v.put("in_para_offset", offsetPx);
        v.put("updated_at", now);
        d.insertWithOnConflict("reading_pos", null, v, SQLiteDatabase.CONFLICT_REPLACE);

        // last_opened_at 也是书架排序键（BookDao.listAll 的 ORDER BY 已经指望它），别漏
        ContentValues b = new ContentValues();
        b.put("progress_pct", pct);
        b.put("last_opened_at", now);
        d.update("book", b, "id = ?", new String[]{String.valueOf(bookId)});
    }

    /**
     * 全书进度 =（前面章节的段数 + 本章已读段数）/ 总段数。纯函数，单测在 {@code ProgressPctTest}。
     *
     * @param chapterPos    当前章在 list 里的下标（从 0 起）
     * @param parasRead     本章已读段数（读到第一句就是 1，不是 0——否则最后一章永远到不了 100%）
     */
    public static float progressPct(List<Chapter> chapters, int chapterPos, int parasRead) {
        int total = 0;
        int done = 0;
        for (int i = 0; i < chapters.size(); i++) {
            int n = Math.max(0, chapters.get(i).paraCount);
            total += n;
            if (i < chapterPos) {
                done += n;
            }
        }
        if (total <= 0) {
            return 0f;
        }
        return Math.min(1f, (done + Math.max(0, parasRead)) / (float) total);
    }
}
