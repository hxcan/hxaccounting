package com.stupidbeauty.hxaccounting.data.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;

import com.stupidbeauty.hxaccounting.data.dao.TransactionDao;
import com.stupidbeauty.hxaccounting.data.database.TaijiDatabase;
import com.stupidbeauty.hxaccounting.data.entity.Transaction;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 流水数据仓库
 * 封装 TransactionDao 的业务逻辑
 */
public class TransactionRepository {

    private final TransactionDao transactionDao;
    private final ExecutorService ioExecutor;

    public TransactionRepository(Context context) {
        TaijiDatabase db = TaijiDatabase.getInstance(context);
        this.transactionDao = db.transactionDao();
        this.ioExecutor = Executors.newSingleThreadExecutor();
    }

    // --- 写入 ---

    public void insert(Transaction transaction, InsertCallback callback) {
        ioExecutor.execute(() -> {
            long id = transactionDao.insert(transaction);
            if (callback != null) {
                callback.onInserted(id);
            }
        });
    }

    public void update(Transaction transaction) {
        ioExecutor.execute(() -> transactionDao.update(transaction));
    }

    public void delete(Transaction transaction) {
        ioExecutor.execute(() -> transactionDao.delete(transaction));
    }

    // --- 按账本查询 ---

    public LiveData<List<Transaction>> getByAccountId(long accountId, int limit, int offset) {
        return transactionDao.getByAccountId(accountId, limit, offset);
    }

    public LiveData<List<Transaction>> getByAccountIdAndTimeRange(
            long accountId, long startTime, long endTime) {
        return transactionDao.getByAccountIdAndTimeRange(accountId, startTime, endTime);
    }

    public LiveData<List<Transaction>> getByCategoryId(long categoryId, int limit) {
        return transactionDao.getByCategoryId(categoryId, limit);
    }

    // --- 统计查询（核心算法用）---

    public LiveData<Double> getTotalExpenseAfter(long accountId, long startTime) {
        return transactionDao.getTotalExpenseAfter(accountId, startTime);
    }

    public LiveData<Integer> getExpenseCountAfter(long accountId, long startTime) {
        return transactionDao.getExpenseCountAfter(accountId, startTime);
    }

    public LiveData<Double> getAverageExpenseAfter(long accountId, long startTime) {
        return transactionDao.getAverageExpenseAfter(accountId, startTime);
    }

    // --- 今日/本周/本月汇总 ---

    public LiveData<Double> getTodayTotal(long accountId, long startOfDay, long endOfDay) {
        return transactionDao.getTodayTotal(accountId, startOfDay, endOfDay);
    }

    /**
     * 本周支出合计（feat/transaction-summary）
     */
    public LiveData<Double> getWeekTotal(long accountId, long startOfWeek) {
        return transactionDao.getWeekTotal(accountId, startOfWeek);
    }

    public LiveData<Double> getMonthTotal(long accountId, long startOfMonth, long endOfMonth) {
        return transactionDao.getMonthTotal(accountId, startOfMonth, endOfMonth);
    }

    /**
     * 本月收入合计（feat/transaction-summary）
     */
    public LiveData<Double> getMonthIncome(long accountId, long startOfMonth, long endOfMonth) {
        return transactionDao.getMonthIncome(accountId, startOfMonth, endOfMonth);
    }

    public interface InsertCallback {
        void onInserted(long id);
    }
}