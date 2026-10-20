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
}
