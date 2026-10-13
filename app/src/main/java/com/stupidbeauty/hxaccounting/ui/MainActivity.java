package com.stupidbeauty.hxaccounting.ui;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
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
import com.stupidbeauty.hxaccounting.data.entity.TransactionStatistics;
import com.stupidbeauty.hxaccounting.data.entity.TransactionWithCategory;
import com.stupidbeauty.hxaccounting.data.repository.AccountRepository;
import com.stupidbeauty.hxaccounting.data.repository.TransactionRepository;
import com.stupidbeauty.hxaccounting.export.BillPdfExporter;
import com.stupidbeauty.hxaccounting.export.PdfFileSaver;
import com.stupidbeauty.hxaccounting.utils.FileLogger;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 主页面
 *
 * <p>v3.3 修复 (#861640737779 v3-final):
 * 真正的根因是 onCurrentAccountChanged 被多次调用，currentAccountIdShown 字段在 if 检查前
 * 已被 loadTransactionsFor 修改，导致第二次回调时 if 永远不进。
 *
 * <p>修复方案：把 currentAccountIdShown 的检查移除，改用 SharedPreferences 同步值对比。
 * 这样多次回调时，每次都能正确判断是否需要通知预算 ViewModel。
 *
 * <p>feat／edit-transaction-move-account：增加流水点击监听，跳转到编辑模式
 *
 * <p>feat／export-pdf-ui (#918517008461)：
 * 长按账本切换栏触发「导出 PDF」功能。复用现有 accountBar，零 UI 改动。
 * 流程：数据层同步查询 → 引擎渲染 → 保存到 Download → 弹成功对话框。
 *
 * @author 未来姐姐
 * @since 2026-08-06
 * @updated 2026-08-09 v3.3 真正的 bug 修复
 * @updated 2026-10-13 增加 PDF 导出（长按账本栏）
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

    // PDF 导出（#918517008461）
    private ExecutorService pdfExportExecutor; // 单线程后台执行 PDF 导出

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
        // PDF 导出：单线程后台任务
        pdfExportExecutor = Executors.newSingleThreadExecutor();

        // C2: 初始化预算 ViewModel 和卡片
        budgetViewModel = new ViewModelProvider(this).get(BudgetViewModel.class);

        bindViews();
        // C2: 嵌入预算卡片到合计卡片上方
        injectBudgetCard();
        setupFab();
        setupAccountBar();
        setupTransactionList();
        observeCurrentAccount();

        // C2 fix: 同步初始化预算账本 ID
        long initialAccountId = accountRepository.getCurrentAccountIdSync();
        FileLogger.i(TAG, "C2 fix: onCreate 同步读取当前账本 ID = " + initialAccountId);
        if (initialAccountId != -1L) {
            budgetViewModel.setCurrentAccountId(initialAccountId);
            FileLogger.i(TAG, "C2 fix: 预算卡片已同步初始化账本 ID = " + initialAccountId);
        } else {
            FileLogger.w(TAG, "C2 fix: 当前账本 ID 为 -1L");
        }

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

    private void injectBudgetCard() {
        View budgetCardView = LayoutInflater.from(this)
                .inflate(R.layout.card_budget_status, null, false);
        budgetStatusCard = (MaterialCardView) budgetCardView;

        View summaryCard = findViewById(R.id.summaryCard);
        if (summaryCard != null && summaryCard.getParent() instanceof LinearLayout) {
            LinearLayout parent = (LinearLayout) summaryCard.getParent();
            int index = parent.indexOfChild(summaryCard);
            parent.addView(budgetStatusCard, index);
        }

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
        // #918517008461：长按账本栏 → 导出当前账本为 PDF
        // 零 UI 改动：复用现有 accountBar 区域
        accountBar.setOnLongClickListener(v -> {
            FileLogger.i(TAG, "【导出】长按账本栏，触发 PDF 导出");
            showExportPdfMenu(v);
            return true;
        });
        FileLogger.i(TAG, "账本栏长按 → 导出 PDF 已注册");
    }

    /**
     * 显示导出 PDF 菜单
     * #918517008461 最小化方案：长按账本栏 → 弹出 PopupMenu
     */
    private void showExportPdfMenu(View anchor) {
        long currentId = accountRepository.getCurrentAccountIdSync();
        if (currentId == -1L) {
            Toast.makeText(this, "请先选择一个账本", Toast.LENGTH_SHORT).show();
            return;
        }

        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 1, "导出 PDF 账单");
        popup.getMenu().add(0, 2, 2, "取消");

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                startExportPdf();
                return true;
            }
            return false;
        });
        popup.show();
    }

    /**
     * 启动 PDF 导出流程
     * #918517008461
     */
    private void startExportPdf() {
        long currentId = accountRepository.getCurrentAccountIdSync();
        if (currentId == -1L) {
            Toast.makeText(this, "请先选择一个账本", Toast.LENGTH_SHORT).show();
            return;
        }

        // 显示进度对话框
        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle("导出账单")
                .setMessage("正在生成 PDF，请稍候...")
                .setCancelable(false)
                .create();
        progressDialog.show();

        // 后台线程执行：查询数据 + 渲染 PDF + 保存
        pdfExportExecutor.execute(() -> {
            try {
                FileLogger.i(TAG, "【导出】开始导出账本 ID = " + currentId);

                // 1. 获取账本信息
                Account account = null;
                List<Account> allAccounts = accountRepository.getActiveAccountsSync();
                if (allAccounts != null) {
                    for (Account acc : allAccounts) {
                        if (acc.getId() == currentId) {
                            account = acc;
                            break;
                        }
                    }
                }
                if (account == null) {
                    throw new Exception("账本不存在");
                }

                // 2. 查询流水（含分类名，PDF 引擎需要）
                List<TransactionWithCategory> transactions =
                        transactionRepository.getTransactionsWithCategoryByAccountIdSync(currentId);

                // 3. 查询统计
                TransactionStatistics statistics =
                        transactionRepository.getStatisticsByAccountIdSync(currentId);

                FileLogger.i(TAG, "【导出】查询完成：流水 " + transactions.size() + " 笔，统计 OK");

                // 4. 渲染 PDF
                BillPdfExporter exporter = new BillPdfExporter(this);
                File cacheDir = new File(getCacheDir(), "pdf_export");
                if (!cacheDir.exists()) cacheDir.mkdirs();
                File tempFile = new File(cacheDir, "temp_export.pdf");
                exporter.export(account, transactions, statistics, tempFile);
                FileLogger.i(TAG, "【导出】PDF 渲染完成：" + tempFile.length() + " 字节");

                // 5. 保存到 Download
                PdfFileSaver saver = new PdfFileSaver(this);
                Uri savedUri = saver.saveToDownload(tempFile, account.getName());
                FileLogger.i(TAG, "【导出】PDF 保存成功：URI = " + savedUri);

                // 6. 清理临时文件
                if (tempFile.exists()) {
                    tempFile.delete();
                }

                // 7. 主线程显示成功对话框
                final Uri finalUri = savedUri;
                final String finalName = account.getName();
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    showExportSuccessDialog(finalUri, finalName);
                });

            } catch (Exception e) {
                FileLogger.e(TAG, "【导出】PDF 导出失败", e);
                final String errorMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    showExportErrorDialog(errorMsg);
                });
            }
        });
    }

    /**
     * 显示导出成功对话框
     */
    private void showExportSuccessDialog(Uri uri, String accountName) {
        new AlertDialog.Builder(this)
                .setTitle("导出成功")
                .setMessage("账本「" + accountName + "」已导出为 PDF 账单！\n\n保存路径：\n" + uri.getPath())
                .setPositiveButton("打开", (dialog, which) -> {
                    try {
                        Intent openIntent = new Intent(Intent.ACTION_VIEW);
                        openIntent.setDataAndType(uri, "application/pdf");
                        openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(openIntent, "打开 PDF"));
                    } catch (Exception e) {
                        Toast.makeText(this, "未找到 PDF 阅读器", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("分享", (dialog, which) -> {
                    try {
                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType("application/pdf");
                        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(shareIntent, "分享 PDF"));
                    } catch (Exception e) {
                        Toast.makeText(this, "分享失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    /**
     * 显示导出失败对话框
     */
    private void showExportErrorDialog(String errorMsg) {
        new AlertDialog.Builder(this)
                .setTitle("导出失败")
                .setMessage("PDF 导出失败：\n" + errorMsg)
                .setPositiveButton("重试", (dialog, which) -> startExportPdf())
                .setNegativeButton("取消", null)
                .show();
    }

    private void setupTransactionList() {
        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        transactionAdapter = new TransactionAdapter(this);
        // feat／edit-transaction-move-account：流水点击进入编辑模式
        transactionAdapter.setOnTransactionClickListener(new TransactionAdapter.OnTransactionClickListener() {
            @Override
            public void onTransactionClick(Transaction transaction) {
                Intent intent = new Intent(MainActivity.this, QuickAddActivity.class);
                intent.putExtra(QuickAddActivity.EXTRA_TRANSACTION_ID, transaction.getId());
                startActivity(intent);
                FileLogger.d(TAG, "点击流水 ID=" + transaction.getId() + "，进入编辑模式");
            }

            @Override
            public void onTransactionLongClick(Transaction transaction, View view) {
                // 长按后续可扩展：删除 ／ 复制等
                FileLogger.d(TAG, "长按流水 ID=" + transaction.getId() + "（暂未实现）");
            }
        });
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
        // v3.3 修复：onResume 不再 observe getAccountById，避免重复回调
        // 账本切换后 AccountRepository 的 currentAccountLive 会自动推送新值
    }

    private void onCurrentAccountChanged(Account account) {
        FileLogger.i(TAG, "===== onCurrentAccountChanged 入口 =====");
        if (account == null) {
            FileLogger.w(TAG, "  account 为 null，跳过");
            return;
        }
        FileLogger.i(TAG, "  account.id = " + account.getId() + ", name = " + account.getName());

        updateCurrentAccountDisplay(account);
        loadTransactionsFor(account);
        loadSummaryFor(account);

        long currentBudgetAccountId = budgetViewModel.getCurrentAccountId();
        long sharedPrefsAccountId = accountRepository.getCurrentAccountIdSync();
        FileLogger.i(TAG, "  BudgetViewModel.getCurrentAccountId() = " + currentBudgetAccountId
                + ", SharedPreferences ID = " + sharedPrefsAccountId
                + ", 当前 account.id = " + account.getId());

        if (account.getId() != currentBudgetAccountId) {
            FileLogger.i(TAG, "C2: 预算 ViewModel 切换账本 ID = " + account.getId()
                    + " (旧=" + currentBudgetAccountId + ")");
            budgetViewModel.setCurrentAccountId(account.getId());
        } else {
            FileLogger.w(TAG, "C2: **未**调用 budgetViewModel.setCurrentAccountId() "
                    + "— account.id=" + account.getId()
                    + " 已等于 BudgetViewModel 当前 ID=" + currentBudgetAccountId);
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
            transactionAdapter.setTransactions(null);
            rvTransactions.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            return;
        }

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

    private void loadSummaryFor(Account account) {
        FileLogger.d(TAG, "【合计】loadSummaryFor 开始");
        if (account == null) {
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

        transactionRepository.getTodayTotal(accountId, today.start, today.end)
                .observe(this, value -> updateAmount(tvTodayExpense, value, "今日"));
        transactionRepository.getWeekTotal(accountId, week.start)
                .observe(this, value -> updateAmount(tvWeekExpense, value, "本周"));
        transactionRepository.getMonthTotal(accountId, month.start, month.end)
                .observe(this, value -> updateAmount(tvMonthExpense, value, "本月"));
        transactionRepository.getMonthIncome(accountId, month.start, month.end)
                .observe(this, value -> updateAmount(tvMonthIncome, value, "本月收入"));
    }

    private void updateAmount(TextView tv, Double value, String label) {
        double amount = value == null ? 0.0 : value;
        tv.setText(String.format(Locale.getDefault(), "¥%.2f", amount));
        FileLogger.d(TAG, "【合计】" + label + " = ¥" + amount);
    }

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
        FileLogger.i(TAG, "onDestroy，MainActivity 销毁");
        if (accountRepository != null) {
            accountRepository.shutdown();
        }
        if (pdfExportExecutor != null) {
            pdfExportExecutor.shutdown();
        }
    }
}
