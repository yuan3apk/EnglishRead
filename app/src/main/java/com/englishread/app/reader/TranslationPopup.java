package com.englishread.app.reader;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.englishread.app.R;
import com.englishread.app.model.WordEntry;

/**
 * 单词卡：锚定到被点词的矩形，下方放不下就翻到上方。
 *
 * <p>只做展示，查词由调用方在 IO 线程做完再送进来——PopupWindow 不该自己联网。
 */
public class TranslationPopup {

    public interface Listener {
        void onSpeak(String word);

        void onAddVocab(WordEntry entry);
    }

    private final Context context;
    private final Listener listener;
    private PopupWindow window;

    public TranslationPopup(Context c, Listener listener) {
        this.context = c;
        this.listener = listener;
    }

    /**
     * @param anchor  词的屏幕矩形（x/y 为屏幕坐标，宽高为词的大小）
     * @param entry   查词结果，null 表示词典里没有
     */
    public void show(View parent, int[] anchor, String word, @Nullable WordEntry entry) {
        dismiss();

        View content = LayoutInflater.from(context).inflate(R.layout.popup_word, null);
        TextView title = content.findViewById(R.id.word);
        TextView phonetic = content.findViewById(R.id.phonetic);
        TextView trans = content.findViewById(R.id.translation);
        View speak = content.findViewById(R.id.speak);
        View addVocab = content.findViewById(R.id.add_vocab);

        title.setText(word);
        if (entry != null && entry.phonetic != null && !entry.phonetic.isEmpty()) {
            phonetic.setText("/" + entry.phonetic + "/");
            phonetic.setVisibility(View.VISIBLE);
        } else {
            phonetic.setVisibility(View.GONE);
        }
        if (entry != null && entry.translation != null && !entry.translation.isEmpty()) {
            trans.setText(entry.translation);
        } else {
            trans.setText(R.string.word_not_found);
        }

        speak.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSpeak(word);
            }
        });
        addVocab.setOnClickListener(v -> {
            if (listener != null && entry != null) {
                listener.onAddVocab(entry);
            }
            dismiss();
        });

        window = new PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        window.setBackgroundDrawable(new ColorDrawable(0));
        window.setOutsideTouchable(true);
        window.setElevation(context.getResources().getDisplayMetrics().density * 8);

        // 先量一遍才好决定放上面还是下面
        content.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int popupH = content.getMeasuredHeight();
        int popupW = content.getMeasuredWidth();
        int screenW = parent.getWidth();

        int x = anchor[0];
        if (x + popupW > screenW) {
            x = Math.max(0, screenW - popupW);
        }
        int below = anchor[1] + anchor[3];
        int screenH = parent.getHeight();
        boolean fitsBelow = below + popupH <= screenH;

        int flag = fitsBelow ? Gravity.TOP | Gravity.START : Gravity.BOTTOM | Gravity.START;
        int y = fitsBelow ? below : screenH - anchor[1];
        window.showAtLocation(parent, flag, x, y);
    }

    public void dismiss() {
        if (window != null) {
            window.dismiss();
            window = null;
        }
    }

    public boolean isShowing() {
        return window != null && window.isShowing();
    }
}
