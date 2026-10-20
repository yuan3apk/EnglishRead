package com.englishread.app.parse;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析器的输出：一本书的标题、作者与全部章节段落。
 * 纯 Java，不碰 Android 类，方便在 JVM 单测里直接构造。
 */
public class ParsedBook {

    public String title;
    public String author;
    /** 'epub' | 'txt' */
    public String format;
    public String coverPath;
    public final List<Chapter> chapters = new ArrayList<>();

    public static class Chapter {
        public String title;
        public String href;
        public final List<String> paragraphs = new ArrayList<>();

        public Chapter() {
        }

        public Chapter(String title, String href) {
            this.title = title;
            this.href = href;
        }
    }

    public static ParsedBook of(String format) {
        ParsedBook pb = new ParsedBook();
        pb.format = format;
        return pb;
    }

    public int paragraphCount() {
        int n = 0;
        for (Chapter c : chapters) {
            n += c.paragraphs.size();
        }
        return n;
    }
}
