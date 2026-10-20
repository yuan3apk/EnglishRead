package com.englishread.app;

import static org.junit.Assert.assertEquals;

import com.englishread.app.parse.Tokenizer;

import org.junit.Test;

public class TokenizerTest {

    @Test
    public void splitsWordsAndKeepsContractionsWhole() {
        // 数字不会被当成词：数字词条不进词典，查了也没用
        assertEquals("[It, was, the, best, don't, well-known]",
                Tokenizer.words("It was the best don't well-known 1813.").toString());
    }

    @Test
    public void normalizesCurlyApostrophe() {
        assertEquals("don't", Tokenizer.words("don\u2019t").get(0));
    }

    @Test
    public void stripsPossessiveAndLowercases() {
        assertEquals("elizabeth", Tokenizer.lookupKey("Elizabeth's"));
        // 只做归一化，不做词形还原：ran→run 由 assets 词典的 form 反查表负责
        assertEquals("ran", Tokenizer.lookupKey("Ran"));
    }

    @Test
    public void tokenizeOffsetsMatchWords() {
        String text = "It was the best of times.";
        int n = Tokenizer.count(text);
        assertEquals(6, n);

        int[] starts = new int[n];
        int[] ends = new int[n];
        String[] words = new String[n];
        Tokenizer.tokenize(text, starts, ends, words);

        // 偏移必须能切回原词，否则点词高亮和查词会错位
        for (int i = 0; i < n; i++) {
            assertEquals(text.substring(starts[i], ends[i]), words[i]);
        }
        assertEquals("It", words[0]);
        assertEquals(0, starts[0]);
        assertEquals("times", words[5]);
    }

    @Test
    public void tokenizeCountsCurlyApostropheAsOneWord() {
        String text = "Don\u2019t stop.";
        assertEquals(2, Tokenizer.count(text));

        int[] starts = new int[2];
        int[] ends = new int[2];
        String[] words = new String[2];
        Tokenizer.tokenize(text, starts, ends, words);
        // 偏移按原文算（含弯撇号），词本身归一化成直撇号
        assertEquals("Don't", words[0]);
        assertEquals(0, starts[0]);
        assertEquals(5, ends[0]);
    }
}
