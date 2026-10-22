package com.englishread.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.englishread.app.R;
import com.englishread.app.model.VocabEntry;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.ArrayList;
import java.util.List;

/** 生词本列表。勾「已掌握」写库；词与释义不在这里改。 */
public class VocabAdapter extends RecyclerView.Adapter<VocabAdapter.VH> {

    public interface Listener {
        void onMastered(VocabEntry entry, boolean mastered);
    }

    private final List<VocabEntry> items = new ArrayList<>();
    private final Listener listener;

    public VocabAdapter(Listener listener) {
        this.listener = listener;
    }

    public void replace(List<VocabEntry> rows) {
        items.clear();
        items.addAll(rows);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_vocab, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        VocabEntry v = items.get(position);
        h.word.setText(v.word);
        // 词与原句走衬线（同 LibraryAdapter 的说明：资源里设不住，只有代码设才稳）
        h.word.setTypeface(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD);
        h.sentence.setTypeface(android.graphics.Typeface.SERIF, android.graphics.Typeface.ITALIC);
        setText(h.phon, v.phon == null || v.phon.isEmpty() ? null : "/" + v.phon + "/");
        setText(h.translation, v.translation);
        setText(h.sentence, v.sentence);
        setText(h.book, v.bookTitle == null ? null
                : h.itemView.getContext().getString(R.string.vocab_from_book, v.bookTitle));
        // 已掌握的整行压暗，扫一眼就能看出还剩多少
        h.itemView.setAlpha(v.mastered ? 0.55f : 1f);

        // 先摘监听再 setChecked：复用时会因为「绑定导致的勾选」误写库
        h.mastered.setOnCheckedChangeListener(null);
        h.mastered.setChecked(v.mastered);
        h.mastered.setOnCheckedChangeListener((b, checked) -> {
            if (listener != null) {
                listener.onMastered(v, checked);
            }
        });
    }

    private static void setText(TextView t, String text) {
        boolean has = text != null && !text.isEmpty();
        t.setText(has ? text : null);
        t.setVisibility(has ? View.VISIBLE : View.GONE);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView word;
        final TextView phon;
        final TextView translation;
        final TextView sentence;
        final TextView book;
        final MaterialCheckBox mastered;

        VH(View v) {
            super(v);
            word = v.findViewById(R.id.word);
            phon = v.findViewById(R.id.phon);
            translation = v.findViewById(R.id.translation);
            sentence = v.findViewById(R.id.sentence);
            book = v.findViewById(R.id.book);
            mastered = v.findViewById(R.id.mastered);
        }
    }
}
