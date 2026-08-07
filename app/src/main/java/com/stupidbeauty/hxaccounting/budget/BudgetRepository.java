package com.stupidbeauty.hxaccounting.budget;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Transformations;

import com.stupidbeauty.hxaccounting.data.dao.TransactionDao;
import com.stupidbeauty.hxaccounting.data.entity.Transaction;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * 预算数据仓库
 *
 * <p>负责：
 * <ol>
 *   <li>从 {@link TransactionDao} 读取窗口期内的支出</li>
 *   <li>实时读取"今日已花"</li>
 *   <li>组合成 {@link BudgetResult} 提供给 ViewModel</li>
 * </ol>
 *
 * <p>这是 BudgetCalculator 与数据库之间的桥梁。
 *
 * @author 未来姐姐
 * @since 2026-08-06
 * @updated 2026-08-08 适配 BudgetCalculator v2 新签名（补 LocalDate today 参数）
 */
public class BudgetRepository {

    private final TransactionDao transactionDao;
    private final ExecutorService ioExecutor;

    public BudgetRepository(TransactionDao transactionDao, ExecutorService ioExecutor) {
        this.transactionDao = transactionDao;
        this.ioExecutor = ioExecutor;
    }

    /**
     * 计算今日剩余预算（响应式版本）
     *
     * <p>数据流：
     * <pre>
     * TransactionDao (窗口期支出)
     *         ↓
     *   calculateDailyAvg
     *         ↓
     *   calculateDailyBudget(× rate)
     *         ↓
     *   combine with 今日已花
     *         ↓
     *     BudgetResult
     * </pre>
     *
     * @param accountId      账本 ID
     * @param windowSize     窗口大小（天），建议 7 或 30
     * @param rate           倍率（> 0）
     * @param excludeAnomaly 是否排除异常支出
     * @return LiveData<BudgetResult> 当流水变化时自动重算
     */
    public LiveData<BudgetResult> getBudgetLive(
            long accountId, int windowSize, double rate, boolean excludeAnomaly) {

        // 1. 窗口期的支出（用于算日均）
        long windowStartTime = getStartOfDay(System.currentTimeMillis() - windowSize * 24L * 3600 * 1000);
        LiveData<List<Transaction>> windowExpenses =
                transactionDao.getByAccountIdAndTimeRange(
                        accountId, windowStartTime, System.currentTimeMillis());

        // 2. 今日已花
        long todayStart = getStartOfDay(System.currentTimeMillis());
        long todayEnd = todayStart + 24L * 3600 * 1000;
        LiveData<Double> todaySpentLive =
                transactionDao.getTodayTotal(accountId, todayStart, todayEnd);

        // 3. 组合：用 switchMap 把"今日已花"和"窗口支出"组合成 BudgetResult
        MediatorLiveData<BudgetResult> result = new MediatorLiveData<>();

        // 缓存中间值（两个源都到达后才计算）
        final List<Transaction>[] cachedExpenses = new List[]{new ArrayList<>()};
        final Double[] cachedSpent = new Double[]{0.0};

        // v2 修复：取一次 now，预算计算用同一时间基准（避免 today 与 window 跨日漂移）
        final LocalDate today = LocalDate.now();

        Runnable recompute = () -> {
            List<ExpenseRecord> records = toExpenseRecords(cachedExpenses[0]);
            BudgetResult r = BudgetCalculator.calculateFromHistory(
                records, windowSize, rate, cachedSpent[0], today, excludeAnomaly);
            result.setValue(r);
        };

        result.addSource(windowExpenses, expenses -> {
            cachedExpenses[0] = expenses != null ? expenses : new ArrayList<>();
            recompute.run();
        });

        result.addSource(todaySpentLive, spent -> {
            cachedSpent[0] = spent != null ? spent : 0.0;
            recompute.run();
        });

        return result;
    }

    /**
     * 同步版本：一次性查询并返回结果
     *
     * <p>适用场景：
     * <ul>
     *   <li>手动刷新预算数据</li>
     *   <li>后台任务定时计算</li>
     * </ul>
     *
     * @param accountId      账本 ID
     * @param windowSize     窗口大小（天）
     * @param rate           倍率
     * @param excludeAnomaly 是否排除异常支出
     * @return BudgetResult
     */
    public BudgetResult getBudgetSync(
            long accountId, int windowSize, double rate, boolean excludeAnomaly) {

        long now = System.currentTimeMillis();
        long windowStart = getStartOfDay(now - windowSize * 24L * 3600 * 1000);
        long todayStart = getStartOfDay(now);
        long todayEnd = todayStart + 24L * 3600 * 1000;

        // v2 修复：用 now 转 LocalDate，与窗口期查询用同一时间基准
        final LocalDate today = LocalDate.now();

        // 同步查询（必须在 IO 线程调用）
        // 这里假设调用方已经在 IO 线程
        List<Transaction> expenses =
                getSync(transactionDao.getByAccountIdAndTimeRange(accountId, windowStart, now));
        Double todaySpent =
                getSync(transactionDao.getTodayTotal(accountId, todayStart, todayEnd));

        List<ExpenseRecord> records = toExpenseRecords(expenses);
        return BudgetCalculator.calculateFromHistory(
                records, windowSize, rate, todaySpent != null ? todaySpent : 0.0, today, excludeAnomaly);
    }

    // ============ 工具方法 ============

    /**
     * 将 Transaction 实体列表转为 BudgetCalculator 的 ExpenseRecord 列表
     */
    private List<ExpenseRecord> toExpenseRecords(List<Transaction> transactions) {
        List<ExpenseRecord> records = new ArrayList<>();
        if (transactions == null) return records;

        for (Transaction t : transactions) {
            // 只取支出
            if (!"EXPENSE".equals(t.getType())) continue;
            records.add(new ExpenseRecord(
                    t.getAmount(),
                    t.getTransactionTime(),
                    t.isAnomaly()));
        }
        return records;
    }

    /**
     * 获取当天 00:00:00 的时间戳
     */
    private long getStartOfDay(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /**
     * LiveData 同步取值辅助方法
     *
     * <p>注意：此方法会阻塞当前线程直到 LiveData 有值，
     * 必须在后台线程调用！
     */
    private static <T> T getSync(LiveData<T> liveData) {
        final Object[] result = new Object[]{null};
        final boolean[] done = new boolean[]{false};
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.post(() -> {
            liveData.observeForever(value -> {
                result[0] = value;
                if (!done[0]) {
                    done[0] = true;
                    synchronized (result) {
                        result.notifyAll();
                    }
                }
            });
        });
        synchronized (result) {
            try {
                result.wait(5000);  // 最多等 5 秒
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        @SuppressWarnings("unchecked")
        T typed = (T) result[0];
        return typed;
    }
}