package com.englishread.app.trans;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 打 OpenAI 兼容的 {@code /v1/chat/completions}。
 *
 * <p>用 {@link HttpURLConnection} + {@code org.json}，不引 okhttp/gson：
 * 平台自带，一次同步 POST 用不着连接池和拦截器（HANDOFF 第 1 节）。
 *
 * <p>重试策略：只对「重发可能成功」的错（超时、断网、5xx、429）重试 1 次；
 * 401/403/400 是配置或参数错，重试只是浪费一次往返和一次配额。
 */
public class LlmClient {

    private static final String TAG = "TransQueue";

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 20_000;
    /** 一次翻译结果就几行字，给 2K 够用；太小会在长段落上被截断。 */
    private static final int MAX_TOKENS = 2048;

    /** 取消信号。切章节/退出阅读器时置位，别让飞行中的请求把译文写回刚关掉的书。 */
    public interface Cancel {
        boolean isCancelled();
    }

    public static final Cancel NEVER = () -> false;

    public static class Result {
        public final boolean ok;
        /** 与请求等长；未译出的槽位是 null。 */
        public final String[] translations;
        public final String error;
        public final boolean retryable;

        private Result(boolean ok, String[] translations, String error, boolean retryable) {
            this.ok = ok;
            this.translations = translations;
            this.error = error;
            this.retryable = retryable;
        }

        static Result ok(String[] t) {
            return new Result(true, t, null, false);
        }

        static Result fail(String error, boolean retryable) {
            return new Result(false, null, error, retryable);
        }
    }

    private final ApiConfig config;

    public LlmClient(ApiConfig config) {
        this.config = config;
    }

