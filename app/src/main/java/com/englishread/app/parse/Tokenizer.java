package com.englishread.app.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 英文分词与查词归一化。
 *
 * <p>「取词」产出字符区间供阅读器命中测试用，「查词键」供词典查询用。
 * 句子切分放到 M4 的 SentenceIndex 里做，因为它要连带产出偏移表。
 */
public final class Tokenizer {

    /**
     * 词：字母开头，允许词内的撇号与连字符（don't / well-known / mother-in-law）。
     * 结尾不允许撇号，避免把 Elizabeth' 这种残尾吞进来。
     */
    private static final Pattern WORD =
            Pattern.compile("[A-Za-z]+(?:['\u2019\\-][A-Za-z]+)*");

    private Tokenizer() {
    }

    /** 按出现顺序返回所有词，已把弯撇号统一成直撇号。 */
    public static List<String> words(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        Matcher m = WORD.matcher(text);
        while (m.find()) {
            out.add(m.group().replace('\u2019', '\''));
        }
        return out;
    }

    /**
     * 把一段的命中边界一次性算好，填进三个等长数组。
     * 阅读器每绑一次段落算一次，点击时就只剩二分查找。
     */
    public static void tokenize(String text, int[] starts, int[] ends, String[] texts) {
        if (text == null) {
            return;
        }
        Matcher m = WORD.matcher(text);
        int i = 0;
        while (m.find() && i < starts.length) {
            starts[i] = m.start();
            ends[i] = m.end();
            texts[i] = m.group().replace('\u2019', '\'');
            i++;
        }
    }

    /** 先数一遍词，好按精确长度分配数组。 */
    public static int count(String text) {
        if (text == null) {
            return 0;
        }
        Matcher m = WORD.matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    /**
     * 查词键：小写、去所有格撇号、去首尾标点。
     * 只处理确定无歧义的情况，真正的变形还原交给 assets 词典的 form 反查表。
     */
    public static String lookupKey(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replace('\u2019', '\'').trim();
        // Elizabeth's → elizabeth，don't 保留原样（它是独立词条）
        if (s.endsWith("'s") || s.endsWith("'S")) {
            s = s.substring(0, s.length() - 2);
        }
        return s.toLowerCase(Locale.US);
    }
}
