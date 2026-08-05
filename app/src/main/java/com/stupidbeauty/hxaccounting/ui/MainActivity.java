package com.stupidbeauty.hxaccounting.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.lifecycle.LiveData;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.stupidbeauty.hxaccounting.R;
import com.stupidbeauty.hxaccounting.data.entity.Account;
import com.stupidbeauty.hxaccounting.data.repository.AccountRepository;

import java.util.List;

/**
 * 主页面
 * 1. 顶部 AppBar（标题"太极记账"）
 * 2. 当前账本切换栏（点击切换 / 长按管理）
 * 3. 右下角 FAB 按钮：点击进入快速记账页面（B4 核心）
 *
 * 后续 B5 任务会在此处接入流水列表（RecyclerView）
 */
public class MainActivity extends AppCompatActivity {

    private TextView tvCurrentAccountName;
    private TextView tvCurrentAccountIcon;
    private CardView currentAccountIconContainer;
    private View accountBar;
    private TextView btnSwitchAccount;
    private TextView btnManageAccounts;
    private AccountRepository accountRepository;
    private LiveData<Account> currentAccountLive;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        accountRepository = new AccountRepository(this);

        bindViews();
        setupFab();
        setupAccountBar();
        observeCurrentAccount();
    }

    private void bindViews() {
        tvCurrentAccountName = findViewById(R.id.tvCurrentAccountName);
        tvCurrentAccountIcon = findViewById(R.id.tvCurrentAccountIcon);
        currentAccountIconContainer = findViewById(R.id.currentAccountIconContainer);
        accountBar = findViewById(R.id.accountBar);
        btnSwitchAccount = findViewById(R.id.btnSwitchAccount);
        btnManageAccounts = findViewById(R.id.btnManageAccounts);
    }

    private void setupFab() {
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);
        fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, QuickAddActivity.class);
            startActivity(intent);
        });
    }

    private void setupAccountBar() {
        // 点击"切换"按钮 → 弹出账本列表
        btnSwitchAccount.setOnClickListener(v -> showAccountSwitcher(v));
        // 点击"管理"按钮 → 进入账本管理页
        btnManageAccounts.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AccountsActivity.class);
            startActivity(intent);
        });
        // 点击账本栏 → 也弹出切换菜单
        accountBar.setOnClickListener(v -> showAccountSwitcher(accountBar));
    }

    private void observeCurrentAccount() {
        // 观察当前账本（LiveData 自动响应）
        currentAccountLive = accountRepository.getCurrentAccount();
        if (currentAccountLive != null) {
            currentAccountLive.observe(this, this::updateCurrentAccountDisplay);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从 AccountsActivity 切回来时重新查询
        long id = accountRepository.getCurrentAccountIdSync();
        if (id != -1L) {
            accountRepository.getAccountById(id).observe(this, this::updateCurrentAccountDisplay);
        }
    }

    /**
     * 弹出账本切换菜单（PopupMenu）
     */
    private void showAccountSwitcher(View anchor) {
        LiveData<List<Account>> accountsLive = accountRepository.getActiveAccounts();
        accountsLive.observe(this, accounts -> {
            if (accounts == null || accounts.isEmpty()) {
                Toast.makeText(this, "还没有账本，请先创建", Toast.LENGTH_SHORT).show();
                return;
            }
            PopupMenu popup = new PopupMenu(this, anchor);
            long currentId = accountRepository.getCurrentAccountIdSync();
            for (int i = 0; i < accounts.size(); i++) {
                Account acc = accounts.get(i);
                String name = acc.getName();
                if (acc.getId() == currentId) {
                    name = "✓ " + name;
                }
                popup.getMenu().add(0, i, i, name);
            }
            popup.getMenu().add(0, 999, 999, "+ 管理账本...");
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 999) {
                    Intent intent = new Intent(MainActivity.this, AccountsActivity.class);
                    startActivity(intent);
                    return true;
                }
                Account selected = accounts.get(item.getItemId());
                accountRepository.setCurrentAccountId(selected.getId());
                updateCurrentAccountDisplay(selected);
                Toast.makeText(this, "已切换到：" + selected.getName(), Toast.LENGTH_SHORT).show();
                return true;
            });
            popup.show();
        });
    }

    /**
     * 更新当前账本显示
     */
    private void updateCurrentAccountDisplay(Account account) {
        if (account == null) {
            tvCurrentAccountName.setText("未选择账本");
            tvCurrentAccountIcon.setText("📒");
            try {
                currentAccountIconContainer.setCardBackgroundColor(Color.parseColor("#778CA3"));
            } catch (Exception e) {
                // ignore
            }
            return;
        }
        tvCurrentAccountName.setText(account.getName());
        // 智能 emoji
        tvCurrentAccountIcon.setText(iconToEmoji(account.getName(), account.getType()));
        try {
            currentAccountIconContainer.setCardBackgroundColor(Color.parseColor(account.getColor()));
        } catch (Exception e) {
            currentAccountIconContainer.setCardBackgroundColor(Color.parseColor("#FF6B6B"));
        }
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (accountRepository != null) {
            accountRepository.shutdown();
        }
    }
}