    /**
     * 翻一段或多段。{@code paragraphs} 顺序即协议里的编号顺序。
     * 返回的 {@link Result#translations} 与入参等长，缺的槽位为 null。
     */
    public Result translate(List<String> paragraphs, Cancel cancel) {
        if (paragraphs == null || paragraphs.isEmpty()) {
            return Result.ok(new String[0]);
        }
        if (!config.isConfigured()) {
            return Result.fail("未配置翻译服务", false);
        }
        String endpoint = TransProtocol.endpoint(config.baseUrl());
        // https 的强制在 SettingsActivity 里做：那是「用户能输入什么」的规则，
        // 客户端只管把配置的地址打出去（也让本地假服务能进仪器测试）。

        String body = requestBody(config.model(), paragraphs, endpoint);
        String lastError = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            if (cancel != null && cancel.isCancelled()) {
                return Result.fail("已取消", false);
            }
            try {
                // 每次真正发请求都留一行：验收要能证明「第二次展开没有网络请求」
                Log.i(TAG, "POST " + endpoint + " paras=" + paragraphs.size()
                        + " attempt=" + (attempt + 1));
                String reply = post(endpoint, body);
                String[] parsed =
                        TransProtocol.parseReply(reply, paragraphs.size());
                if (TransProtocol.countTranslated(parsed) == 0) {
                    // 200 但一行都没解析出来：模型答非所问，值得再试一次
                    lastError = "返回内容无法解析";
                    continue;
                }
                return Result.ok(parsed);
            } catch (HttpError e) {
                lastError = e.getMessage();
                Log.w(TAG, "http error: " + lastError + " retryable=" + e.retryable);
                if (!e.retryable) {
                    return Result.fail(lastError, false);
                }
            } catch (IOException e) {
                lastError = describe(e);
                Log.w(TAG, "io error: " + lastError, e);
            }
        }
        return Result.fail(lastError == null ? "翻译失败" : lastError, true);
    }

    /** 请求体。static 是为了能在 JVM 单测里断言结构——形状错就是每次都 400。 */
    static String requestBody(String model, List<String> paragraphs, String endpoint) {
        JSONArray messages = new JSONArray();
        try {
            messages.put(new JSONObject()
                    .put("role", "system")
                    .put("content", TransProtocol.SYSTEM_PROMPT));
            messages.put(new JSONObject()
                    .put("role", "user")
                    .put("content", TransProtocol.buildUser(
                            paragraphs.toArray(new String[0]))));
            JSONObject body = new JSONObject()
                    .put("model", model)
                    .put("messages", messages)
                    // 翻译要稳定复现，不要发挥
                    .put("temperature", 0.2)
                    .put("max_tokens", MAX_TOKENS)
                    .put("stream", false);
            if (TransProtocol.disablesThinking(endpoint)) {
                // DeepSeek 新模型默认开思考模式，会先吐一大段 reasoning_content——
                // 又慢又贵，翻译这活不需要推理。别的厂商不认这个字段，所以只对它下发，
                // 免得碰到严格校验参数的服务商被 400。
                body.put("thinking", new JSONObject().put("type", "disabled"));
            }
            return body.toString();
        } catch (JSONException e) {
            // 只可能是 model 为空这类编程错误，不该在运行时发生
            throw new IllegalStateException(e);
        }
    }

    private String post(String endpoint, String body) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            c.setRequestMethod("POST");
            c.setConnectTimeout(CONNECT_TIMEOUT_MS);
            c.setReadTimeout(READ_TIMEOUT_MS);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("Authorization", "Bearer " + config.apiKey());

            try (OutputStream out = c.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int code = c.getResponseCode();
            if (code < 200 || code >= 300) {
                // 错误体只用来给人看，可以截断——别把整页 HTML 塞进 toast
                throw new HttpError(code, clip(readAll(c.getErrorStream()), 300));
            }
            // 成功响应必须完整读：截断会切出半个 JSON，解析必然失败
            return content(readAll(c.getInputStream()));
        } finally {
            c.disconnect();
        }
    }

    /**
     * 从 chat/completions 的回复里取 choices[0].message.content。
     *
     * <p>{@code raw} 必须是**完整**响应体，见 {@link #readAll}。
     */
    public static String content(String raw) throws IOException {
        try {
            JSONObject json = new JSONObject(raw);
            JSONArray choices = json.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                throw new IOException("回复里没有 choices");
            }
            JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
            // 注意：optString(k, null) 遇到 JSON 的 null 会返回字符串 "null"，不是 Java null。
            // 必须用 isNull 判，否则「content: null」会被当成正常译文 "null" 写进库里。
            String content = msg == null || msg.isNull("content")
                    ? null : msg.optString("content", null);
            if (content == null || content.isEmpty()) {
                // 思考模式下模型可能只吐了 reasoning_content 而 content 为空；
                // 这时报「内容为空」会让人以为是网络问题，说明白点更好排查
                String reasoning = msg == null || msg.isNull("reasoning_content")
                        ? null : msg.optString("reasoning_content", null);
                throw new IOException(reasoning == null || reasoning.isEmpty()
                        ? "回复内容为空"
                        : "模型只返回了思考过程，没有译文（请关掉思考模式或换个模型）");
            }
            return content;
        } catch (JSONException e) {
            // 有的中转返回 HTML 错误页；这时也当网络错，可重试。
            // 把响应长度一起报出来——"回复不是合法 JSON" 配上长度，能一眼看出是截断还是真 HTML
            throw new IOException("回复不是合法 JSON（长度 " + raw.length() + "）", e);
        }
    }

    /**
     * 原样读完整个响应体。
     *
     * <p>**不要在这里截断**：成功响应是 JSON，切一半就必然解析失败。
     * 需要短文本（错误提示）由调用方用 {@link #clip} 自己截。
     *
     * <p>public 是为了让仪器测试能直接喂一个长流进来——这个截断 bug 就发生在这一层，
     * 只测 {@link #content} 是测不到的（旧用例正是这么漏过去的）。
     */
    public static String readAll(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        try (InputStream s = in) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = s.read(b)) != -1) {
                buf.write(b, 0, n);
            }
            return buf.toString("UTF-8").trim();
        }
    }

    /** 只给错误提示用：截短，别把整页 HTML 塞进 toast。 */
    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String describe(IOException e) {
        String m = e.getMessage();
        return m == null || m.isEmpty() ? e.getClass().getSimpleName() : m;
    }

    /** HTTP 非 2xx。4xx 里只有 408/429 值得重试。 */
    private static class HttpError extends IOException {
        final boolean retryable;

        HttpError(int code, String detail) {
            super("HTTP " + code + (detail.isEmpty() ? "" : "：" + detail));
            this.retryable = code >= 500 || code == 408 || code == 429;
        }
    }
}
