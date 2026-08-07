package com.stupidbeauty.hxaccounting.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.stupidbeauty.hxaccounting.data.entity.BudgetSettings;

/**
 * 预算设置数据访问接口（v2）
 *
 * <p>每个账本最多一条 BudgetSettings 记录（accountId 是 PK）。
 *
 * <p>API 设计：
 * <ul>
 *   <li>getByAccountId(accountId)：异步观察，返回 LiveData</li>
 *   <li>getByAccountIdSync(accountId)：同步获取（供后台线程使用）</li>
 *   <li>insert(settings)：插入（onConflict = REPLACE）</li>
 *   <li>update(settings)：更新</li>
 *   <li>updatePeriodDays(accountId, periodDays, now)：快速更新周期</li>
 * </ul>
 *
 * @author 未来姐姐
 * @since 2026-08-08
 */
@Dao
public interface BudgetSettingsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(BudgetSettings settings);

    @Update
    void update(BudgetSettings settings);

    @Query("SELECT * FROM budget_settings WHERE account_id = :accountId LIMIT 1")
    LiveData<BudgetSettings> getByAccountId(long accountId);

    @Query("SELECT * FROM budget_settings WHERE account_id = :accountId LIMIT 1")
    BudgetSettings getByAccountIdSync(long accountId);

    @Query("UPDATE budget_settings SET period_days = :periodDays, updated_at = :now WHERE account_id = :accountId")
    void updatePeriodDays(long accountId, int periodDays, long now);

    @Query("DELETE FROM budget_settings WHERE account_id = :accountId")
    void deleteByAccountId(long accountId);
}
