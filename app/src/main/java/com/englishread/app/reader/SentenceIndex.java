package com.englishread.app.reader;

import java.util.ArrayList;
import java.util.List;

/**
 * 把一段正文切成「一次朗读」的片段，返回**原文区间**而不是子串——
 * 逐词高亮 {@code onRangeStart} 给的偏移是相对片段内部的，加回区间起点才是段落偏移。
 *
 * <p>正文本来就是一句话一段（{@code parse/Sentences} 在导入时切过），所以绝大多数段落
 * 只有一段。这里切分只为两件事：老书里没切过句的整段（一句话一段是 M2 补丁 5 才有的，
 * 已在库里的书仍是整段），以及个别超长句子——TTS 引擎单次输入约 4000 字符上限，
 * 太长会直接报错、一声不出。
 *
 * <p>纯 Java，没有 Android 依赖：偏移算错不会崩，只会让高亮落错位置，所以用单测钉住。
 */
public final class SentenceIndex {

    /** 单次朗读的字符上限。远小于引擎上限；超长句按空格再切。 */
    public static final int MAX_CHARS = 220;

    private SentenceIndex() {
    }

    /**
     * @return 按出现顺序排列的 [start, end) 原文区间，已去掉首尾空白；没有可读内容返回空表。
     */
    public static List<int[]> chunks(String text) {
        List<int[]> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        for (int[] s : sentences(text)) {
            if (s[1] - s[0] <= MAX_CHARS) {
                out.add(s);
            } else {
                splitLong(text, s[0], s[1], out);
            }
        }
        return out;
    }

    /**
     * 按句末标点切。英文标点后面必须跟空白才算断句（避开 3.14、www.example.com），
     * 中文 。！？ 自带断句语义，不要求空白——与 {@code parse/Sentences} 同一套规则，
     * 那边切的是「段落」，这里切的是「朗读片段」，两处独立，互不影响。
     */
    private static List<int[]> sentences(String s) {
        List<int[]> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean end;
            if (c == '.' || c == '!' || c == '?') {
                end = i + 1 >= s.length() || Character.isWhitespace(s.charAt(i + 1));
            } else {
                end = c == '。' || c == '！' || c == '？';
            }
            if (end) {
                addTrimmed(out, s, start, i + 1);
                start = i + 1;
            }
        }
        addTrimmed(out, s, start, s.length());
        return out;
    }

    /** 超长句按空格切；实在没有空格就硬切——宁可断在半个词上，也不能让引擎吞掉整句。 */
    private static void splitLong(String s, int start, int end, List<int[]> out) {
        int a = start;
        while (end - a > MAX_CHARS) {
            int limit = a + MAX_CHARS;
            int cut = s.lastIndexOf(' ', limit - 1);
            if (cut <= a) {
                cut = limit;
            }
            out.add(new int[]{a, cut});
            a = cut;
            while (a < end && Character.isWhitespace(s.charAt(a))) {
                a++;
            }
        }
        if (end > a) {
            out.add(new int[]{a, end});
        }
    }

    private static void addTrimmed(List<int[]> out, String s, int from, int to) {
        int a = from;
        int b = Math.min(to, s.length());
        while (a < b && Character.isWhitespace(s.charAt(a))) {
            a++;
        }
        while (b > a && Character.isWhitespace(s.charAt(b - 1))) {
            b--;
        }
        if (b > a) {
            out.add(new int[]{a, b});
        }
    }
}
