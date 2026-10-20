package com.englishread.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.englishread.app.parse.BookParser;
import com.englishread.app.parse.ParsedBook;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * 拿 samples/ 里的真样本走一遍解析，验证章节/段落数与样本一致。
 * 样本通过 androidTest assets 注入，见 build.gradle.kts 的 sourceSets。
 */
@RunWith(AndroidJUnit4.class)
public class ImportParsersTest {

    private Context ctx() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    /** 把 androidTest assets 里的样本拷到 filesDir，模拟导入后的落盘位置。 */
    private File materialize(String assetName) throws Exception {
        Context c = InstrumentationRegistry.getInstrumentation().getContext();
        File out = new File(ctx().getCacheDir(), assetName);
        try (InputStream in = c.getAssets().open(assetName);
             FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                fos.write(buf, 0, n);
            }
        }
        return out;
    }

    @Test
    public void parsesTxtSample() throws Exception {
        File f = materialize("sample.txt");
        ParsedBook pb = BookParser.parse(f, "Sample", ctx().getCacheDir());

        assertEquals("txt", pb.format);
        assertEquals(2, pb.chapters.size());
        assertEquals("Chapter 1", pb.chapters.get(0).title);
        assertEquals(2, pb.chapters.get(0).paragraphs.size());
        // 标题行归章节名，不重复进正文
        assertEquals("It was the best of times.", pb.chapters.get(0).paragraphs.get(0));
        assertEquals(3, pb.paragraphCount());
    }

    @Test
    public void parsesEpub2SampleInSpineOrder() throws Exception {
        File f = materialize("sample_epub2.epub");
        ParsedBook pb = BookParser.parse(f, "Fallback", ctx().getCacheDir());

        assertEquals("epub", pb.format);
        assertEquals("Sample EPUB2", pb.title);
        assertEquals("Ada Lovelace", pb.author);
        assertEquals(2, pb.chapters.size());
        assertEquals("Chapter One", pb.chapters.get(0).title);
        assertEquals("Chapter Two", pb.chapters.get(1).title);
        // 每章 h1 之外各两段，标题不重复进正文
        assertEquals(2, pb.chapters.get(0).paragraphs.size());
        assertEquals(4, pb.paragraphCount());
    }

    @Test
    public void parsesEpub3SampleWithNestedPathsAndBrokenChapter() throws Exception {
        File f = materialize("sample_epub3.epub");
        ParsedBook pb = BookParser.parse(f, "Fallback", ctx().getCacheDir());

        assertEquals("Sample EPUB3", pb.title);
        // 第三章的 XHTML 不合法（命名实体 + 裸 br），必须靠清洗救回来而不是整本失败
        assertEquals(3, pb.chapters.size());
        assertNotNull(pb.chapters.get(2).paragraphs);
        assertTrue(pb.chapters.get(2).paragraphs.size() > 0);
        assertTrue(pb.chapters.get(2).paragraphs.get(0).contains("Bad"));
    }
}
