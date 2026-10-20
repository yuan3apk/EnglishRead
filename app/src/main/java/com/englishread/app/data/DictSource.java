package com.englishread.app.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.englishread.app.model.WordEntry;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 内置词典查询：form 反查表 → dict 表 → word_cache。
 *
 * <p>assets 里的库不能直接开写，先拷到 databases/ 再开。只读查询，
 * 所以不做事务也不加锁——SQLite 多读无碍。
 */
public class DictSource {

    private static final String ASSET = "dict.db";
    private static final String DB_NAME = "dict.db";

    private final Context app;
    private SQLiteDatabase db;

    public DictSource(Context c) {
        this.app = c.getApplicationContext();
    }

    /** 词典是否存在。没打包词典时阅读器要能优雅降级，而不是崩。 */
    public static boolean isBundled(Context c) {
        try (InputStream in = c.getAssets().open(ASSET)) {
            return in != null;
        } catch (IOException e) {
            return false;
        }
    }

    private SQLiteDatabase db() throws IOException {
        if (db != null && db.isOpen()) {
            return db;
        }
        File out = new File(app.getDatabasePath(DB_NAME).getAbsolutePath());
        // getDatabasePath 在部分机型上可能返回不存在的父目录
        File parent = out.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建词典目录");
        }
        if (!out.exists() || out.length() == 0) {
            try (InputStream in = app.getAssets().open(ASSET);
                 OutputStream fos = new FileOutputStream(out)) {
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    fos.write(buf, 0, n);
                }
            }
        }
        db = SQLiteDatabase.openDatabase(out.getAbsolutePath(), null,
                SQLiteDatabase.OPEN_READONLY);
        return db;
    }

    /**
     * 查一个词。先按原形反查（ran→run），再直接查词条，
     * 最后才落到业务库的 word_cache（网络补译过的词）。
     *
     * <p>注意 word_cache 在业务库 Db 里，不在 assets/dict.db 里——
     * assets 库只有 dict / form 两张表。
     */
    public WordEntry lookup(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        try {
            SQLiteDatabase d = db();
            String lemma = lemmaOf(d, key);
            WordEntry e = dictRow(d, lemma);
            if (e != null) {
                e.word = key;
                e.lemma = lemma;
                return e;
            }
        } catch (IOException e) {
            // 没有词典文件不是错误路径，是未打包的降级路径。
            // 继续走 word_cache，说不定之前网络补译过。
        }
        return cached(key);
    }

    private static String lemmaOf(SQLiteDatabase d, String key) {
        try (Cursor c = d.rawQuery("SELECT lemma FROM form WHERE form = ?",
                new String[]{key})) {
            if (c.moveToFirst()) {
                String lemma = c.getString(0);
                if (lemma != null && !lemma.isEmpty()) {
                    return lemma;
                }
            }
        }
        return key;
    }

    private static WordEntry dictRow(SQLiteDatabase d, String key) {
        try (Cursor c = d.rawQuery(
                "SELECT word, phonetic, translation FROM dict WHERE word = ?",
                new String[]{key})) {
            if (c.moveToFirst()) {
                WordEntry e = new WordEntry();
                e.lemma = c.getString(0);
                e.phonetic = c.isNull(1) ? null : c.getString(1);
                e.translation = c.isNull(2) ? null : c.getString(2);
                return e;
            }
        }
        return null;
    }

    private WordEntry cached(String key) {
        // word_cache 属于业务库，不是词典库
        try (Cursor c = Db.of(app).getReadableDatabase().rawQuery(
                "SELECT lemma, phonetic, translation FROM word_cache WHERE word = ?",
                new String[]{key})) {
            if (c.moveToFirst()) {
                WordEntry e = new WordEntry();
                e.lemma = c.isNull(0) ? key : c.getString(0);
                e.phonetic = c.isNull(1) ? null : c.getString(1);
                e.translation = c.isNull(2) ? null : c.getString(2);
                e.word = key;
                return e;
            }
        }
        return null;
    }
}
