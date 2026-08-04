package com.stupidbeauty.hxaccounting.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import com.stupidbeauty.hxaccounting.data.entity.Account;
import java.util.List;

/**
 * 账本数据访问接口
 * 对应 accounts 表的增删改查
 */
@Dao
public interface AccountDao {

    @Query("SELECT * FROM accounts WHERE is_archived = 0 ORDER BY sort_order ASC, created_at DESC")
    LiveData<List<Account>> getActiveAccounts();

    @Query("SELECT * FROM accounts ORDER BY sort_order ASC, created_at DESC")
    LiveData<List<Account>> getAllAccounts();

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    LiveData<Account> getAccountById(long id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Account account);

    @Update
    void update(Account account);

    @Delete
    void delete(Account account);

    @Query("UPDATE accounts SET is_archived = 1, updated_at = :now WHERE id = :id")
    void archive(long id, long now);

    @Query("UPDATE accounts SET name = :name, color = :color, icon = :icon, " +
           "budget = :budget, sort_order = :sortOrder, updated_at = :now WHERE id = :id")
    void updateDetails(long id, String name, String color, String icon,
                       double budget, int sortOrder, long now);
}
