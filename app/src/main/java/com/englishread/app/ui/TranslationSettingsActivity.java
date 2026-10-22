package com.englishread.app.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.englishread.app.BuildConfig;
import com.englishread.app.R;
import com.englishread.app.trans.ApiConfig;
import com.englishread.app.trans.ProviderTemplates;
import com.englishread.app.trans.TransProtocol;
import com.google.android.material.textfield.TextInputEditText;

/**
 * 翻译服务配置：接口地址 / API Key / 模型三件套。
 *
 * <p>只管存取，不做「测试连接」——真发一次请求要花一次配额，
 * 而配置错在第一次点「段」时就会明确报出来，不需要额外的按钮。
 */
public class TranslationSettingsActivity extends AppCompatActivity {

    private ApiConfig config;
    private TextInputEditText baseUrl;
    private TextInputEditText apiKey;
    private TextInputEditText model;
    private TextView error;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_translation_settings);

        com.google.android.material.appbar.MaterialToolbar bar =
                findViewById(R.id.trans_toolbar);
        bar.setNavigationOnClickListener(v -> finish());
        applyWindowInsets(findViewById(R.id.trans_root));

        config = new ApiConfig(this);
        baseUrl = findViewById(R.id.input_base_url);
        apiKey = findViewById(R.id.input_api_key);
        model = findViewById(R.id.input_model);
        error = findViewById(R.id.settings_error);

        baseUrl.setText(config.baseUrl());
        apiKey.setText(config.apiKey());
        model.setText(config.model());

        setupTemplates();

        findViewById(R.id.btn_save).setOnClickListener(v -> save());
    }

    /**
     * 模板 chip：点一下把 base_url 与 model 填好，Key 留空让用户自己贴。
     *
     * <p>不预选任何一家——预选等于替用户做了决定，而他可能并没有那家的账号。
     * 当前配置若正好等于某家模板，就把那家标成选中，改动过的配置则不标。
     */
    private void setupTemplates() {
        com.google.android.material.chip.ChipGroup group =
                findViewById(R.id.template_group);
        String curUrl = text(baseUrl);
        String curModel = text(model);

        for (ProviderTemplates.Template t : ProviderTemplates.all()) {
            com.google.android.material.chip.Chip chip =
                    new com.google.android.material.chip.Chip(this);
            chip.setText(t.name);
            chip.setCheckable(true);
            chip.setTag(t);
            chip.setId(View.generateViewId());
            group.addView(chip);
            if (t.baseUrl.equals(curUrl) && t.model.equals(curModel)) {
                group.check(chip.getId());
                showTemplateNote(t);
            }
        }

        group.setOnCheckedStateChangeListener((g, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            View v = g.findViewById(checkedIds.get(0));
            if (v == null || !(v.getTag() instanceof ProviderTemplates.Template)) {
                return;
            }
            ProviderTemplates.Template t = (ProviderTemplates.Template) v.getTag();
            // 只填地址和模型，绝不动 Key
            baseUrl.setText(t.baseUrl);
            model.setText(t.model);
            showTemplateNote(t);
            error.setVisibility(View.GONE);
        });
    }

    private void showTemplateNote(ProviderTemplates.Template t) {
        TextView note = findViewById(R.id.template_note);
        note.setText(t.note + "\n" + getString(R.string.settings_key_page, t.keyPage));
        note.setVisibility(View.VISIBLE);
        note.setOnClickListener(v -> {
            // 长按/点击跳浏览器没必要，用户自己复制更快；这里只在 debug 提示明文限制
            if (!TransProtocol.isHttps(t.baseUrl)) {
                Toast.makeText(this, R.string.settings_template_ollama_warning,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * targetSdk 35+ 强制 edge-to-edge：不让出状态栏，标题会顶到时间/信号上。
     *
     * <p><b>必须垫在根布局上，不能垫在工具栏上</b>：工具栏高度固定为 `actionBarSize`，
     * 给它加 paddingTop 只会把内容区挤扁（实测标题被压成一行窄条）。
     */
    private void applyWindowInsets(View root) {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars =
                    insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(root);
    }

    /** 校验只挡「明确送不出去」的配置，格式猜错留给服务端报——少写一套校验。 */
    private void save() {
        String url = text(baseUrl);
        String key = text(apiKey);
        String m = text(model);

        if (TextUtils.isEmpty(url)) {
            showError(getString(R.string.settings_need_base_url));
            return;
        }
        if (!TransProtocol.allowsCleartext(url, BuildConfig.DEBUG)) {
            showError(getString(R.string.settings_must_be_https));
            return;
        }
        if (TextUtils.isEmpty(key)) {
            showError(getString(R.string.settings_need_api_key));
            return;
        }
        if (TextUtils.isEmpty(m)) {
            showError(getString(R.string.settings_need_model));
            return;
        }

        config.setBaseUrl(url);
        config.setApiKey(key);
        config.setModel(m);
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void showError(String msg) {
        error.setText(msg);
        error.setVisibility(View.VISIBLE);
    }

    private static String text(TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }
}
