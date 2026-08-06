package com.stupidbeauty.hxaccounting.ui;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.stupidbeauty.hxaccounting.R;
import com.stupidbeauty.hxaccounting.data.entity.Account;
import com.stupidbeauty.hxaccounting.data.entity.AccountType;

import java.util.ArrayList;
import java.util.List;

public class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.ViewHolder> {

    private final List<Account> accounts = new ArrayList<>();
    private long currentAccountId = -1L;
    private OnAccountClickListener listener;

    public interface OnAccountClickListener {
        void onAccountClick(Account account);
        void onMoreClick(Account account, View view);
    }

    public AccountAdapter(List<Account> accounts) {
        // 拷贝一份到内部可变列表（避免外部 list 变更影响）
        if (accounts != null) {
            this.accounts.addAll(accounts);
        }
    }

    /**
     * 用新数据替换内部列表，并通知 RecyclerView 刷新。
     * 修复 #859864944989：LiveData 回调时只调用 notifyDataSetChanged()
     * 但 adapter 内部的 accounts 引用还是第一次构造时传入的旧 List，
     * 导致新插入的账本不显示。
     */
    public void setAccounts(List<Account> newAccounts) {
        this.accounts.clear();
        if (newAccounts != null) {
            this.accounts.addAll(newAccounts);
        }
        notifyDataSetChanged();
    }

    public void setCurrentAccountId(long currentAccountId) {
        long old = this.currentAccountId;
        this.currentAccountId = currentAccountId;
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).getId() == old || accounts.get(i).getId() == currentAccountId) {
                notifyItemChanged(i);
            }
        }
    }

    public void setOnAccountClickListener(OnAccountClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_account, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Account account = accounts.get(position);

        holder.tvName.setText(account.getName());

        try {
            AccountType type = account.getAccountType();
            holder.tvType.setText(type.getDisplayName());
        } catch (Exception e) {
            holder.tvType.setText(account.getType());
        }

        holder.tvIcon.setText(iconToEmoji(account.getName(), account.getType()));

        try {
            int color = Color.parseColor(account.getColor());
            holder.iconContainer.setCardBackgroundColor(color);
        } catch (Exception e) {
            holder.iconContainer.setCardBackgroundColor(Color.parseColor("#FF6B6B"));
        }

        if (account.getId() == currentAccountId) {
            holder.tvCurrentBadge.setVisibility(View.VISIBLE);
        } else {
            holder.tvCurrentBadge.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAccountClick(account);
            }
        });
        holder.btnMore.setOnClickListener(v -> {
            if (listener != null) {
                listener.onMoreClick(account, v);
            }
        });
    }

    @Override
    public int getItemCount() {
        return accounts == null ? 0 : accounts.size();
    }

    private String iconToEmoji(String name, String type) {
        if (name == null) name = "";
        String n = name.toLowerCase();
        if (n.contains("教育") || n.contains("学习") || n.contains("书")) return "📚";
        if (n.contains("餐") || n.contains("吃") || n.contains("食") || n.contains("饭")) return "🍜";
        if (n.contains("交通") || n.contains("车") || n.contains("出行")) return "🚗";
        if (n.contains("购物") || n.contains("日用") || n.contains("买")) return "🛒";
        if (n.contains("房") || n.contains("租") || n.contains("家")) return "🏠";
        if (n.contains("娱乐") || n.contains("玩")) return "🎮";
        if (n.contains("医") || n.contains("健康")) return "💊";
        if (n.contains("育儿") || n.contains("孩子") || n.contains("宝宝")) return "👶";
        if (n.contains("工资") || n.contains("收入") || n.contains("薪")) return "💰";
        if (n.contains("投资") || n.contains("股票") || n.contains("基金")) return "📈";
        if (n.contains("零花")) return "🪙";
        if (n.contains("社交") || n.contains("人情")) return "🎁";
        if ("SAVINGS".equals(type)) return "🏦";
        if ("CREDIT".equals(type)) return "💳";
        return "📒";
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvIcon;
        final TextView tvName;
        final TextView tvType;
        final TextView tvCurrentBadge;
        final ImageButton btnMore;
        final CardView iconContainer;

        ViewHolder(View itemView) {
            super(itemView);
            tvIcon = itemView.findViewById(R.id.tvAccountIcon);
            tvName = itemView.findViewById(R.id.tvAccountName);
            tvType = itemView.findViewById(R.id.tvAccountType);
            tvCurrentBadge = itemView.findViewById(R.id.tvCurrentBadge);
            btnMore = itemView.findViewById(R.id.btnMore);
            iconContainer = (CardView) itemView;
        }
    }
}