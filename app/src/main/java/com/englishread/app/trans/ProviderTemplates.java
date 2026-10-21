package com.englishread.app.trans;

import java.util.ArrayList;
import java.util.List;

/**
 * 常见厂商的 API 配置模板：一键填好 base_url 与 model，用户只需要贴自己的 Key。
 *
 * <p>为什么要有这个类：base_url 和模型名是**会过期**的东西
 * （写这份代码时 DeepSeek 的模型名已经从 {@code deepseek-chat} 换成 {@code deepseek-flash}），
 * 让用户自己去文档里翻、抄错一个字符就得到一个必然失败的配置。
 *
 * <p>约定：模板只填 base_url 与 model，**绝不含 Key**。
 * 填完用户仍需自己贴 Key，「没配 Key 就发不出去」这条防线不受模板影响。
 */
public final class ProviderTemplates {

    /** 一家厂商的一行配置。 */
    public static final class Template {
        public final String name;
        /** 一句话说明，给用户判断选哪个。 */
        public final String note;
        public final String baseUrl;
        public final String model;
        /** 该厂商的 Key 申请页面，方便用户去拿。 */
        public final String keyPage;

        Template(String name, String note, String baseUrl, String model, String keyPage) {
            this.name = name;
            this.note = note;
            this.baseUrl = baseUrl;
            this.model = model;
            this.keyPage = keyPage;
        }
    }

    /**
     * 模板清单。模型名都是写这份代码时官方文档里的正式名字；
     * 厂商改名是常态，所以界面上要允许用户改 model——别把模板当成硬约束。
     */
    private static final List<Template> ALL = build();

    private static List<Template> build() {
        List<Template> list = new ArrayList<>();
        // 国内直连、价格低，适合整章翻译这种量大又不需要多聪明的活
        list.add(new Template("DeepSeek", "国内直连，便宜，适合段落翻译",
                "https://api.deepseek.com", "deepseek-flash",
                "https://platform.deepseek.com/api_keys"));
        list.add(new Template("阿里云百炼（千问）", "国内直连，需把地址里的 {WorkspaceId} 换成你的业务空间 ID",
                "https://dashscope.aliyuncs.com/compatible-mode", "qwen-plus",
                "https://bailian.console.aliyun.com/"));
        list.add(new Template("智谱 GLM", "国内直连，有免费额度",
                "https://open.bigmodel.cn/api/paas/v4", "glm-5.3",
                "https://bigmodel.cn/usercenter/proj-mgmt/apikeys"));
        list.add(new Template("Kimi（月之暗面）", "国内直连，长上下文",
                "https://api.moonshot.cn/v1", "kimi-k2.6",
                "https://platform.kimi.com/console/api-keys"));
        list.add(new Template("硅基流动 SiliconFlow", "国内直连，开源模型聚合，部分模型免费",
                "https://api.siliconflow.cn/v1", "Qwen/Qwen3-32B",
                "https://cloud.siliconflow.cn/account/ak"));
        list.add(new Template("OpenAI", "需要海外网络环境",
                "https://api.openai.com/v1", "gpt-4o-mini",
                "https://platform.openai.com/api-keys"));
        // 本机模型：段落原文完全不出网，适合对隐私敏感或有大量书要读的场景。
        // 只在 debug 包放行明文（见 src/debug/AndroidManifest.xml），release 用它发不出去。
        list.add(new Template("本机 Ollama（不出网）", "原文不离开本机；需先 ollama serve，且仅 debug 包可用",
                "http://127.0.0.1:11434/v1", "qwen2.5:7b",
                "https://ollama.com/download"));
        return list;
    }

    private ProviderTemplates() {
    }

    public static List<Template> all() {
        return ALL;
    }

    /** 按名字找，找不到返回 null。 */
    public static Template byName(String name) {
        for (Template t : ALL) {
            if (t.name.equals(name)) {
                return t;
            }
        }
        return null;
    }
}
