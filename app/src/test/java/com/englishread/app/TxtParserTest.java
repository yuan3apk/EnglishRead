package com.englishread.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.englishread.app.parse.ParsedBook;
import com.englishread.app.parse.TxtParser;

import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class TxtParserTest {

    @Test
    public void splitsChaptersOnChapterHeadings() {
        String text = "Chapter 1\n\nIt was the best of times.\n\nIt was the worst of times.\n\n"
                + "Chapter 2\n\nCall me Ishmael.\n";
        ParsedBook pb = TxtParser.parse(text, "Fallback");

        assertEquals("Fallback", pb.title);
        assertEquals(2, pb.chapters.size());
        assertEquals("Chapter 1", pb.chapters.get(0).title);
        assertEquals(2, pb.chapters.get(0).paragraphs.size());
        assertEquals("It was the best of times.", pb.chapters.get(0).paragraphs.get(0));
        assertEquals("Call me Ishmael.", pb.chapters.get(1).paragraphs.get(0));
    }

    @Test
    public void noHeadingMeansSingleChapterAndKeepsLineBreaksAsSpaces() {
        // 单换行是排版换行，应合并成空格而不是切成两段
        ParsedBook pb = TxtParser.parse("A line here\ncontinues on.\n\nSecond para.\n", "One");
        assertEquals(1, pb.chapters.size());
        assertEquals(2, pb.chapters.get(0).paragraphs.size());
        assertEquals("A line here continues on.", pb.chapters.get(0).paragraphs.get(0));
    }

    @Test
    public void chineseChapterHeadingIsRecognized() {
        ParsedBook pb = TxtParser.parse("第一章 起点\n\n正文一段。\n\n第二章 终点\n\n正文二段。\n", "书");
        assertEquals(2, pb.chapters.size());
        assertTrue(pb.chapters.get(0).title.contains("第一章"));
    }

    @Test
    public void decodesGbkTextThatIsNotValidUtf8() {
        // 「中文」的 GB18030 字节不是合法 UTF-8，必须走兜底而不是变成问号
        byte[] gbk = "中文测试\n\n第二段。".getBytes(Charset.forName("GB18030"));
        String decoded = TxtParser.decode(gbk);
        assertEquals("中文测试\n\n第二段。", decoded);
    }

    @Test
    public void decodesUtf8WithBomAndStripsIt() {
        byte[] body = "Hello world.".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);

        ParsedBook pb = TxtParser.parse(withBom, "Bom");
        assertEquals("Hello world.", pb.chapters.get(0).paragraphs.get(0));
    }
}
