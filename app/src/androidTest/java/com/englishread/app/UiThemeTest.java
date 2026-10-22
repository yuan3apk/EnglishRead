package com.englishread.app;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.englishread.app.ui.TranslationSettingsActivity;
import com.englishread.app.ui.UiTheme;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * 界面主题真的作用在「阅读器之外的页面」上。
 *
 * <p>守的是一条容易写歪的线：{@code UiTheme} 存了偏好但没落到进程上时，
 * 设置页看起来完全正常、只有明暗不动——这类 bug 单测抓不到，必须真起一个页面看 uiMode。
 *
 * <p>阅读器那一侧由 {@link ReaderThemeTest} 守着（它反过来验证全局主题不入侵阅读器）。
 */
@RunWith(AndroidJUnit4.class)
public class UiThemeTest {

    private Context ctx;

    @Before
    public void setUp() {
        ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        UiTheme.set(ctx, UiTheme.FOLLOW_SYSTEM);
    }

    @After
    public void tearDown() {
        UiTheme.set(ctx, UiTheme.FOLLOW_SYSTEM);
    }

    @Test
    public void darkUiThemeDarkensNonReaderScreens() {
        assertScreenGets(UiTheme.DARK, Configuration.UI_MODE_NIGHT_YES);
    }

    @Test
    public void lightUiThemeBrightensNonReaderScreens() {
        assertScreenGets(UiTheme.LIGHT, Configuration.UI_MODE_NIGHT_NO);
    }

    private void assertScreenGets(int uiThemeMode, int expectedNightMode) {
        UiTheme.set(ctx, uiThemeMode);

        Intent i = new Intent(ctx, TranslationSettingsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try (ActivityScenario<TranslationSettingsActivity> scenario =
                     ActivityScenario.launch(i)) {
            scenario.onActivity(a -> assertEquals("设置页应跟随界面主题",
                    expectedNightMode,
                    a.getResources().getConfiguration().uiMode
                            & Configuration.UI_MODE_NIGHT_MASK));
        }
    }
}
