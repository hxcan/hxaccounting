package com.stupidbeauty.hxaccounting.ui;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.stupidbeauty.hxaccounting.R;
import com.stupidbeauty.hxaccounting.data.entity.Category;

import java.util.List;

/**
 * 分类网格 Adapter
 * 在快速记账页显示分类列表
 */
public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.ViewHolder> {

    private final List<Category> categories;
    private long selectedCategoryId = -1L;
    private OnCategoryClickListener listener;

    public interface OnCategoryClickListener {
        void onCategoryClick(Category category);
    }

    public CategoryAdapter(List<Category> categories) {
        this.categories = categories;
    }

    public void setOnCategoryClickListener(OnCategoryClickListener listener) {
        this.listener = listener;
    }

    /**
     * 设置当前选中的分类（用于高亮）
     */
    public void setSelectedCategoryId(long categoryId) {
        long oldSelected = this.selectedCategoryId;
        this.selectedCategoryId = categoryId;
        // 局部刷新
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).getId() == oldSelected || categories.get(i).getId() == categoryId) {
                notifyItemChanged(i);
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category_chip, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Category category = categories.get(position);

        // 名称
        holder.tvName.setText(category.getName());

        // 图标（用 emoji 简化版，根据 icon 字段映射）
        holder.tvIcon.setText(iconToEmoji(category.getIcon()));

        // 背景色
        try {
            int color = Color.parseColor(category.getColor());
            holder.iconContainer.setCardBackgroundColor(color);
        } catch (Exception e) {
            holder.iconContainer.setCardBackgroundColor(Color.parseColor("#778CA3"));
        }

        // 选中态：加粗 + 不同背景
        if (category.getId() == selectedCategoryId) {
            holder.tvName.setTextColor(holder.itemView.getContext().getColor(R.color.taiji_text_primary));
            holder.tvName.setTextSize(13);
            holder.itemView.setBackgroundResource(R.color.taiji_divider);
        } else {
            holder.tvName.setTextColor(holder.itemView.getContext().getColor(R.color.taiji_text_secondary));
            holder.tvName.setTextSize(12);
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
        }

        // 点击事件
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCategoryClick(category);
            }
        });
    }

    @Override
    public int getItemCount() {
        return categories == null ? 0 : categories.size();
    }

    /**
     * 将 icon 字段映射为 emoji 图标
     */
    private String iconToEmoji(String icon) {
        if (icon == null) return "📦";
        switch (icon) {
            case "restaurant": return "🍜";
            case "bread": return "🥐";
            case "rice": return "🍚";
            case "noodles": return "🍜";
            case "takeout": return "🥡";
            case "tea": return "🧋";
            case "car": return "🚗";
            case "bus": return "🚌";
            case "taxi": return "🚕";
            case "fuel": return "⛽";
            case "shopping": return "🛒";
            case "daily": return "🧴";
            case "clothing": return "👕";
            case "home": return "🏠";
            case "rent": return "🏠";
            case "utility": return "💡";
            case "game": return "🎮";
            case "medical": return "💊";
            case "education": return "📚";
            case "baby": return "👶";
            case "income": return "💰";
            case "other": return "📦";
            default: return "📌";
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvIcon;
        final TextView tvName;
        final CardView iconContainer;

        ViewHolder(View itemView) {
            super(itemView);
            tvIcon = itemView.findViewById(R.id.tvCategoryIcon);
            tvName = itemView.findViewById(R.id.tvCategoryName);
            iconContainer = (CardView) itemView;
        }
    }
}
