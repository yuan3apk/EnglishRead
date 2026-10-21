package com.englishread.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * URL 导入认书名：认错就是线上 bug ——
 * 把网页当 TXT 入库得到一锅标签，或把 EPUB 存成 .txt 解析成一堆二进制。
 */
public class UrlImportNameTest {

    @Test
    public void keepsSupportedExtensionFromUrl() {
        assertEquals("book.epub", ImportHelper.pickName("https://example.com/a/book.epub?x=1", null));
    }

    @Test
    public void decodesPercentEscapesInFileName() {
        assertEquals("Pride and Prejudice.txt",
                ImportHelper.pickName("https://example.com/Pride%20and%20Prejudice.txt", null));
    }

    @Test
    public void fallsBackToContentTypeWhenUrlHasNoExtension() {
        assertEquals("example.com.txt", ImportHelper.pickName("https://example.com/books/1342",
                "text/plain; charset=utf-8"));
        assertEquals("example.com.epub", ImportHelper.pickName("https://example.com/d/1342",
                "application/epub+zip"));
    }

    @Test
    public void unsupportedExtensionInUrlStillUsesContentType() {
        // Gutenberg 的 .epub.noimages 这种地址：后缀不认识，但服务端说这是 zip
        assertEquals("example.com.epub", ImportHelper.pickName("https://example.com/1342.epub.noimages",
                "application/epub+zip"));
    }

    @Test
    public void rejectsHtmlPagesAndUnknownTypes() {
        assertNull(ImportHelper.pickName("https://example.com/article", "text/html; charset=utf-8"));
        assertNull(ImportHelper.pickName("https://example.com/download", "application/octet-stream"));
        assertNull(ImportHelper.pickName("https://example.com/download", null));
    }
}
