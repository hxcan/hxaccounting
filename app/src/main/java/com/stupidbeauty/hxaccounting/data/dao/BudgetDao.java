package com.stupidbeauty.hxaccounting.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import com.stupidbeauty.hxaccounting.data.entity.Budget;

/**
 * 预算配置数据访问接口
 * 每个账本只有一条预算配置（UNIQUE约束）
 */
@Dao
public interface BudgetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Budget budget);

    @Update
    void update(Budget budget);

    @Delete
    void delete(Budget budget);

    // --- 按账本查询预算配置 ---

    @Query("SELECT * FROM budgets WHERE account_id = :accountId LIMIT 1")
    LiveData<Budget> getByAccountId(long accountId);

    // --- 更新倍率（核心算法：用户调节系数）---

    @Query("UPDATE budgets SET rate = :rate, updated_at = :now WHERE account_id = :accountId")
    void updateRate(long accountId, double rate, long now);

    // --- 更新窗口大小 ---

    @Query("UPDATE budgets SET window_size = :windowSize, updated_at = :now WHERE account_id = :accountId")
    void updateWindowSize(long accountId, String windowSize, long now);

    // --- 开关预算控制 ---

    @Query("UPDATE budgets SET enabled = :enabled, updated_at = :now WHERE account_id = :accountId")
    void setEnabled(long accountId, boolean enabled, long now);

    // --- 设置是否排除异常支出 ---

    @Query("UPDATE budgets SET anomaly_excluded = :excluded, updated_at = :now WHERE account_id = :accountId")
    void setAnomalyExcluded(long accountId, boolean excluded, long now);
}
