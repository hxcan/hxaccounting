package com.stupidbeauty.hxaccounting.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.stupidbeauty.hxaccounting.R;
import com.stupidbeauty.hxaccounting.data.entity.Transaction;
import com.stupidbeauty.hxaccounting.data.entity.TransactionType;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 流水列表 Adapter
 * 显示当前账本的流水，按时间倒序
 */
public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {

    private final List<Transaction> transactions;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd", Locale.getDefault());

    private OnTransactionClickListener listener;

    public interface OnTransactionClickListener {
        void onTransactionClick(Transaction transaction);
        void onTransactionLongClick(Transaction transaction, View view);
    }

    public TransactionAdapter(List<Transaction> transactions) {
        this.transactions = transactions;
    }

    public void setOnTransactionClickListener(OnTransactionClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Transaction t = transactions.get(position);

        // 分类名（暂时用 enum 显示）
        String categoryName = categoryNameFromType(t.getType());
        holder.tvCategoryName.setText(categoryName);
        holder.tvCategoryIcon.setText(iconForType(t.getType()));

        // 颜色（暂时用 expense/income 区分）
        try {
            int color = Color.parseColor("#FF6B6B");
            if (t.getTransactionType() == TransactionType.INCOME) {
                color = Color.parseColor("#FF26DE81");
            }
            holder.categoryIconContainer.setCardBackgroundColor(color);
        } catch (Exception e) {
            // ignore
        }

        // 备注
        String description = t.getDescription();
        if (description == null || description.isEmpty()) {
            holder.tvDescription.setText(categoryName);
        } else {
            holder.tvDescription.setText(description);
        }

        // 时间
        long time = t.getTransactionTime();
        if (isToday(time)) {
            holder.tvTimeAgo.setText(timeFormat.format(new Date(time)));
        } else if (isYesterday(time)) {
            holder.tvTimeAgo.setText("昨天 " + timeFormat.format(new Date(time)));
        } else {
            holder.tvTimeAgo.setText(dateFormat.format(new Date(time)));
        }

        // 金额
        TransactionType type = t.getTransactionType();
        String sign = type == TransactionType.EXPENSE ? "-" : "+";
        holder.tvAmount.setText(sign + String.format(Locale.getDefault(), "%.2f", t.getAmount()));
        if (type == TransactionType.EXPENSE) {
            holder.tvAmount.setTextColor(Color.parseColor("#FFE53935"));
        } else {
            holder.tvAmount.setTextTextColorSafe(Color.parseColor("#FF43A047"));
        }

        // 点击事件
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onTransactionClick(t);
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onTransactionLongClick(t, v);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return transactions == null ? 0 : transactions.size();
    }

    private boolean isToday(long time) {
        java.util.Calendar now = java.util.Calendar.getInstance();
        java.util.Calendar that = java.util.Calendar.getInstance();
        that.setTimeInMillis(time);
        return now.get(java.util.Calendar.YEAR) == that.get(java.util.Calendar.YEAR)
                && now.get(java.util.Calendar.DAY_OF_YEAR) == that.get(java.util.Calendar.DAY_OF_YEAR);
    }

    private boolean isYesterday(long time) {
        java.util.Calendar now = java.util.Calendar.getInstance();
        java.util.Calendar that = java.util.Calendar.getInstance();
        that.setTimeInMillis(time);
        now.add(java.util.Calendar.DAY_OF_YEAR, -1);
        return now.get(java.util.Calendar.YEAR) == that.get(java.util.Calendar.YEAR)
                && now.get(java.util.Calendar.DAY_OF_YEAR) == that.get(java.util.Calendar.DAY_OF_YEAR);
    }

    private String categoryNameFromType(String type) {
        if ("EXPENSE".equals(type)) return "支出";
        if ("INCOME".equals(type)) return "收入";
        return "其他";
    }

    private String iconForType(String type) {
        if ("EXPENSE".equals(type)) return "💸";
        if ("INCOME".equals(type)) return "💰";
        return "📦";
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvCategoryName;
        final TextView tvCategoryIcon;
        final TextView tvDescription;
        final TextView tvTimeAgo;
        final TextView tvAmount;
        final CardView categoryIconContainer;

        ViewHolder(View itemView) {
            super(itemView);
            tvCategoryName = itemView.findViewById(R.id.tvCategoryName);
            tvCategoryIcon = itemView.findViewById(R.id.tvCategoryIcon);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvTimeAgo = itemView.findViewById(R.id.tvTimeAgo);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            categoryIconContainer = (CardView) itemView.findViewById(R.id.categoryIconContainer);
        }
    }
}
