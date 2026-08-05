package com.stupidbeauty.hxaccounting.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.stupidbeauty.hxaccounting.data.entity.Account;
import com.stupidbeauty.hxaccounting.data.entity.AccountType;

import java.util.List;

/**
 * 账本 DAO（数据访问对象）
 * 对应数据库 accounts 表
 *
 * 提供账本的 CRUD + 多维度查询能力
 */
@Dao
public interface AccountDao {

    // ============ INSERT ============

    /**
     * 插入新账本，返回自动生成的 ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Account account);

    /**
     * 批量插入账本
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Account> accounts);

    // ============ UPDATE ============

    /**
     * 更新账本
     * 自动更新 updatedAt 字段
     */
    @Update
    void update(Account account);

    /**
     * 根据 ID 更新名称
     */
    @Query("UPDATE accounts SET name = :name, updated_at = :updatedAt WHERE id = :id")
    void updateName(long id, String name, long updatedAt);

    /**
     * 根据 ID 更新预算
     */
    @Query("UPDATE accounts SET budget = :budget, updated_at = :updatedAt WHERE id = :id")
    void updateBudget(long id, double budget, long updatedAt);

    /**
     * 根据 ID 归档/取消归档
     */
    @Query("UPDATE accounts SET is_archived = :archived, updated_at = :updatedAt WHERE id = :id")
    void updateArchived(long id, boolean archived, long updatedAt);

    /**
     * 根据 ID 更新排序顺序
     */
    @Query("UPDATE accounts SET sort_order = :sortOrder, updated_at = :updatedAt WHERE id = :id")
    void updateSortOrder(long id, int sortOrder, long updatedAt);

    // ============ DELETE ============

    /**
     * 删除账本（级联删除流水由外键处理）
     */
    @Delete
    void delete(Account account);

    /**
     * 根据 ID 删除账本
     */
    @Query("DELETE FROM accounts WHERE id = :id")
    void deleteById(long id);

    /**
     * 删除所有已归档账本
     */
    @Query("DELETE FROM accounts WHERE is_archived = 1")
    void deleteAllArchived();

    // ============ QUERY - 单个 ============

    /**
     * 根据 ID 查询账本
     */
    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    Account findById(long id);

    /**
     * 根据 ID 查询账本（响应式）
     */
    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    LiveData<Account> findByIdLive(long id);

    /**
     * 根据名称精确查询（用于查重）
     */
    @Query("SELECT * FROM accounts WHERE name = :name LIMIT 1")
    Account findByName(String name);

    /**
     * 根据类型查询第一个账本
     */
    @Query("SELECT * FROM accounts WHERE type = :type AND is_archived = 0 ORDER BY sort_order ASC, created_at ASC LIMIT 1")
    Account findFirstByType(String type);

    // ============ QUERY - 列表 ============

    /**
     * 查询所有未归档账本（响应式）
     */
    @Query("SELECT * FROM accounts WHERE is_archived = 0 ORDER BY sort_order ASC, created_at ASC")
    LiveData<List<Account>> findAllActive();

    /**
     * 查询所有账本（含已归档）
     */
    @Query("SELECT * FROM accounts ORDER BY sort_order ASC, created_at ASC")
    LiveData<List<Account>> findAll();

    /**
     * 根据类型查询账本
     */
    @Query("SELECT * FROM accounts WHERE type = :type AND is_archived = 0 ORDER BY sort_order ASC, created_at ASC")
    LiveData<List<Account>> findByType(String type);

    /**
     * 根据 AccountType 枚举查询
     */
    @Query("SELECT * FROM accounts WHERE type = :accountType AND is_archived = 0 ORDER BY sort_order ASC, created_at ASC")
    LiveData<List<Account>> findByAccountType(AccountType accountType);

    /**
     * 模糊查询账本（按名称）
     */
    @Query("SELECT * FROM accounts WHERE name LIKE '%' || :keyword || '%' AND is_archived = 0 ORDER BY sort_order ASC, created_at ASC")
    LiveData<List<Account>> searchByName(String keyword);

    /**
     * 查询已归档账本
     */
    @Query("SELECT * FROM accounts WHERE is_archived = 1 ORDER BY updated_at DESC")
    LiveData<List<Account>> findArchived();

    // ============ QUERY - 统计 ============

    /**
     * 统计未归档账本数量
     */
    @Query("SELECT COUNT(*) FROM accounts WHERE is_archived = 0")
    int countActive();

    /**
     * 统计所有账本数量
     */
    @Query("SELECT COUNT(*) FROM accounts")
    int countAll();

    /**
     * 统计指定类型的账本数量
     */
    @Query("SELECT COUNT(*) FROM accounts WHERE type = :type AND is_archived = 0")
    int countByType(String type);

    /**
     * 检查名称是否存在（用于创建前查重）
     */
    @Query("SELECT EXISTS(SELECT 1 FROM accounts WHERE name = :name AND is_archived = 0)")
    boolean existsByName(String name);
}