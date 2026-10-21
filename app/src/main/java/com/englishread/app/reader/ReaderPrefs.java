package com.englishread.app.reader;

import android.content.Context;
import android.content.SharedPreferences;

import com.englishread.app.R;

/**
 * 阅读器的显示设置。都存在私有 SharedPreferences 里——
 * 这些是纯本地偏好，没有同步需求，犯不上开数据库表。
 */
public class ReaderPrefs {

    private static final String FILE = "reader_prefs";

    private static final String KEY_TEXT_SIZE = "text_size";
    private static final String KEY_LINE_SPACING = "line_spacing";
    private static final String KEY_PARA_SPACING = "para_spacing";
    private static final String KEY_LETTER_SPACING = "letter_spacing";
    private static final String KEY_INDENT = "indent_chars";
    private static final String KEY_DARK = "dark";
    private static final String KEY_BRIGHTNESS = "brightness";
    private static final String KEY_FOLLOW_SYSTEM = "follow_system_brightness";
    private static final String KEY_SHOW_MARKER = "show_marker";
    private static final String KEY_SPEECH_RATE = "speech_rate";
    private static final String KEY_TAP_TO_PAUSE = "tap_to_pause";

    /** 字号档位（sp）。给固定档位而不是连续滑杆：排版尺寸本来就该是几档，不是无限可调。 */
    public static final int[] TEXT_SIZES = {14, 16, 18, 20, 22, 24, 28};
    /** 行距倍数 */
    public static final float[] LINE_SPACINGS = {1.2f, 1.4f, 1.5f, 1.8f, 2.0f};
    /**
     * 句距（dp）。一句话一段，这条缝就是句子之间的呼吸。
     * **最小档不能是 0**：它同时是「这里没有字」的点击区，长句占满整屏时全靠它呼出菜单。
     */
    public static final int[] PARA_SPACINGS = {8, 12, 16, 24, 32};
    /** 字距（em）：0 就是字体默认间距。 */
    public static final float[] LETTER_SPACINGS = {0f, 0.02f, 0.04f, 0.08f};
    /** 首行缩进，单位「字」——1 字 = 一个正文字号的宽度。 */
    public static final int[] INDENTS = {0, 1, 2};
    /**
     * 朗读语速（倍数）。档位而不是连续滑杆：听书时要的是「慢一点/快一点」，
     * 不是精确到 0.01 的调参；而且 TTS 换语速要重念当前句，连续滑杆会一直重念。
     */
    public static final float[] SPEECH_RATES = {0.7f, 0.85f, 1.0f, 1.25f, 1.5f};

    private final SharedPreferences sp;

