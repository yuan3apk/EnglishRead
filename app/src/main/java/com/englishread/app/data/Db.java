package com.englishread.app.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * 业务库。6 张表，见 HANDOFF.md 第 3 节。
 *
 * <p>用裸 SQLiteOpenHelper 而不是 Room：只有 6 张表、DAO 手写不到 200 行，
 * 换 Room 要多一个注解处理器和两个依赖（见 HANDOFF 第 1 节）。
 */
public class Db extends SQLiteOpenHelper {

    private static final String NAME = "englishread.db";
    private static final int VERSION = 1;

    private static volatile Db instance;

    public static Db of(Context c) {
        if (instance == null) {
            synchronized (Db.class) {
                if (instance == null) {
                    instance = new Db(c.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private Db(Context c) {
        super(c, NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE book ("
                + " id INTEGER PRIMARY KEY, title TEXT, author TEXT,"
                + " format TEXT, src_path TEXT, cover_path TEXT,"
                + " chapter_count INTEGER, added_at INTEGER, last_opened_at INTEGER,"
                + " progress_pct REAL DEFAULT 0)");

        d.execSQL("CREATE TABLE chapter ("
                + " id INTEGER PRIMARY KEY, book_id INTEGER, idx INTEGER,"
                + " title TEXT, href TEXT, para_count INTEGER)");
        d.execSQL("CREATE INDEX idx_chapter_book ON chapter(book_id, idx)");

        d.execSQL("CREATE TABLE paragraph ("
                + " id INTEGER PRIMARY KEY, chapter_id INTEGER, idx INTEGER,"
                + " text TEXT, trans TEXT,"
                + " trans_status INTEGER DEFAULT 0, trans_engine TEXT, trans_at INTEGER)");
        d.execSQL("CREATE INDEX idx_para_chapter ON paragraph(chapter_id, idx)");

        d.execSQL("CREATE TABLE word_cache ("
                + " word TEXT PRIMARY KEY, lemma TEXT, phonetic TEXT,"
                + " translation TEXT, source TEXT, updated_at INTEGER)");

        d.execSQL("CREATE TABLE vocab ("
                + " id INTEGER PRIMARY KEY, word TEXT, lemma TEXT, phon TEXT,"
                + " translation TEXT, sentence TEXT,"
                + " book_id INTEGER, chapter_id INTEGER, para_id INTEGER,"
                + " created_at INTEGER, mastered INTEGER DEFAULT 0)");
        d.execSQL("CREATE UNIQUE INDEX idx_vocab_word ON vocab(lemma)");

        d.execSQL("CREATE TABLE reading_pos ("
                + " book_id INTEGER PRIMARY KEY, chapter_id INTEGER, para_id INTEGER,"
                + " in_para_offset INTEGER, updated_at INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase d, int oldVersion, int newVersion) {
        // v1 无迁移。加表时按 oldVersion 递增，别 drop 用户数据。
    }
}
