package com.englishread.app.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.englishread.app.R;
import com.englishread.app.model.Book;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 书架列表。点击进阅读器，长按删除。 */
public class LibraryAdapter extends RecyclerView.Adapter<LibraryAdapter.VH> {

    public interface OnBookLongPress {
        void onLongPress(Book book);
    }

    public interface OnBookClick {
        void onClick(Book book);
    }

    private final List<Book> items = new ArrayList<>();
    private final OnBookLongPress longPress;
    private final OnBookClick click;

    public LibraryAdapter(List<Book> initial, OnBookClick click, OnBookLongPress longPress) {
        this.items.addAll(initial);
        this.click = click;
        this.longPress = longPress;
    }

    public void replace(List<Book> books) {
        items.clear();
        items.addAll(books);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_book, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Book b = items.get(position);
        h.title.setText(b.title);
        // 书名用衬线：资源里的 fontFamily 会被主题的 textAppearance 盖回无衬线（实测），
        // 只有代码设才稳。首页这一处最显眼，值得多一行。
        h.title.setTypeface(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD);
        // 没有作者就整行收起来，否则每张卡都空一行，列表节奏会乱
        boolean hasAuthor = b.author != null && !b.author.trim().isEmpty();
        h.author.setText(hasAuthor ? b.author : "");
        h.author.setVisibility(hasAuthor ? View.VISIBLE : View.GONE);
        int pct = Math.round(b.progressPct * 100);
        h.progress.setText(String.format(Locale.US, "%d%%", pct));
        h.progressBar.setProgressCompat(pct, false);

        // 封面是 48x64 的缩略图，直接主线程解码：几百 KB 的 jpg 也就几毫秒。
        // ponytail: 书多了（>200）再换后台解码 + 环形缓存，现在不值得。
        h.cover.setImageDrawable(null);
        if (b.coverPath != null) {
            Bitmap bmp = BitmapFactory.decodeFile(b.coverPath);
            if (bmp != null) {
                h.cover.setImageBitmap(bmp);
            }
        }

        h.itemView.setOnClickListener(v -> {
            if (click != null) {
                click.onClick(b);
            }
        });
        h.itemView.setOnLongClickListener(v -> {
            if (longPress != null) {
                longPress.onLongPress(b);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView author;
        final TextView progress;
        final com.google.android.material.progressindicator.LinearProgressIndicator progressBar;
        final android.widget.ImageView cover;

        VH(View v) {
            super(v);
            title = v.findViewById(R.id.title);
            author = v.findViewById(R.id.author);
            progress = v.findViewById(R.id.progress);
            progressBar = v.findViewById(R.id.progress_bar);
            cover = v.findViewById(R.id.cover);
        }
    }
}
