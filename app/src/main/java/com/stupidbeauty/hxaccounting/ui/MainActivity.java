package com.stupidbeauty.hxaccounting.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.stupidbeauty.hxaccounting.R;
import com.stupidbeauty.hxaccounting.budget.BudgetCardBinder;
import com.stupidbeauty.hxaccounting.budget.BudgetViewModel;
import com.stupidbeauty.hxaccounting.data.entity.Account;
import com.stupidbeauty.hxaccounting.data.entity.Transaction;
import com.stupidbeauty.hxaccounting.data.repository.AccountRepository;
import com.stupidbeauty.hxaccounting.data.repository.TransactionRepository;
import com.stupidbeauty.hxaccounting.utils.FileLogger;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 主页面
 * 1. 顶部 AppBar（标题"太极记账"）
 * 2. 当前账本切换栏（点击切换 / 长按管理）
 * 3. 预算状态卡片（C2 新增：今日剩余预算）
 * 4. 合计卡片：今日/本周/本月支出 + 本月收入
 * 5. 当前账本的流水列表（B5）
 * 6. 右下角 FAB 按钮：点击进入快速记账页面（B4 核心）
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private TextView tvCurrentAccountName;
    private TextView tvCurrentAccountIcon;
    private CardView currentAccountIconContainer;
    private View accountBar;
    private TextView btnSwitchAccount;
    private TextView btnManageAccounts;
    private TextView tvTodayExpense;
    private TextView tvWeekExpense;
    private TextView tvMonthExpense;
    private TextView tvMonthIncome;
    private LinearLayout emptyView;
    private RecyclerView rvTransactions;
    private TransactionAdapter transactionAdapter;
    private TransactionRepository transactionRepository;
    private AccountRepository accountRepository;
    private LiveData<Account> currentAccountLive;
    private LiveData<List<Transaction>> currentTransactionsLive;
    private long currentAccountIdShown = -1L;

    // C2 预算相关
    private BudgetViewModel budgetViewModel;
    private MaterialCardView budgetStatusCard;
    private BudgetCardBinder budgetCardBinder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        accountRepository = new AccountRepository(this);
        transactionRepository = new TransactionRepository(this);

        // C2: 初始化预算 ViewModel 和卡片
        budgetViewModel = new ViewModelProvider(this).get(BudgetViewModel.class);

        bindViews();
        // C2: 嵌入预算卡片到合计卡片上方
        injectBudgetCard();
        setupFab();
        setupAccountBar();
        setupTransactionList();
        observeCurrentAccount();

        // C2 fix: 同步初始化预算账本 ID，避免首次启动时预算卡片显示"选择账本后显示"
        // 必须在 observeCurrentAccount() 之后、budgetCardBinder.bind() 之前调用，
        // 这样 BudgetViewModel.rebuildBudgetLive() 在首次渲染时就能拿到账本 ID。
        long initialAccountId = accountRepository.getCurrentAccountIdSync();
        FileLogger.i(TAG, "C2 fix: onCreate 同步读取当前账本 ID = " + initialAccountId);
        if (initialAccountId != -1L) {
            budgetViewModel.setCurrentAccountId(initialAccountId);
            FileLogger.i(TAG, "C2 fix: 预算卡片已同步初始化账本 ID = " + initialAccountId);
        } else {
            FileLogger.w(TAG, "C2 fix: 当前账本 ID 为 -1L（首次启动或未创建账本），预算卡片将保持占位文案");
        }

        // C2: 绑定预算卡片
        if (budgetCardBinder != null) {
            budgetCardBinder.bind();
        }

        FileLogger.i(TAG, "onCreate 完成，初始化完毕");
    }

    private void bindViews() {
        tvCurrentAccountName = findViewById(R.id.tvCurrentAccountName);
        tvCurrentAccountIcon = findViewById(R.id.tvCurrentAccountIcon);
        currentAccountIconContainer = findViewById(R.id.currentAccountIconContainer);
        accountBar = findViewById(R.id.accountBar);
        btnSwitchAccount = findViewById(R.id.btnSwitchAccount);
        btnManageAccounts = findViewById(R.id.btnManageAccounts);
        tvTodayExpense = findViewById(R.id.tvTodayExpense);
        tvWeekExpense = findViewById(R.id.tvWeekExpense);
        tvMonthExpense = findViewById(R.id.tvMonthExpense);
        tvMonthIncome = findViewById(R.id.tvMonthIncome);
        rvTransactions = findViewById(R.id.rvTransactions);
        emptyView = findViewById(R.id.emptyView);
    }

    /**
     * C2: 把预算状态卡片动态注入到合计卡片上方
     */
    private void injectBudgetCard() {
        // 通过 inflate 创建预算卡片
        View budgetCardView = LayoutInflater.from(this)
                .inflate(R.layout.card_budget_status, null, false);
        budgetStatusCard = (MaterialCardView) budgetCardView;

        // 找到合计卡片的父容器，把预算卡片插入到它前面
        View summaryCard = findViewById(R.id.summaryCard);
        if (summaryCard != null && summaryCard.getParent() instanceof LinearLayout) {
            LinearLayout parent = (LinearLayout) summaryCard.getParent();
            int index = parent.indexOfChild(summaryCard);
            parent.addView(budgetStatusCard, index);
        }

        // 创建绑定器
        budgetCardBinder = new BudgetCardBinder(this, this, budgetStatusCard, budgetViewModel);
        FileLogger.i(TAG, "预算卡片已注入");
    }

    private void setupFab() {
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);
        fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, QuickAddActivity.class);
            startActivity(intent);
            FileLogger.d(TAG, "点击 FAB，进入快速记账页面");
        });
    }

    private void setupAccountBar() {
        btnSwitchAccount.setOnClickListener(v -> showAccountSwitcher(v));
        btnManageAccounts.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AccountsActivity.class);
            startActivity(intent);
            FileLogger.d(TAG, "点击管理按钮，进入账本管理页面");
        });
        accountBar.setOnClickListener(v -> showAccountSwitcher(accountBar));
    }

    private void setupTransactionList() {
        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        transactionAdapter = new TransactionAdapter(this);
        rvTransactions.setAdapter(transactionAdapter);
        FileLogger.d(TAG, "流水列表初始化完成");
    }

    private void observeCurrentAccount() {
        FileLogger.i(TAG, "【观察】observeCurrentAccount 开始");
        currentAccountLive = accountRepository.getCurrentAccount();
        if (currentAccountLive != null) {
            FileLogger.i(TAG, "【观察】注册 LiveData 观察者");
            currentAccountLive.observe(this, this::onCurrentAccountChanged);
        } else {
            FileLogger.w(TAG, "【观察】currentAccountLive 为 null！");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        FileLogger.i(TAG, "onResume");
        long id = accountRepository.getCurrentAccountIdSync();
        FileLogger.d(TAG, "onResume 读取当前账本 ID = " + id);
        if (id != -1L) {
            FileLogger.d(TAG, "onResume 重新观察账本 ID = " + id);
            accountRepository.getAccountById(id).observe(this, this::onCurrentAccountChanged);
        }
    }

    private void onCurrentAccountChanged(Account account) {
        if (account == null) {
            FileLogger.d(TAG, "【回调】onCurrentAccountChanged: account = null");
        } else {
            FileLogger.d(TAG, "【回调】onCurrentAccountChanged: account.id = " + account.getId() + ", name = " + account.getName());
        }
        updateCurrentAccountDisplay(account);
        loadTransactionsFor(account);
        loadSummaryFor(account);
        // C2: 通知预算 ViewModel 当前账本已切换
        if (account != null && account.getId() != currentAccountIdShown) {
            budgetViewModel.setCurrentAccountId(account.getId());
            FileLogger.i(TAG, "C2: 预算 ViewModel 已切换账本 ID = " + account.getId());
        }
    }

    private void updateCurrentAccountDisplay(Account account) {
        FileLogger.d(TAG, "【显示】updateCurrentAccountDisplay 开始，account = " + (account == null ? "null" : account.getName()));
        if (account == null) {
            tvCurrentAccountName.setText("未选择账本");
            tvCurrentAccountIcon.setText("📒");
            try {
                currentAccountIconContainer.setCardBackgroundColor(Color.parseColor("#778CA3"));
            } catch (Exception e) {
                // ignore
            }
            FileLogger.d(TAG, "【显示】更新为「未选择账本」");
            return;
        }

        tvCurrentAccountName.setText(account.getName());
        tvCurrentAccountIcon.setText(iconToEmoji(account.getName(), account.getType()));
        try {
            currentAccountIconContainer.setCardBackgroundColor(Color.parseColor(account.getColor()));
        } catch (Exception e) {
            currentAccountIconContainer.setCardBackgroundColor(Color.parseColor("#FF6B6B"));
        }
        FileLogger.d(TAG, "【显示】更新为账本：「" + account.getName() + "」");
    }

    private void loadTransactionsFor(Account account) {
        FileLogger.d(TAG, "【流水】loadTransactionsFor 开始");
        if (account == null) {
            currentAccountIdShown = -1L;
            transactionAdapter.setTransactions(null);
            rvTransactions.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            return;
        }

        if (account.getId() == currentAccountIdShown) {
            FileLogger.d(TAG, "【流水】账本 ID 未变化，跳过刷新");
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
                FileLogger.d(TAG, "【流水】账本「" + account.getName() + "」无流水数据");
            } else {
                transactionAdapter.setTransactions(transactions);
                rvTransactions.setVisibility(View.VISIBLE);
                emptyView.setVisibility(View.GONE);
                FileLogger.d(TAG, "【流水】账本「" + account.getName() + "」加载了 " + transactions.size() + " 笔流水");
            }
        });
    }

    /**
     * 加载合计数据（今日/本周/本月支出 + 本月收入）
     * 账本变化时自动重新订阅。
     */
    private void loadSummaryFor(Account account) {
        FileLogger.d(TAG, "【合计】loadSummaryFor 开始");
        if (account == null) {
            // 无账本时清空显示
            tvTodayExpense.setText("¥0.00");
            tvWeekExpense.setText("¥0.00");
            tvMonthExpense.setText("¥0.00");
            tvMonthIncome.setText("¥0.00");
            return;
        }

        long accountId = account.getId();
        TimeRange today = todayRange();
        TimeRange week = weekRange();
        TimeRange month = monthRange();

        // 今日支出
        transactionRepository.getTodayTotal(accountId, today.start, today.end)
                .observe(this, value -> updateAmount(tvTodayExpense, value, "今日"));
        // 本周支出
        transactionRepository.getWeekTotal(accountId, week.start)
                .observe(this, value -> updateAmount(tvWeekExpense, value, "本周"));
        // 本月支出
        transactionRepository.getMonthTotal(accountId, month.start, month.end)
                .observe(this, value -> updateAmount(tvMonthExpense, value, "本月"));
        // 本月收入
        transactionRepository.getMonthIncome(accountId, month.start, month.end)
                .observe(this, value -> updateAmount(tvMonthIncome, value, "本月收入"));
    }

    private void updateAmount(TextView tv, Double value, String label) {
        double amount = value == null ? 0.0 : value;
        tv.setText(String.format(Locale.getDefault(), "¥%.2f", amount));
        FileLogger.d(TAG, "【合计】" + label + " = ¥" + amount);
    }

    /** 今天 00:00:00 ~ 明天 00:00:00 */
    private TimeRange todayRange() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_YEAR, 1);
        long end = cal.getTimeInMillis();
        return new TimeRange(start, end);
    }

    /** 本周一 00:00:00 ~ 下周一 00:00:00 */
    private TimeRange weekRange() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        long start = cal.getTimeInMillis();
        cal.add(Calendar.WEEK_OF_YEAR, 1);
        long end = cal.getTimeInMillis();
        return new TimeRange(start, end);
    }

    /** 本月 1 号 00:00:00 ~ 下月 1 号 00:00:00 */
    private TimeRange monthRange() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTimeInMillis();
        cal.add(Calendar.MONTH, 1);
        long end = cal.getTimeInMillis();
        return new TimeRange(start, end);
    }

    private static class TimeRange {
        final long start;
        final long end;
        TimeRange(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }

    private void showAccountSwitcher(View anchor) {
        FileLogger.i(TAG, "【切换】showAccountSwitcher 被点击");
        LiveData<List<Account>> accountsLive = accountRepository.getActiveAccounts();
        accountsLive.observe(this, accounts -> {
            if (accounts == null || accounts.isEmpty()) {
                Toast.makeText(this, "还没有账本，请先创建", Toast.LENGTH_SHORT).show();
                FileLogger.w(TAG, "【切换】没有账本可选");
                return;
            }

            FileLogger.d(TAG, "【切换】找到 " + accounts.size() + " 个账本");
            PopupMenu popup = new PopupMenu(this, anchor);
            long currentId = accountRepository.getCurrentAccountIdSync();
            FileLogger.d(TAG, "【切换】当前账本 ID = " + currentId);

            for (int i = 0; i < accounts.size(); i++) {
                Account acc = accounts.get(i);
                String displayName = (acc.getId() == currentId ? "✓ " : "   ") + acc.getName();
                popup.getMenu().add(0, i, i, displayName);
            }
            popup.getMenu().add(0, 999, 999, "管理账本...");

            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 999) {
                    Intent intent = new Intent(MainActivity.this, AccountsActivity.class);
                    startActivity(intent);
                    return true;
                }

                Account selected = accounts.get(item.getItemId());
                FileLogger.i(TAG, "【切换】用户选择了账本：「" + selected.getName() + "」(ID=" + selected.getId() + ")");
                FileLogger.i(TAG, "【切换】调用 setCurrentAccountId() 修改当前账本");

                accountRepository.setCurrentAccountId(selected.getId());

                FileLogger.i(TAG, "【切换】setCurrentAccountId() 调用完毕，弹出 Toast");
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
        FileLogger.i(TAG, "onDestroy，MainActivity 销毁");
        if (accountRepository != null) {
            accountRepository.shutdown();
        }
    }
}
