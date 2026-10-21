package com.englishread.app.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 把一整段切成句子。**一句话就是一个段落**——段落是阅读器的最小单位
 * （一段一个 View、一个「段」按钮、一次翻译），切成句子后点词、翻译、朗读的粒度才对得上「一句话」。
 *
 * <p>切分规则：句号/问号/感叹号之后紧跟空白才算断句（原来的换行已经在切段时并成空格了）。
 * 要求后面有空白是为了避开 3.14、U.S. 这种不带空格的点。中文的 。！？ 后面通常没有空格，另算一条。
 *
 * <p>已知取舍：`Mr. Smith`、`Dr. Who` 这类「缩写 + 空格 + 大写」会被切断——要修得挂一份缩写表，
 * 对一个阅读器来说不值当。真要修，在 {@link #BOUNDARY} 前面加负向断言即可，调用方不用动。
 */
public final class Sentences {

    /** 英文标点后必须跟空白；中文标点自带断句语义，不要求空白。 */
    private static final Pattern BOUNDARY = Pattern.compile("(?<=[.!?])\\s+|(?<=[。！？])");

    private Sentences() {
    }

    /** 切成句子；没有断句标点的整块仍然是一句。空串返回空表。 */
    public static List<String> split(String block) {
        List<String> out = new ArrayList<>();
        if (block == null) {
            return out;
        }
        for (String part : BOUNDARY.split(block)) {
            String s = part.trim();
            // 只剩标点/空白的碎片没有阅读价值，丢掉
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    /** 就地把整本书的每段拆成句子段。 */
    public static void splitBook(ParsedBook book) {
        if (book == null) {
            return;
        }
        for (ParsedBook.Chapter ch : book.chapters) {
            List<String> sentences = new ArrayList<>(ch.paragraphs.size());
            for (String para : ch.paragraphs) {
                sentences.addAll(split(para));
            }
            ch.paragraphs.clear();
            ch.paragraphs.addAll(sentences);
        }
    }
}
