package com.englishread.app.model;

/** 生词本一行。字段口径见 HANDOFF.md 第 3 节的 vocab 表。 */
public class VocabEntry {
    public long id;
    /** 用户点到的原词，如 "ran" */
    public String word;
    /** 词典原形，也是生词本的去重键，如 "run" */
    public String lemma;
    public String phon;
    public String translation;
    /** 命中处的原句。一句话一段，所以它就是那一段的原文。 */
    public String sentence;
    public long bookId;
    public long chapterId;
    public long paraId;
    public long createdAt;
    public boolean mastered;
    /** 列表展示用，来自 book 表 JOIN（库里不存）。 */
    public String bookTitle;
}