    public ReaderPrefs(Context c) {
        this.sp = c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public int textSize() {
        return sp.getInt(KEY_TEXT_SIZE, 18);
    }

    public void setTextSize(int sizeSp) {
        sp.edit().putInt(KEY_TEXT_SIZE, sizeSp).apply();
    }

    public float lineSpacing() {
        return sp.getFloat(KEY_LINE_SPACING, 1.5f);
    }

    public void setLineSpacing(float mult) {
        sp.edit().putFloat(KEY_LINE_SPACING, mult).apply();
    }

    /** 段距（dp）。同时决定「段间空白」那条呼菜单的缝有多宽。 */
    public int paraSpacing() {
        return sp.getInt(KEY_PARA_SPACING, 16);
    }

    public void setParaSpacing(int dp) {
        sp.edit().putInt(KEY_PARA_SPACING, Math.max(1, dp)).apply();
    }

    /** 字距（em），0 = 字体默认。 */
    public float letterSpacing() {
        return sp.getFloat(KEY_LETTER_SPACING, 0f);
    }

    public void setLetterSpacing(float em) {
        sp.edit().putFloat(KEY_LETTER_SPACING, em).apply();
    }

    /** 首行缩进几个字；0 = 不缩进。 */
    public int indent() {
        return sp.getInt(KEY_INDENT, 0);
    }

    public void setIndent(int chars) {
        sp.edit().putInt(KEY_INDENT, Math.max(0, chars)).apply();
    }

    /** null = 跟随系统（用 DayNight 的默认）。 */
    public Boolean dark() {
        return sp.contains(KEY_DARK) ? sp.getBoolean(KEY_DARK, false) : null;
    }

    public void setDark(Boolean dark) {
        if (dark == null) {
            sp.edit().remove(KEY_DARK).apply();
        } else {
            sp.edit().putBoolean(KEY_DARK, dark).apply();
        }
    }

    /** 0..1 的窗口亮度；{@link #followSystemBrightness()} 为 true 时不生效。 */
    public float brightness() {
        return sp.getFloat(KEY_BRIGHTNESS, 0.6f);
    }

    public void setBrightness(float v) {
        sp.edit().putFloat(KEY_BRIGHTNESS, Math.max(0.02f, Math.min(1f, v))).apply();
    }

    public boolean followSystemBrightness() {
        return sp.getBoolean(KEY_FOLLOW_SYSTEM, true);
    }

    public void setFollowSystemBrightness(boolean follow) {
        sp.edit().putBoolean(KEY_FOLLOW_SYSTEM, follow).apply();
    }

    /** 是否显示每段下方的「段」按钮（隐藏后就没有段落翻译入口）。 */
    public boolean showMarker() {
        return sp.getBoolean(KEY_SHOW_MARKER, true);
    }

    public void setShowMarker(boolean show) {
        sp.edit().putBoolean(KEY_SHOW_MARKER, show).apply();
    }

    /** 朗读语速倍数，1.0 = 引擎默认。 */
    public float speechRate() {
        return sp.getFloat(KEY_SPEECH_RATE, 1.0f);
    }

    public void setSpeechRate(float rate) {
        sp.edit().putFloat(KEY_SPEECH_RATE, Math.max(0.5f, Math.min(2.0f, rate))).apply();
    }

    /**
     * 听书时点正文是否暂停/继续。**默认关**：点词查词是这个 App 的核心动作，
     * 开着会把它抢走（HANDOFF D2「词优先」），想要这个手感的人自己去开。
     */
    public boolean tapToPause() {
        return sp.getBoolean(KEY_TAP_TO_PAUSE, false);
    }

    public void setTapToPause(boolean on) {
        sp.edit().putBoolean(KEY_TAP_TO_PAUSE, on).apply();
    }


    /** 档位下标，找不到就返回默认档（18sp）。 */
    public int textSizeIndex() {
        return nearestIndex(TEXT_SIZES, textSize());
    }

    public int lineSpacingIndex() {
        return nearestIndex(LINE_SPACINGS, lineSpacing());
    }

    public int paraSpacingIndex() {
        return nearestIndex(PARA_SPACINGS, paraSpacing());
    }

    public int letterSpacingIndex() {
        return nearestIndex(LETTER_SPACINGS, letterSpacing());
    }

    public int indentIndex() {
        return nearestIndex(INDENTS, indent());
    }

    public int speechRateIndex() {
        return nearestIndex(SPEECH_RATES, speechRate());
    }

    private static int nearestIndex(int[] values, int target) {
        int best = 0;
        int bestDiff = Integer.MAX_VALUE;
        for (int i = 0; i < values.length; i++) {
            int d = Math.abs(values[i] - target);
            if (d < bestDiff) {
                bestDiff = d;
                best = i;
            }
        }
        return best;
    }

    private static int nearestIndex(float[] values, float target) {
        int best = 0;
        float bestDiff = Float.MAX_VALUE;
        for (int i = 0; i < values.length; i++) {
            float d = Math.abs(values[i] - target);
            if (d < bestDiff) {
                bestDiff = d;
                best = i;
            }
        }
        return best;
    }

    /** 供设置面板显示的档位文字。 */
    public static String textSizeLabel(Context c, int index) {
        return c.getString(R.string.reader_text_size_value, TEXT_SIZES[index]);
    }

    public static String lineSpacingLabel(int index) {
        return String.valueOf(LINE_SPACINGS[index]);
    }

    public static String paraSpacingLabel(Context c, int index) {
        return c.getString(R.string.reader_dp_value, PARA_SPACINGS[index]);
    }

    public static String letterSpacingLabel(Context c, int index) {
        float em = LETTER_SPACINGS[index];
        return em <= 0f ? c.getString(R.string.reader_value_off)
                : c.getString(R.string.reader_em_value, em);
    }

    public static String indentLabel(Context c, int index) {
        int chars = INDENTS[index];
        return chars <= 0 ? c.getString(R.string.reader_value_off)
                : c.getString(R.string.reader_chars_value, chars);
    }

    public static String speechRateLabel(Context c, int index) {
        return c.getString(R.string.reader_speech_rate_value, SPEECH_RATES[index]);
    }
}
