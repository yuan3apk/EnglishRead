package com.englishread.app.model;

/** 书架条目。字段口径见 HANDOFF.md 第 3 节的 book 表。 */
public class Book {
    public long id;
    public String title;
    public String author;
    public String format;
    public String coverPath;
    public float progressPct;
    public long lastOpenedAt;
}
