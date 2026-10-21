package com.englishread.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.englishread.app.R;

/**
 * 设置首页：只做导航。
 *
 * <p>原来这一页是「翻译服务三件套 + 隐私 + 数据来源」一整张长表单，
 * 用户想换个明暗主题得先滚过整张表。拆成两个入口后各管一件事：
 * <ul>
 *   <li>{@link TranslationSettingsActivity} —— 翻译配置</li>
 *   <li>{@link ThemeSettingsActivity} —— 界面主题（只影响阅读器之外的页面）</li>
 * </ul>
 */
public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        com.google.android.material.appbar.MaterialToolbar bar =
                findViewById(R.id.settings_toolbar);
        bar.setNavigationOnClickListener(v -> finish());
        applyWindowInsets(findViewById(R.id.settings_root));

        findViewById(R.id.row_translation).setOnClickListener(v ->
                startActivity(new Intent(this, TranslationSettingsActivity.class)));
        findViewById(R.id.row_theme).setOnClickListener(v ->
                startActivity(new Intent(this, ThemeSettingsActivity.class)));
    }

    /**
     * 副标题要显示「当前：深浅」，所以每次回到这页都重读一次——
     * 从主题页返回时主题可能已经变了（这页自己也会被系统重建，两道保险）。
     */
    @Override
    protected void onResume() {
        super.onResume();
        TextView desc = findViewById(R.id.row_theme_desc);
        desc.setText(getString(R.string.settings_entry_theme_desc,
                UiTheme.label(this, UiTheme.mode(this))));
    }

    /**
     * targetSdk 35+ 强制 edge-to-edge：不让出状态栏，标题会顶到时间/信号上。
     *
     * <p><b>必须垫在根布局上，不能垫在工具栏上</b>：工具栏高度是固定的 `actionBarSize`，
     * 给它加 paddingTop 只会把内容区挤扁——实测标题被压成 12px 高、贴在工具栏下边缘。
     * 根布局是 match_parent，加 padding 才是「整块内容往下让」。
     *
     * <p>同时补一次 {@code requestApplyInsets}：listener 是在 View 已经 attach 之后才挂的，
     * 系统那次分发已经过去了，不主动请求就永远等不到回调。
     */
    private void applyWindowInsets(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
