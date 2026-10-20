package com.englishread.app.model;

/** 词典查词结果。查不到时 DictSource 返回 null，不返回空壳对象。 */
public class WordEntry {
    /** 用户点到的原词（已归一化），如 "ran" */
    public String word;
    /** 词典里的词条原形，如 "run" */
    public String lemma;
    public String phonetic;
    public String translation;
}
