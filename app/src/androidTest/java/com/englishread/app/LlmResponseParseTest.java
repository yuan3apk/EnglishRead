package com.englishread.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.englishread.app.trans.LlmClient;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * 解析 chat/completions 的回复体。用真 org.json（平台自带），不 mock。
 *
 * <p>这一层守的是一个真实线上 bug：`readAll` 曾把响应体截到 300 字符，
 * 而真实厂商的回复有 id/model/usage 等字段，动辄 400+ 字节 ——
 * 于是完整 JSON 被切成半个，报「回复不是合法 JSON」。
 * 下面 {@link #realisticSizeResponseParses()} 就是按真实长度构造的回归用例。
 */
@RunWith(AndroidJUnit4.class)
public class LlmResponseParseTest {

    /** 按真实 DeepSeek 回复的形状与体量拼一个响应体（远超旧的 300 字符截断线）。 */
    private static String realisticResponse(String content) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"id\":\"a1b2c3d4-1234-5678-9abc-def012345678\",")
                .append("\"object\":\"chat.completion\",")
                .append("\"created\":1792471239,")
                .append("\"model\":\"deepseek-flash\",")
                .append("\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",")
                .append("\"content\":").append(quote(content)).append("},")
                .append("\"logprobs\":null,\"finish_reason\":\"stop\"}],")
                .append("\"usage\":{\"prompt_tokens\":86,\"completion_tokens\":24,")
                .append("\"total_tokens\":110,\"prompt_cache_hit_tokens\":0,")
                .append("\"prompt_cache_miss_tokens\":86},")
                .append("\"system_fingerprint\":\"fp_abc123def\"}");
        return sb.toString();
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n") + "\"";
    }

    @Test
    public void realisticSizeResponseParses() throws Exception {
        String content = "1|这是最好的时代。\n2|这是最坏的时代。";
        String raw = realisticResponse(content);

        // 先钉住前提：这个响应体必须比当年那个 300 字符截断线长，
        // 否则这条用例抓不到旧 bug（测试自身也要防退化）
        assertTrue("用例前提失效：响应体只有 " + raw.length() + " 字符，短于旧的截断线 300",
                raw.length() > 300);

        assertEquals(content, LlmClient.content(raw));
    }

    /**
     * **这条是真正的回归守卫**：走 `readAll`（读流）+ `content`（解析）的完整路径。
     *
     * <p>只调 {@code content(raw)} 是抓不到截断 bug 的——第一版用例就是那么写的，
     * 结果 bug 明明还在、测试却全绿。截断发生在读流那一层。
     */
    @Test
    public void longResponseSurvivesReadThenParse() throws Exception {
        String content = "1|这是最好的时代。\n2|这是最坏的时代。";
        String raw = realisticResponse(content);
        assertTrue("前提：响应要长于 300 字符", raw.length() > 300);

        String read = LlmClient.readAll(
                new java.io.ByteArrayInputStream(raw.getBytes("UTF-8")));

        assertEquals("读流时被截断了，完整响应必须原样读出", raw.length(), read.length());
        assertEquals(content, LlmClient.content(read));
    }

    @Test
    public void multiParagraphRealisticResponseParses() throws Exception {
        StringBuilder c = new StringBuilder();
        for (int i = 1; i <= 5; i++) {
            if (i > 1) {
                c.append('\n');
            }
            c.append(i).append("|第").append(i).append("段的译文，写得长一点更接近真实输出。");
        }
        String raw = realisticResponse(c.toString());
        assertEquals(c.toString(), LlmClient.content(raw));
    }

    @Test
    public void truncatedJsonReportsLengthSoTruncationIsObvious() {
        String raw = realisticResponse("1|译文");
        String cut = raw.substring(0, 300); // 模拟当年的截断
        try {
            LlmClient.content(cut);
            fail("半个 JSON 不该解析成功");
        } catch (IOException e) {
            // 错误信息里要带长度，否则「不是合法 JSON」根本没法判断是截断还是真 HTML
            assertTrue("错误信息应包含长度以便排查，实际：" + e.getMessage(),
                    e.getMessage().contains(String.valueOf(cut.length())));
        }
    }

    @Test
    public void reasoningOnlyReplyExplainsItself() {
        // 模型只吐了思考过程、content 为空：别报「内容为空」让人以为是网络问题
        String raw = "{\"choices\":[{\"message\":{\"role\":\"assistant\","
                + "\"content\":null,\"reasoning_content\":\"让我想想……\"}}]}";
        try {
            LlmClient.content(raw);
            fail("只有思考过程时不该当成译文");
        } catch (IOException e) {
            assertTrue("应提示是思考模式的问题，实际：" + e.getMessage(),
                    e.getMessage().contains("思考"));
        }
    }

    @Test
    public void emptyChoicesIsAnError() {
        try {
            LlmClient.content("{\"choices\":[]}");
            fail("没有 choices 应报错");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("choices"));
        }
    }
}
