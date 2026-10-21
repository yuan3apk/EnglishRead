package com.englishread.app;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.englishread.app.reader.ReaderPrefs;

/**
 * 排版档位表的约束。这些是「改档位」依赖的前提，坏了不会崩，只会悄悄错位——
 * 所以用最便宜的方式钉住。
 */
public class ReaderPrefsLadderTest {

    @Test
    public void paraSpacingNeverZero() {
        // 段间那条缝同时是长段落「呼出菜单」的唯一入口（见 ParagraphGap / HANDOFF M2 补丁）。
        // 归零 = 菜单再也叫不出来，且没有任何测试会失败——所以在这里钉死。
        for (int dp : ReaderPrefs.PARA_SPACINGS) {
            assertTrue("段距必须 > 0，否则段间空白消失、菜单叫不出来", dp > 0);
        }
    }

    @Test
    public void laddersAreAscending() {
        // bump(+1)/bump(-1) 用档位下标走，表不递增就会出现「点了 + 反而变小」。
        assertAscending(ReaderPrefs.TEXT_SIZES);
        assertAscending(ReaderPrefs.PARA_SPACINGS);
        assertAscending(ReaderPrefs.INDENTS);
        assertAscending(ReaderPrefs.LINE_SPACINGS);
        assertAscending(ReaderPrefs.LETTER_SPACINGS);
        assertAscending(ReaderPrefs.SPEECH_RATES);
    }

    @Test
    public void speechRatesStayInApiRange() {
        // TextToSpeech.setSpeechRate 的可用区间是 0.5–2.0 附近，超出不会报错但会怪腔怪调。
        for (float rate : ReaderPrefs.SPEECH_RATES) {
            assertTrue("语速必须在 0.5–2.0 之间: " + rate, rate >= 0.5f && rate <= 2.0f);
        }
    }

    @Test
    public void defaultsAreInTheirLadder() {
        // 默认值必须能在表里找到，否则点一次 +/- 会跳到表头（nearestIndex 落到 0）
        assertContains(ReaderPrefs.TEXT_SIZES, 18);
        assertContains(ReaderPrefs.PARA_SPACINGS, 16);
        assertContains(ReaderPrefs.INDENTS, 0);
        assertContains(ReaderPrefs.LETTER_SPACINGS, 0f);
        assertContains(ReaderPrefs.LINE_SPACINGS, 1.5f);
        assertContains(ReaderPrefs.SPEECH_RATES, 1.0f);
    }

    private static void assertAscending(int[] values) {
        for (int i = 1; i < values.length; i++) {
            assertTrue("档位必须递增: " + values[i - 1] + " < " + values[i],
                    values[i - 1] < values[i]);
        }
    }

    private static void assertAscending(float[] values) {
        for (int i = 1; i < values.length; i++) {
            assertTrue("档位必须递增: " + values[i - 1] + " < " + values[i],
                    values[i - 1] < values[i]);
        }
    }

    private static void assertContains(int[] values, int target) {
        for (int v : values) {
            if (v == target) {
                return;
            }
        }
        throw new AssertionError("默认值 " + target + " 不在档位表里");
    }

    private static void assertContains(float[] values, float target) {
        for (float v : values) {
            if (v == target) {
                return;
            }
        }
        throw new AssertionError("默认值 " + target + " 不在档位表里");
    }
}
