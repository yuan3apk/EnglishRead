package com.englishread.app.ui;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.englishread.app.R;
import com.google.android.material.button.MaterialButtonToggleGroup;

/**
 * 界面主题：书架 / 生词本 / 设置页的明暗。
 *
 * <p>**不包含阅读器**：阅读器的明暗由它自己的设置面板管
 * （{@code ReaderActivity} 里 `setLocalNightMode`，只影响那一个 Activity）。
 * local 模式的优先级高于 {@code setDefaultNightMode}，所以这里改什么都不会波及阅读器；
 * 反过来阅读器里改成深色，书架也不会跟着黑。
 *
 * <p>选中即生效，没有「保存」按钮——主题是所见即所得的东西，多一步确认没意义。
 */
public class ThemeSettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_theme_settings);

        com.google.android.material.appbar.MaterialToolbar bar =
                findViewById(R.id.theme_toolbar);
        bar.setNavigationOnClickListener(v -> finish());
        applyWindowInsets(findViewById(R.id.theme_root));

        MaterialButtonToggleGroup group = findViewById(R.id.ui_theme_group);
        int current = UiTheme.mode(this);
        group.check(checkedIdOf(current));

        group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            int target = modeOf(checkedId);
            if (target == UiTheme.mode(this)) {
                return; // 重建后 check() 会再触发一次，值没变就别再折腾
            }
            // 会立即重建所有界面（包括本页），所以不需要额外刷新界面
            UiTheme.set(this, target);
        });
    }

    private static int checkedIdOf(int mode) {
        return mode == UiTheme.DARK ? R.id.ui_theme_dark
                : mode == UiTheme.LIGHT ? R.id.ui_theme_light : R.id.ui_theme_system;
    }

    private static int modeOf(int checkedId) {
        return checkedId == R.id.ui_theme_dark ? UiTheme.DARK
                : checkedId == R.id.ui_theme_light ? UiTheme.LIGHT : UiTheme.FOLLOW_SYSTEM;
    }

    /**
     * targetSdk 35+ 强制 edge-to-edge：不让出状态栏，标题会顶到时间/信号上。
     *
     * <p><b>垫在根布局上，不垫工具栏</b>：工具栏高度固定，加 paddingTop 会把标题挤扁。
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
