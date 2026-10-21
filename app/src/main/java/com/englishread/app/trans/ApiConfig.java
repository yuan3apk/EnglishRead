package com.englishread.app.trans;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

/**
 * LLM 接入配置：base_url / key / model 三件套 + 开关。
 *
 * <p>存在私有 SharedPreferences 里。私有目录受系统沙箱保护，
 * {@code ponytail:} 明文存储的升级路径是 EncryptedSharedPreferences（需引 androidx.security），
 * v1 不引——见 HANDOFF R8。
 */
public class ApiConfig {

    private static final String FILE = "api_config";

    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_MODEL = "model";

    private final SharedPreferences sp;

    public ApiConfig(Context c) {
        this.sp = c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public String baseUrl() {
        return sp.getString(KEY_BASE_URL, "");
    }

    public void setBaseUrl(String v) {
        sp.edit().putString(KEY_BASE_URL, trim(v)).apply();
    }

    public String apiKey() {
        return sp.getString(KEY_API_KEY, "");
    }

    public void setApiKey(String v) {
        sp.edit().putString(KEY_API_KEY, trim(v)).apply();
    }

    public String model() {
        return sp.getString(KEY_MODEL, "");
    }

    public void setModel(String v) {
        sp.edit().putString(KEY_MODEL, trim(v)).apply();
    }

    /** 三件套齐了才算配置好——缺一个都不该发请求。 */
    public boolean isConfigured() {
        return !TextUtils.isEmpty(baseUrl())
                && !TextUtils.isEmpty(apiKey())
                && !TextUtils.isEmpty(model());
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }
}
