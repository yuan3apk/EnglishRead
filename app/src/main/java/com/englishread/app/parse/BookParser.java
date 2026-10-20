package com.englishread.app.parse;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/** 按格式分派。格式只看扩展名，猜错也没有更好的办法——内容是用户给的文件。 */
public final class BookParser {

    private BookParser() {
    }

    public static boolean isSupported(String fileName) {
        String n = fileName == null ? "" : fileName.toLowerCase();
        return n.endsWith(".epub") || n.endsWith(".txt");
    }

    /**
     * @param src    已拷贝到 filesDir/books/ 的文件
     * @param coverOut 封面落地目录，EPUB 才有封面，TXT 返回 null
     */
    public static ParsedBook parse(File src, String fallbackTitle, File coverOut) throws IOException {
        String name = src.getName().toLowerCase();
        if (name.endsWith(".epub")) {
            return EpubParser.parse(src, fallbackTitle, coverOut);
        }
        try (FileInputStream in = new FileInputStream(src)) {
            return TxtParser.parse(TxtParser.readAll(in), fallbackTitle);
        }
    }
}
