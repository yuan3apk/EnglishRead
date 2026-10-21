package com.englishread.app.reader;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.englishread.app.R;
import com.englishread.app.model.Chapter;

import java.util.ArrayList;
import java.util.List;

/** 目录：章节列表，当前章高亮。 */
public class TocAdapter extends RecyclerView.Adapter<TocAdapter.VH> {

    public interface OnPick {
        void onPick(int index);
    }

    private final List<Chapter> items = new ArrayList<>();
    private final OnPick onPick;
    private int current = -1;

    public TocAdapter(OnPick onPick) {
        this.onPick = onPick;
    }

    public void submit(List<Chapter> chapters, int currentIndex) {
        items.clear();
        items.addAll(chapters);
        current = currentIndex;
        notifyDataSetChanged();
    }

    public void setCurrent(int index) {
        int old = current;
        current = index;
        if (old >= 0) {
            notifyItemChanged(old);
        }
        if (index >= 0) {
            notifyItemChanged(index);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_toc, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Chapter c = items.get(position);
        h.item.setText(c.title == null ? "" : c.title);
        boolean isCurrent = position == current;
        // 当前章只用颜色和字重区分，不加图标——列表里加图标反而吵
        h.item.setSelected(isCurrent);
        h.item.setTextColor(isCurrent
                ? h.item.getContext().getColor(
                        com.englishread.app.R.color.brand)
                : resolvedDefaultColor(h.item));
        h.item.getPaint().setFakeBoldText(isCurrent);
        h.item.setOnClickListener(v -> {
            if (onPick != null) {
                onPick.onPick(position);
            }
        });
    }

    /** 记下第一次绑定时主题给的颜色，切回非当前章时用。 */
    private static int resolvedDefaultColor(TextView tv) {
        Object tag = tv.getTag(R.id.toc_item);
        if (tag instanceof Integer) {
            return (Integer) tag;
        }
        int color = tv.getCurrentTextColor();
        tv.setTag(R.id.toc_item, color);
        return color;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView item;

        VH(View v) {
            super(v);
            item = v.findViewById(R.id.toc_item);
        }
    }
}
