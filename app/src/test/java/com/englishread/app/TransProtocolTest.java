package com.englishread.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.englishread.app.trans.TransProtocol;

import org.junit.Test;

/**
 * 线协议：拼请求、切回复、认地址。
 *
 * <p>这一层的失败都是「静默的」——模型漏一段、多写一行寒暄、把编号写成全角，
 * 界面只会少一段译文或整批失败，而钱已经花掉了。所以宽松解析的每条规则都要有断言。
 */
public class TransProtocolTest {

    // ---- 请求体 ----

    @Test
    public void requestNumbersParagraphsFromOne() {
        String body = TransProtocol.buildUser("It was the best of times.", "It was the worst.");
        assertEquals("1|It was the best of times.\n2|It was the worst.", body);
    }

    @Test
    public void requestFlattensNewlinesInsideParagraph() {
        // 段内换行必须压掉：协议是「一行一段」，漏进来会拆成两行冒充两段
        String body = TransProtocol.buildUser("line one\nline two");
        assertEquals("1|line one line two", body);
    }

    // ---- 回复解析 ----

    @Test
    public void parsesPlainReply() {
        String[] t = TransProtocol.parseReply("1|这是最好的时代。\n2|这是最坏的时代。", 2);
        assertEquals("这是最好的时代。", t[0]);
        assertEquals("这是最坏的时代。", t[1]);
    }

    @Test
    public void parsesOutOfOrderAndFullWidthPipe() {
        String[] t = TransProtocol.parseReply("2｜第二段\n1|第一段", 2);
        assertEquals("第一段", t[0]);
        assertEquals("第二段", t[1]);
    }

    @Test
    public void ignoresChatterAndOutOfRangeNumbers() {
        // 模型爱加一句「好的，以下是翻译：」，这不该换成一整批失败
        String reply = "好的，以下是翻译：\n1|第一段\n9|越界的编号";
        String[] t = TransProtocol.parseReply(reply, 2);
        assertEquals("第一段", t[0]);
        assertNull("越界编号不能挤进别的槽位", t[1]);
    }

    @Test
    public void missingParagraphStaysNull() {
        // 漏段是最常见的失败：必须留 null，让上层知道「这段还得重发」，
        // 而不是用空串冒充译文（那样界面上就是个空框，再也不会重试）
        String[] t = TransProtocol.parseReply("1|只有第一段", 3);
        assertEquals("只有第一段", t[0]);
        assertNull(t[1]);
        assertNull(t[2]);
        assertEquals(1, TransProtocol.countTranslated(t));
        assertEquals(2, TransProtocol.missing(t).size());
        assertEquals(Integer.valueOf(2), TransProtocol.missing(t).get(0));
    }

    @Test
    public void wrapsContinuationLineIntoPreviousParagraph() {
        String[] t = TransProtocol.parseReply("1|第一行\n第二行\n2|第二段", 2);
        assertEquals("第一行\n第二行", t[0]);
        assertEquals("第二段", t[1]);
    }

    @Test
    public void emptyTranslationCountsAsMissing() {
        // 「1|」是模型偶尔的空响应，当成已译会永久缓存一个空译文
        String[] t = TransProtocol.parseReply("1|\n2|有内容", 2);
        assertNull(t[0]);
        assertEquals("有内容", t[1]);
    }

    @Test
    public void nullReplyDoesNotThrow() {
        String[] t = TransProtocol.parseReply(null, 2);
        assertEquals(2, t.length);
        assertNull(t[0]);
    }

    // ---- 地址 ----

    @Test
    public void endpointAcceptsThreeShapes() {
        assertEquals("https://a.com/v1/chat/completions",
                TransProtocol.endpoint("https://a.com"));
        assertEquals("https://a.com/v1/chat/completions",
                TransProtocol.endpoint("https://a.com/"));
        // 已经写到 /v1 的别再叠一层
        assertEquals("https://a.com/v1/chat/completions",
                TransProtocol.endpoint("https://a.com/v1"));
        // 整条完整地址原样用
        assertEquals("https://a.com/v1/chat/completions",
                TransProtocol.endpoint("https://a.com/v1/chat/completions"));
    }

