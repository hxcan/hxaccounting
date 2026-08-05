package com.stupidbeauty.hxaccounting.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.stupidbeauty.hxaccounting.R;
import com.stupidbeauty.hxaccounting.data.entity.Account;
import com.stupidbeauty.hxaccounting.data.entity.Transaction;
import com.stupidbeauty.hxaccounting.data.repository.AccountRepository;
import com.stupidbeauty.hxaccounting.data.repository.TransactionRepository;

import java.util.List;

/**
 * 主页面
 * 1. 顶部 AppBar（标题"太极记账"）
 * 2. 当前账本切换栏（点击切换 / 长按管理）
 * 3. 当前账本的流水列表（B5）
 * 4. 右下角 FAB 按钮：点击进入快速记账页面（B4 核心）
 */
public class MainActivity extends AppCompatActivity {

    private TextView tvCurrentAccountName;
    private TextView tvCurrentAccountIcon;
    private CardView currentAccountIconContainer;
    private View accountBar;
    private TextView btnSwitchAccount;
    private TextView btnManageAccounts;
    private LinearLayout emptyView;
    private RecyclerView rvTransactions;
    private TransactionAdapter transactionAdapter;
    private TransactionRepository transactionRepository;
    private AccountRepository accountRepository;
    private LiveData<Account> currentAccountLive;
    private LiveData<List<Transaction>> currentTransactionsLive;
    private long currentAccountIdShown = -1L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        accountRepository = new AccountRepository(this);
        transactionRepository = new TransactionRepository(this);

        bindViews();
        setupFab();
        setupAccountBar();
        setupTransactionList();
        observeCurrentAccount();
    }

    private void bindViews() {
        tvCurrentAccountName = findViewById(R.id.tvCurrentAccountName);
        tvCurrentAccountIcon = findViewById(R.id.tvCurrentAccountIcon);
        currentAccountIconContainer = findViewById(R.id.currentAccountIconContainer);
        accountBar = findViewById(R.id.accountBar);
        btnSwitchAccount = findViewById(R.id.btnSwitchAccount);
        btnManageAccounts = findViewById(R.id.btnManageAccounts);
        rvTransactions = findViewById(R.id.rvTransactions);
        emptyView = findViewById(R.id.emptyView);
    }

    private void setupFab() {
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);
        fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, QuickAddActivity.class);
            startActivity(intent);
        });
    }

    private void setupAccountBar() {
        btnSwitchAccount.setOnClickListener(v -> showAccountSwitcher(v));
        btnManageAccounts.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AccountsActivity.class);
            startActivity(intent);
        });
        accountBar.setOnClickListener(v -> showAccountSwitcher(accountBar));
    }

    private void setupTransactionList() {
        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        transactionAdapter = new TransactionAdapter(this);
        rvTransactions.setAdapter(transactionAdapter);
    }

    private void observeCurrentAccount() {
        currentAccountLive = accountRepository.getCurrentAccount();
        if (currentAccountLive != null) {
            currentAccountLive.observe(this, this::onCurrentAccountChanged);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        long id = accountRepository.getCurrentAccountIdSync();
        if (id != -1L) {
            accountRepository.getAccountById(id).observe(this, this::onCurrentAccountChanged);
        }
    }

    private void onCurrentAccountChanged(Account account) {
        updateCurrentAccountDisplay(account);
        loadTransactionsFor(account);
    }

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
        tvCurrentAccountIcon.setText(iconToEmoji(account.getName(), account.getType()));
        try {
            currentAccountIconContainer.setCardBackgroundColor(Color.parseColor(account.getColor()));
        } catch (Exception e) {
            currentAccountIconContainer.setCardBackgroundColor(Color.parseColor("#FF6B6B"));
        }
    }

    private void loadTransactionsFor(Account account) {
        if (account == null) {
            currentAccountIdShown = -1L;
            transactionAdapter.setTransactions(null);
            rvTransactions.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            return;
        }
        if (account.getId() == currentAccountIdShown) {
            return;
        }
        currentAccountIdShown = account.getId();
        if (currentTransactionsLive != null) {
            currentTransactionsLive.removeObservers(this);
        }
        currentTransactionsLive = transactionRepository.getByAccountId(account.getId(), 100, 0);
        currentTransactionsLive.observe(this, transactions -> {
            if (transactions == null || transactions.isEmpty()) {
                transactionAdapter.setTransactions(null);
                rvTransactions.setVisibility(View.GONE);
                emptyView.setVisibility(View.VISIBLE);
            } else {
                transactionAdapter.setTransactions(transactions);
                rvTransactions.setVisibility(View.VISIBLE);
                emptyView.setVisibility(View.GONE);
            }
        });
    }

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
                Toast.makeText(this, "已切换到：" + selected.getName(), Toast.LENGTH_SHORT).show();
                return true;
            });
            popup.show();
        });
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
