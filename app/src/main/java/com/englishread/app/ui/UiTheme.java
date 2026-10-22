package com.englishread.app.ui;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import com.englishread.app.R;

/**
 * 阅读器之外的界面（书架 / 生词本 / 设置页）的明暗主题。
 *
 * <p><b>与 {@code ReaderPrefs.dark()} 是两套独立偏好，别合并。</b>
 * 阅读器有自己的主题开关，用的是 {@code getDelegate().setLocalNightMode()}（只作用于那个 Activity）；
 * 这里走进程级的 {@code setDefaultNightMode()}。local 优先级高于 default，
 * 所以这里怎么改都不会把阅读器的明暗顶掉——这正是需求要的「互不影响」。
 *
 * <p>反过来也别在普通 Activity 里调 local：那样会把全局设置变成一次性生效。
 */
public final class UiTheme {

    /** 跟随系统（默认）。 */
    public static final int FOLLOW_SYSTEM = 0;
    public static final int LIGHT = 1;
    public static final int DARK = 2;

    private static final String FILE = "app_prefs";
    private static final String KEY_MODE = "ui_theme_mode";

    private UiTheme() {
    }

    public static int mode(Context c) {
        return sp(c).getInt(KEY_MODE, FOLLOW_SYSTEM);
    }

    /**
     * 用户点了某个选项：落盘 + 立刻生效。
     *
     * <p>{@code setDefaultNightMode} 会让所有已创建的 AppCompat 界面按新配置重建，
     * 所以当前这一页也会重建一次——这是期望行为，不是 bug。
     */
    public static void set(Context c, int mode) {
        sp(c).edit().putInt(KEY_MODE, mode).apply();
        apply(c);
    }

    /**
     * 把已存的偏好落到进程上。
     *
     * <p>必须在 {@code App.onCreate()} 里调：晚于第一个 Activity 创建就只改得动之后打开的页面，
     * 首屏会先闪一下旧主题。
     */
    public static void apply(Context c) {
        AppCompatDelegate.setDefaultNightMode(nightMode(mode(c)));
    }

    public static int nightMode(int mode) {
        if (mode == DARK) {
            return AppCompatDelegate.MODE_NIGHT_YES;
        }
        if (mode == LIGHT) {
            return AppCompatDelegate.MODE_NIGHT_NO;
        }
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    /** 给界面显示的当前值文字（跟随系统 / 浅色 / 深色）。 */
    public static String label(Context c, int mode) {
        return c.getString(mode == DARK ? R.string.ui_theme_dark
                : mode == LIGHT ? R.string.ui_theme_light : R.string.ui_theme_system);
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