    /**
     * 智谱的 base_url 以 /v4 结尾、百炼以 /compatible-mode 结尾。
     * 只认 /v1 的实现会把智谱拼成 /v4/v1/chat/completions —— 必然 404。
     */
    @Test
    public void endpointHandlesNonV1VersionSegment() {
        assertEquals("https://open.bigmodel.cn/api/paas/v4/chat/completions",
                TransProtocol.endpoint("https://open.bigmodel.cn/api/paas/v4"));
        assertEquals("https://api.siliconflow.cn/v1/chat/completions",
                TransProtocol.endpoint("https://api.siliconflow.cn/v1"));
        // 没有版本段的照样补 /v1
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                TransProtocol.endpoint("https://dashscope.aliyuncs.com/compatible-mode"));
    }

    /** 每个内置模板拼出来的地址都必须是完整 endpoint，且不该出现两层版本段。 */
    @Test
    public void allProviderTemplatesProduceSaneEndpoint() {
        for (com.englishread.app.trans.ProviderTemplates.Template t
                : com.englishread.app.trans.ProviderTemplates.all()) {
            String ep = TransProtocol.endpoint(t.baseUrl);
            assertTrue(t.name + " 的地址没拼到端点: " + ep,
                    ep.endsWith("/chat/completions"));
            assertFalse(t.name + " 出现了两层版本段: " + ep,
                    ep.matches(".*/v\\d+/v\\d+/.*"));
            // 模板绝不能自带 Key
            assertFalse(t.name + " 的地址不该含 key", ep.toLowerCase().contains("key="));
        }
    }

    /** 模板里的模型名不能为空——空模型名会在第一次调用时 400。 */
    @Test
    public void allProviderTemplatesHaveModel() {
        for (com.englishread.app.trans.ProviderTemplates.Template t
                : com.englishread.app.trans.ProviderTemplates.all()) {
            assertTrue(t.name + " 缺模型名", t.model != null && !t.model.trim().isEmpty());
            assertTrue(t.name + " 缺地址", t.baseUrl != null && !t.baseUrl.trim().isEmpty());
        }
    }

    @Test
    public void onlyHttpsIsAllowed() {
        // 段落原文不能明文出网：配置成 http 要明确拒绝，不能静默发出去
        assertTrue(TransProtocol.isHttps("https://api.deepseek.com"));
        assertTrue(TransProtocol.isHttps("  HTTPS://a.com  "));
        assertFalse(TransProtocol.isHttps("http://a.com"));
        assertFalse(TransProtocol.isHttps("a.com"));
        assertFalse(TransProtocol.isHttps(null));
    }

    /**
     * thinking:disabled 只下发给 DeepSeek。
     *
     * <p>DeepSeek 新模型默认开思考模式，翻译时先吐一大段 reasoning_content，又慢又贵。
     * 但别的厂商不认这个字段，严格校验参数的服务商可能因此 400，所以不能无脑发。
     */
    @Test
    public void thinkingDisabledOnlySentToDeepSeek() {
        assertTrue(TransProtocol.disablesThinking(
                TransProtocol.endpoint("https://api.deepseek.com")));
        // 别的厂商一律不发
        assertFalse(TransProtocol.disablesThinking(
                TransProtocol.endpoint("https://open.bigmodel.cn/api/paas/v4")));
        assertFalse(TransProtocol.disablesThinking(
                TransProtocol.endpoint("https://api.moonshot.cn/v1")));
        assertFalse(TransProtocol.disablesThinking(
                TransProtocol.endpoint("https://api.siliconflow.cn/v1")));
        // 本机假服务当然也不发
        assertFalse(TransProtocol.disablesThinking(
                TransProtocol.endpoint("http://10.0.2.2:8787")));
    }
}
