package com.englishread.app.parse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 把一整段切成句子。**一句话就是一个段落**——段落是阅读器的最小单位
 * （一段一个 View、一个「段」按钮、一次翻译），切成句子后点词、翻译、朗读的粒度才对得上「一句话」。
 *
 * <p>断句规则：
 * <ul>
 *   <li>英文 {@code . ! ?} 后面必须跟空白（可以先跟一个右引号/右括号）才算句末，
 *       所以 3.14、www.example.com 不会被切开；</li>
 *   <li>中文 {@code 。！？} 自带断句语义，不要求后面有空白；</li>
 *   <li>缩写里的点不断句：Mr. / Mrs. / Dr. / St. / No. 之类查 {@link #ABBREV} 表；
 *       U.S. / e.g. / a.m. / J.K. 这类「字母 + 点 + 字母 + 点」的缩写链靠形态识别；
 *       J. K. Rowling 这种单字母名字缩写，往后看还有大写词才认。</li>
 * </ul>
 *
 * <p>已知取舍：句末正好落在缩写上（`...works at Acme Inc. She left.`）时会把两句并成一句；
 * 反过来宁可少断也不错断——断错了「Mr.」会单独成段，读起来更难受。
 */
public final class Sentences {

    /** 句号后面常跟着右引号/右括号，断句点要挪到它们之后。 */
    private static final String CLOSERS = "\"'”’»）)」』】";

    /** 常见缩写（小写、不含末尾的点）：这种点不是句末。 */
    private static final Set<String> ABBREV = new HashSet<>(Arrays.asList(
            // 称谓与头衔
            "mr", "mrs", "ms", "dr", "prof", "rev", "hon", "gen", "col", "capt", "maj",
            "lt", "sgt", "cmdr", "fr", "st", "jr", "sr",
            // 计量、编号、地址
            "nos", "fig", "figs", "vol", "vols", "pp", "p", "ave", "blvd", "rd", "mt", "ft",
            // 公司、杂项
            "inc", "ltd", "corp", "dept", "etc", "vs", "cf", "approx",
            // 月份缩写
            "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "sept", "oct", "nov", "dec"));

    /**
     * 本身也是常用词，只有首字母大写时才算缩写。
     * 例：`No. 5` 是编号，`He said no. Then...` 里的 no 是句末。
     */
    private static final Set<String> CAP_ONLY = new HashSet<>(Arrays.asList(
            "no", "ch", "pt", "sec", "ed", "al", "co", "est", "min", "max"));

    private Sentences() {
    }

    /** 切成句子；没有断句标点的整块仍然是一句。空串返回空表。 */
    public static List<String> split(String block) {
        List<String> out = new ArrayList<>();
        if (block == null) {
            return out;
        }
        int n = block.length();
        int start = 0;
        for (int i = 0; i < n; i++) {
            char c = block.charAt(i);
            if (!isTerminator(c)) {
                continue;
            }
            // 断句点要含上紧随其后的右引号/右括号
            int end = i + 1;
            while (end < n && CLOSERS.indexOf(block.charAt(end)) >= 0) {
                end++;
            }
            boolean chinese = c == '。' || c == '！' || c == '？';
            if (!chinese) {
                if (end < n && !Character.isWhitespace(block.charAt(end))) {
                    continue;   // 3.14 / www.example.com / U.S. 后面没空白，不是句末
                }
                if (isAbbreviation(block, i)) {
                    continue;   // Mr. / Mrs. / Dr. / e.g. 之类
                }
            }
            add(out, block.substring(start, end));
            start = end;
            i = end - 1;
        }
        add(out, block.substring(start));
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

    private static void add(List<String> out, String piece) {
        String s = piece.trim();
        // 只剩标点/空白的碎片没有阅读价值，丢掉
        if (!s.isEmpty()) {
            out.add(s);
        }
    }

    private static boolean isTerminator(char c) {
        return c == '.' || c == '!' || c == '?' || c == '。' || c == '！' || c == '？';
    }

    private static boolean isLetter(char c) {
        return Character.isLetter(c);
    }

    /**
     * {@code dot} 处的点是否属于缩写（不属于句末）。
     *
     * @param s   原串
     * @param dot 点的下标
     */
    private static boolean isAbbreviation(String s, int dot) {
        int k = dot - 1;
        if (k < 0 || !isLetter(s.charAt(k))) {
            return false;
        }
        // 「字母 + 点 + 字母 + 点」：U.S. / e.g. / i.e. / a.m. / J.K. —— 缩写链，末点不是句末
        if (k >= 2 && s.charAt(k - 1) == '.' && isLetter(s.charAt(k - 2))) {
            return true;
        }
        int b = k;
        while (b >= 0 && isLetter(s.charAt(b))) {
            b--;
        }
        String word = s.substring(b + 1, dot).toLowerCase(Locale.ROOT);
        if (ABBREV.contains(word)) {
            return true;
        }
        if (CAP_ONLY.contains(word)) {
            return Character.isUpperCase(s.charAt(b + 1));
        }
        if (word.length() == 1) {
            // 单字母名字缩写（J. K. Rowling）：后面还得有大写词才认，否则 A. B. 这种仍按句子切。
            // I. 不当缩写——"It was I." 是句末。
            return Character.isUpperCase(s.charAt(k)) && !"i".equals(word) && hasNameAfter(s, dot + 1);
        }
        return false;
    }

    /**
     * 从 {@code from} 开始找下一个实词，判断它像不像人名/专名（大写开头的多字母词）。
     * 中间遇到单字母缩写（J. K.）就继续往后看。
     */
    private static boolean hasNameAfter(String s, int from) {
        int i = from;
        int n = s.length();
        while (true) {
            while (i < n && (Character.isWhitespace(s.charAt(i))
                    || CLOSERS.indexOf(s.charAt(i)) >= 0 || s.charAt(i) == '(')) {
                i++;
            }
            if (i >= n || !Character.isUpperCase(s.charAt(i))) {
                return false;
            }
            int j = i;
            while (j < n && isLetter(s.charAt(j))) {
                j++;
            }
            if (j - i >= 2) {
                return true;
            }
            if (j < n && s.charAt(j) == '.') {
                i = j + 1;   // 又一个单字母缩写，接着看
                continue;
            }
            return false;
        }
    }
}
