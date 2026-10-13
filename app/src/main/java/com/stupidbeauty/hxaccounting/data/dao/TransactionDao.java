package com.stupidbeauty.hxaccounting.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import com.stupidbeauty.hxaccounting.data.entity.Transaction;
import com.stupidbeauty.hxaccounting.data.entity.TransactionStatistics;
import com.stupidbeauty.hxaccounting.data.entity.TransactionWithCategory;
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

    // --- 按 ID 查询（编辑流水功能用） ---

    @Query("SELECT * FROM transactions WHERE id = :id")
    Transaction getByIdSync(long id);

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

    // ====================================================================
    // 以下为 PDF 导出功能新增方法（#918517000679）
    // 全部为同步方法，调用方必须在后台线程！
    // ====================================================================

    /**
     * 按账本 ID 同步查询所有流水（按时间倒序）
     * PDF 导出用，调用方需在后台线程（Room 禁止主线程执行同步查询）
     */
    @Query("SELECT * FROM transactions WHERE account_id = :accountId " +
           "ORDER BY transaction_time DESC")
    List<Transaction> getByAccountIdSync(long accountId);

    /**
     * 按账本 ID + 时间区间同步查询流水（按时间倒序）
     * startTime / endTime 单位毫秒，闭区间
     * 调用方需在后台线程
     */
    @Query("SELECT * FROM transactions WHERE account_id = :accountId " +
           "AND transaction_time >= :startTime AND transaction_time <= :endTime " +
           "ORDER BY transaction_time DESC")
    List<Transaction> getByAccountIdAndTimeRangeSync(
            long accountId, long startTime, long endTime);

    /**
     * 按账本 ID JOIN 查询流水（含分类名）
     * 一次性拿到 TransactionWithCategory 列表，PDF 渲染不用再查 CategoryRepository
     * LEFT JOIN：未分类（categoryId=NULL）也能正常返回，categoryName 为 null
     * 调用方需在后台线程
     */
    @Query("SELECT t.id AS id, t.account_id AS accountId, t.amount AS amount, " +
           "t.type AS type, t.category_id AS categoryId, c.name AS categoryName, " +
           "t.subcategory AS subcategory, t.description AS description, " +
           "t.recipient AS recipient, t.payment_method AS paymentMethod, " +
           "t.transaction_time AS transactionTime, t.is_anomaly AS isAnomaly, " +
           "t.source AS source " +
           "FROM transactions t LEFT JOIN categories c ON t.category_id = c.id " +
           "WHERE t.account_id = :accountId " +
           "ORDER BY t.transaction_time DESC")
    List<TransactionWithCategory> getByAccountIdWithCategorySync(long accountId);

    /**
     * 计算账本收支统计（行 POJO，Room 返回类型）
     * 字段名必须与 SQL SELECT 别名完全一致
     * 注意：金额 SUM 已自动排除 NULL（COALESCE），count 用 SUM(CASE...) 避免 0 行返回 null
     * 调用方需在后台线程
     */
    @Query("SELECT " +
           "  COALESCE(SUM(CASE WHEN type = 'INCOME' AND is_anomaly = 0 THEN amount ELSE 0 END), 0) AS totalIncome, " +
           "  COALESCE(SUM(CASE WHEN type = 'EXPENSE' AND is_anomaly = 0 THEN amount ELSE 0 END), 0) AS totalExpense, " +
           "  COUNT(*) AS transactionCount, " +
           "  COALESCE(SUM(CASE WHEN is_anomaly = 1 THEN 1 ELSE 0 END), 0) AS anomalyCount, " +
           "  COALESCE(SUM(CASE WHEN type = 'INCOME' THEN 1 ELSE 0 END), 0) AS incomeCount, " +
           "  COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN 1 ELSE 0 END), 0) AS expenseCount " +
           "FROM transactions WHERE account_id = :accountId")
    TransactionStatisticsRow calculateStatisticsRowSync(long accountId);

    /**
     * Room 返回类型专用 POJO（数据库行）
     * 字段名必须与 SQL SELECT 别名完全一致
     * 与 TransactionStatistics（业务 POJO）不同：这里只有数据库原始字段，没有 balance（计算字段）
     */
    class TransactionStatisticsRow {
        public double totalIncome;
        public double totalExpense;
        public long transactionCount;
        public long anomalyCount;
        public long incomeCount;
        public long expenseCount;
    }
}
