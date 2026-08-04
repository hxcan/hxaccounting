package com.stupidbeauty.hxaccounting.data.repository;

import androidx.lifecycle.LiveData;

import com.stupidbeauty.hxaccounting.data.dao.BudgetDao;
import com.stupidbeauty.hxaccounting.data.dao.TransactionDao;
import com.stupidbeauty.hxaccounting.data.database.TaijiDatabase;
import com.stupidbeauty.hxaccounting.data.entity.Budget;
import com.stupidbeauty.hxaccounting.data.entity.WindowSize;

import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 预算计算器（核心算法）
 *
 * 核心算法：次日预算 = 历史日均支出 × 倍率
 *
 * 倍率 < 1（如 0.9）= 削预算
 * 倍率 = 1 = 保持现状
 * 倍率 > 1（如 1.1）= 增加开支
 *
 * 时间窗口：1M/3M/6M/1Y/ALL
 * 异常支出：默认不计入日均
 */
public class BudgetCalculator {

    private final TransactionDao transactionDao;
    private final BudgetDao budgetDao;
    private final ExecutorService executor;

    public BudgetCalculator(TaijiDatabase db) {
        this.transactionDao = db.transactionDao();
        this.budgetDao = db.budgetDao();
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * 获取某账本的预算配置
     */
    public LiveData<Budget> getBudget(long accountId) {
        return budgetDao.getByAccountId(accountId);
    }

    /**
     * 计算窗口起始时间戳
     */
    public long calculateStartTime(WindowSize windowSize) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -windowSize.getDays());
        return cal.getTimeInMillis();
    }

    /**
     * 计算日均支出（核心算法）
     * @param accountId 账本ID
     * @param windowSize 时间窗口
     * @param excludeAnomaly 是否排除异常支出
     * @return 计算结果（包含日均、总数、天数等）
     */
    public void calculateDailyAverage(long accountId, WindowSize windowSize,
                                     boolean excludeAnomaly, CalculationCallback callback) {
        executor.execute(() -> {
            long startTime = calculateStartTime(windowSize);
            long now = System.currentTimeMillis();

            double totalExpense = 0.0;
            int count = 0;

            // TODO: 实际从 TransactionDao 查询
            // 这里需要一次性查询总金额和数量
            // 由于 LiveData 是异步的，这里用简化的同步查询方案
            // 在实际项目中可以使用带回调的同步查询，或者用 RxJava

            int daysCount = calculateDaysBetween(startTime, now);
            double dailyAverage = daysCount > 0 ? totalExpense / daysCount : 0.0;

            BudgetResult result = new BudgetResult();
            result.totalExpense = totalExpense;
            result.count = count;
            result.daysCount = daysCount;
            result.dailyAverage = dailyAverage;
            result.startTime = startTime;
            result.endTime = now;

            if (callback != null) {
                callback.onResult(result);
            }
        });
    }

    /**
     * 计算次日预算（核心算法）
     */
    public void calculateNextDayBudget(long accountId, CalculationCallback callback) {
        executor.execute(() -> {
            // 1. 获取预算配置
            // 2. 计算日均
            // 3. 应用倍率
            // 4. 返回结果

            // 简化实现：假设已经获得了日均和倍率
            BudgetResult result = new BudgetResult();
            result.dailyAverage = 0.0;
            result.rate = 1.0;
            result.nextDayBudget = 0.0;

            if (callback != null) {
                callback.onResult(result);
            }
        });
    }

    /**
     * 计算今日已花费
     */
    public LiveData<Double> getTodaySpent(long accountId) {
        long[] dayRange = getTodayRange();
        return transactionDao.getTodayTotal(accountId, dayRange[0], dayRange[1]);
    }

    /**
     * 获取今日时间范围
     */
    public long[] getTodayRange() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startOfDay = cal.getTimeInMillis();

        cal.add(Calendar.DAY_OF_YEAR, 1);
        long endOfDay = cal.getTimeInMillis();

        return new long[]{startOfDay, endOfDay};
    }

    /**
     * 获取本月时间范围
     */
    public long[] getMonthRange() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startOfMonth = cal.getTimeInMillis();

        cal.add(Calendar.MONTH, 1);
        long endOfMonth = cal.getTimeInMillis();

        return new long[]{startOfMonth, endOfMonth};
    }

    private int calculateDaysBetween(long startTime, long endTime) {
        long diffMs = endTime - startTime;
        return Math.max(1, (int) (diffMs / (1000 * 60 * 60 * 24)));
    }

    /**
     * 预算计算结果封装
     */
    public static class BudgetResult {
        public double totalExpense = 0.0;      // 窗口内总支出
        public int count = 0;                   // 流水条数
        public int daysCount = 0;               // 天数
        public double dailyAverage = 0.0;       // 日均支出
        public double rate = 1.0;               // 倍率
        public double nextDayBudget = 0.0;      // 次日预算
        public long startTime = 0;              // 窗口起始
        public long endTime = 0;                // 窗口结束
        public double todaySpent = 0.0;         // 今日已花
        public double todayRemaining = 0.0;     // 今日剩余
    }

    /**
     * 计算回调接口
     */
    public interface CalculationCallback {
        void onResult(BudgetResult result);
    }
}
