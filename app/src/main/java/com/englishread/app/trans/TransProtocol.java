package com.englishread.app.trans;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 段落翻译的「线协议」：怎么把 N 段拼成一次请求、怎么把回复切回 N 段。
 *
 * <p>纯 Java、无 Android 依赖——这是整条翻译链路里唯一会在细微处悄悄坏掉的地方
 * （模型偶尔漏一段、多一行寒暄、把编号写成全角），所以全部放在这里好单测。
 *
 * <p>约定（HANDOFF 2.2）：请求体是「编号|原文」逐行，回复体是「编号|译文」逐行。
 * 用固定分隔符而不是 JSON 数组，既省 token 也省一次解析失败的机会。
 */
public final class TransProtocol {

    /** 一次请求最多几段。太少往返多，太多模型容易中途漏段。 */
    public static final int MAX_BATCH = 5;

    public static final String SYSTEM_PROMPT =
            "你是英译中翻译器。把下面编号的段落翻译成简体中文。\n"
                    + "要求：保留原段落编号；只输出「编号|译文」行；"
                    + "术语与专有名词前后一致；不添加解释，不扩写，不合并段落。";

    /** 回复里一行「编号|译文」；编号后面允许空格与全角竖线（模型偶尔会写）。 */
    private static final Pattern LINE = Pattern.compile("^\\s*(\\d+)\\s*[|｜]\\s*(.*)$");

    private TransProtocol() {
    }

    /** 拼请求体：原文里有换行的段落先压成一行，否则会打乱「一行一段」的约定。 */
    public static String buildUser(String... paragraphs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paragraphs.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(i + 1).append('|').append(oneLine(paragraphs[i]));
        }
        return sb.toString();
    }

    /**
     * 把回复切回与请求等长的数组，缺的槽位是 null（调用方按「未译」处理，不要用空串冒充译文）。
     *
     * <p>宽松解析的三处：编号越界就丢掉；译文中间的空行/续行接到上一段后面；
     * 整段没有编号的寒暄直接忽略——模型多话不能换成一整批失败。
     */
    public static String[] parseReply(String reply, int expected) {
        String[] out = new String[Math.max(0, expected)];
        if (reply == null) {
            return out;
        }
        int lastIndex = -1;
        for (String raw : reply.split("\\r?\\n")) {
            Matcher m = LINE.matcher(raw);
            if (m.matches()) {
                int n;
                try {
                    n = Integer.parseInt(m.group(1));
                } catch (NumberFormatException e) {
                    continue;
                }
                int slot = n - 1;
                if (slot < 0 || slot >= out.length) {
                    continue;
                }
                out[slot] = m.group(2).trim();
                lastIndex = slot;
            } else if (lastIndex >= 0 && !raw.trim().isEmpty() && out[lastIndex] != null) {
                // 续行：译文里本来就有换行，接回上一段
                out[lastIndex] = out[lastIndex] + "\n" + raw.trim();
            }
        }
        for (int i = 0; i < out.length; i++) {
            if (out[i] != null && out[i].isEmpty()) {
                out[i] = null; // 空的「1|」当没译，别把空框显示给用户
            }
        }
        return out;
    }

    /** 已译出几段，用来判断这次请求是否值得回写。 */
    public static int countTranslated(String[] parsed) {
        int n = 0;
        for (String s : parsed) {
            if (s != null) {
                n++;
            }
        }
        return n;
    }

    /** 把一批里没译出来的段落编号（1 起）挑出来，方便重试时只重发这些。 */
    public static List<Integer> missing(String[] parsed) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < parsed.length; i++) {
            if (parsed[i] == null) {
                out.add(i + 1);
            }
        }
        return out;
    }

    /**
     * 用户填的 base_url → 真正的 chat/completions 地址。四种写法都认：
     * 整条完整地址、以 {@code /v1} 结尾、以别的版本段结尾（智谱是 {@code /api/paas/v4}）、只写域名。
     *
     * <p>版本段这条别删：只判 {@code /v1} 的话，智谱的 {@code /api/paas/v4}
     * 会被拼成 {@code /v4/v1/chat/completions}，必然 404。
     */
    public static String endpoint(String baseUrl) {
        String s = baseUrl == null ? "" : baseUrl.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.endsWith("/chat/completions")) {
            return s;
        }
        // 已经带了版本段（/v1、/v4……）就只补端点，别再叠一层 /v1
        if (VERSION_SEGMENT.matcher(s).find()) {
            return s + "/chat/completions";
        }
        return s + "/v1/chat/completions";
    }

    private static final Pattern VERSION_SEGMENT = Pattern.compile("/v\\d+$");

    /** 只接受 https：明文传输段落原文不可接受，见 SettingsActivity 里的提示。 */
    public static boolean isHttps(String url) {
        return url != null && url.trim().toLowerCase(java.util.Locale.US).startsWith("https://");
    }

    /**
     * 这个地址要不要下发 {@code thinking:disabled}。
     *
     * <p>只有 DeepSeek 认这个字段（新模型默认开思考模式，翻译时白烧 token）。
     * 别的厂商不认，严格校验参数的服务商还可能因此 400，所以只对它下发。
     */
    public static boolean disablesThinking(String endpoint) {
        return endpoint != null
                && endpoint.toLowerCase(java.util.Locale.US).contains("api.deepseek.com");
    }

    /**
     * 明文地址是否被允许。只在 debug 构建里放行本机回环，
     * 好让 {@code tools/fake_llm.py} 能跑端到端验收——release 包永远是 false。
     */
    public static boolean allowsCleartext(String url, boolean debugBuild) {
        if (isHttps(url)) {
            return true;
        }
        if (!debugBuild || url == null) {
            return false;
        }
        String s = url.trim().toLowerCase(java.util.Locale.US);
        return s.startsWith("http://10.0.2.2")      // AVD 里的宿主机
                || s.startsWith("http://127.0.0.1")
                || s.startsWith("http://localhost");
    }

    public static String oneLine(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
