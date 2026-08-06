package com.stupidbeauty.hxaccounting.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import com.stupidbeauty.hxaccounting.data.entity.Transaction;
import java.util.List;

/**
 * 流水数据访问接口
 * 对应 transactions 表的增删改查
 * 包含按时间范围、按分类、按账本的查询
 */
@Dao
public interface TransactionDao {

    // --- 基础 CRUD ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Transaction transaction);

    @Update
    void update(Transaction transaction);

    @Delete
    void delete(Transaction transaction);

    // --- 按账本查询（最常用）---

    @Query("SELECT * FROM transactions WHERE account_id = :accountId " +
           "ORDER BY transaction_time DESC LIMIT :limit OFFSET :offset")
    LiveData<List<Transaction>> getByAccountId(long accountId, int limit, int offset);

    @Query("SELECT * FROM transactions WHERE account_id = :accountId " +
           "AND transaction_time >= :startTime AND transaction_time <= :endTime " +
           "ORDER BY transaction_time DESC")
    LiveData<List<Transaction>> getByAccountIdAndTimeRange(
            long accountId, long startTime, long endTime);

    // --- 按分类查询 ---

    @Query("SELECT * FROM transactions WHERE category_id = :categoryId " +
           "ORDER BY transaction_time DESC LIMIT :limit")
    LiveData<List<Transaction>> getByCategoryId(long categoryId, int limit);

    // --- 按类型查询（收入/支出）---

    @Query("SELECT * FROM transactions WHERE account_id = :accountId " +
           "AND type = :type AND transaction_time >= :startTime " +
           "ORDER BY transaction_time DESC")
    LiveData<List<Transaction>> getByTypeAndTime(long accountId, String type, long startTime);

    // --- 统计查询（核心算法用）---

    @Query("SELECT SUM(amount) FROM transactions WHERE account_id = :accountId " +
           "AND type = 'EXPENSE' AND is_anomaly = 0 " +
           "AND transaction_time >= :startTime")
    LiveData<Double> getTotalExpenseAfter(long accountId, long startTime);

    @Query("SELECT COUNT(*) FROM transactions WHERE account_id = :accountId " +
           "AND type = 'EXPENSE' AND is_anomaly = 0 " +
           "AND transaction_time >= :startTime")
    LiveData<Integer> getExpenseCountAfter(long accountId, long startTime);

    @Query("SELECT AVG(amount) FROM transactions WHERE account_id = :accountId " +
           "AND type = 'EXPENSE' AND is_anomaly = 0 " +
           "AND transaction_time >= :startTime")
    LiveData<Double> getAverageExpenseAfter(long accountId, long startTime);

    // --- 今日/本周/本月汇总 ---

    @Query("SELECT SUM(amount) FROM transactions WHERE account_id = :accountId " +
           "AND type = 'EXPENSE' AND transaction_time >= :startOfDay " +
           "AND transaction_time < :endOfDay")
    LiveData<Double> getTodayTotal(long accountId, long startOfDay, long endOfDay);

    /**
     * 本周支出合计（feat/transaction-summary）
     */
    @Query("SELECT SUM(amount) FROM transactions WHERE account_id = :accountId " +
           "AND type = 'EXPENSE' AND transaction_time >= :startOfWeek")
    LiveData<Double> getWeekTotal(long accountId, long startOfWeek);

    @Query("SELECT SUM(amount) FROM transactions WHERE account_id = :accountId " +
           "AND type = 'EXPENSE' AND transaction_time >= :startOfMonth " +
           "AND transaction_time < :endOfMonth")
    LiveData<Double> getMonthTotal(long accountId, long startOfMonth, long endOfMonth);

    /**
     * 本月收入合计（feat/transaction-summary）
     */
    @Query("SELECT SUM(amount) FROM transactions WHERE account_id = :accountId " +
           "AND type = 'INCOME' AND transaction_time >= :startOfMonth " +
           "AND transaction_time < :endOfMonth")
    LiveData<Double> getMonthIncome(long accountId, long startOfMonth, long endOfMonth);

    // --- 删除某账本所有流水（级联已在Entity定义，这里做显式清理）---

    @Query("DELETE FROM transactions WHERE account_id = :accountId")
    void deleteByAccountId(long accountId);
}