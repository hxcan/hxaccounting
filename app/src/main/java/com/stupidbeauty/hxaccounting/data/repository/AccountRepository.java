package com.stupidbeauty.hxaccounting.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.stupidbeauty.hxaccounting.data.dao.AccountDao;
import com.stupidbeauty.hxaccounting.data.database.TaijiDatabase;
import com.stupidbeauty.hxaccounting.data.entity.Account;
import com.stupidbeauty.hxaccounting.data.entity.AccountType;
import com.stupidbeauty.hxaccounting.utils.FileLogger;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 账本数据仓库
 * 封装 AccountDao 的业务逻辑
 *
 * 职责：
 * 1. 封装 DAO 调用，提供响应式数据流
 * 2. 管理当前账本状态（跨 Activity 共享）
 * 3. 异步执行写入操作
 * 4. 提供业务级方法（设置当前账本、按类型获取等）
 *
 * Bug 修复（#859357536826 / #859820582382）：
 * - 使用 Transformations.switchMap 让 getCurrentAccount 真正响应 currentAccountIdLive 变化
 * - 解决"启动后顶部不显示当前账本"的问题
 */
public class AccountRepository {
    private static final String TAG = "AccountRepository";

    // SharedPreferences 键
    private static final String PREFS_NAME = "account_prefs";
    private static final String KEY_CURRENT_ACCOUNT_ID = "current_account_id";

    private final AccountDao accountDao;
    private final ExecutorService ioExecutor;
    private final Context appContext;

    // 当前账本状态（跨 Activity 共享）
    private final MutableLiveData<Long> currentAccountIdLive = new MutableLiveData<>();

    // 当前账本 LiveData：基于 currentAccountIdLive 自动响应变化
    private final LiveData<Account> currentAccountLive;

    public AccountRepository(Context context) {
        this.appContext = context.getApplicationContext();
        TaijiDatabase db = TaijiDatabase.getInstance(this.appContext);
        this.accountDao = db.accountDao();
        this.ioExecutor = Executors.newSingleThreadExecutor();

        // 从 SharedPreferences 同步恢复当前账本（用 setValue 保证立即生效）
        SharedPreferences prefs = this.appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long savedId = prefs.getLong(KEY_CURRENT_ACCOUNT_ID, -1L);
        currentAccountIdLive.setValue(savedId);
        FileLogger.i(TAG, "AccountRepository 初始化完成，从 SharedPreferences 恢复当前账本 ID=" + savedId);

        // 使用 switchMap：currentAccountIdLive 变化时，自动切换数据源
        // 这样 getCurrentAccount() 不需要每次手动调用，永远是响应式的
        currentAccountLive = Transformations.switchMap(currentAccountIdLive, currentId -> {
            FileLogger.d(TAG, "【Repo】switchMap 触发，currentId=" + currentId);
            if (currentId == null || currentId == -1L) {
                FileLogger.d(TAG, "【Repo】switchMap 返回 null LiveData");
                MutableLiveData<Account> empty = new MutableLiveData<>();
                empty.setValue(null);
                return empty;
            }
            FileLogger.d(TAG, "【Repo】switchMap 从 DAO 查询账本 ID=" + currentId);
            return accountDao.getAccountById(currentId);
        });
    }

    // ========== 查询（LiveData，自动响应 UI） ==========

    /**
     * 获取所有未归档账本
     */
    public LiveData<List<Account>> getActiveAccounts() {
        return accountDao.getActiveAccounts();
    }

    /**
     * 获取所有账本（含已归档）
     */
    public LiveData<List<Account>> getAllAccounts() {
        return accountDao.getAllAccounts();
    }

    /**
     * 根据 ID 获取账本
     */
    public LiveData<Account> getAccountById(long id) {
        FileLogger.d(TAG, "【Repo】getAccountById 被调用，id=" + id);
        return accountDao.getAccountById(id);
    }

    /**
     * 根据类型查询账本
     */
    public LiveData<List<Account>> getAccountsByType(AccountType type) {
        return accountDao.findByAccountType(type);
    }

    /**
     * 模糊搜索账本
     */
    public LiveData<List<Account>> searchAccounts(String keyword) {
        return accountDao.searchByName(keyword);
    }

    /**
     * 同步查询：根据名称查重
     */
    @Nullable
    public Account findByNameSync(String name) {
        return accountDao.findByName(name);
    }

    /**
     * 同步查询：检测名称是否存在
     */
    public boolean existsByName(String name) {
        return accountDao.existsByName(name);
    }

    /**
     * 同步查询：首个指定类型的账本
     */
    @Nullable
    public Account findFirstByType(@Nullable AccountType type) {
        if (type == null) return null;
        return accountDao.findFirstByType(type.name());
    }

    /**
     * 同步查询：统计数量
     */
    public int getActiveAccountCount() {
        return accountDao.countActive();
    }

    // ========== 当前账本状态管理 ==========

    /**
     * 获取当前账本 ID（LiveData，自动响应）
     */
    public LiveData<Long> getCurrentAccountId() {
        return currentAccountIdLive;
    }

    /**
     * 同步获取当前账本 ID
     * 注意：必须在主线程调用，因为内部用 setValue
     */
    public long getCurrentAccountIdSync() {
        Long value = currentAccountIdLive.getValue();
        long result = value != null ? value : -1L;
        FileLogger.d(TAG, "【Repo】getCurrentAccountIdSync 返回 " + result);
        return result;
    }

    /**
     * 设置当前账本
     * @param accountId 账本 ID（-1L 表示清除当前账本）
     */
    public void setCurrentAccountId(long accountId) {
        FileLogger.i(TAG, "【Repo】setCurrentAccountId 被调用，accountId=" + accountId);
        Long oldValue = currentAccountIdLive.getValue();
        FileLogger.d(TAG, "【Repo】setValue 前，currentAccountIdLive.getValue()=" + oldValue);

        // 用 setValue（同步）确保立即生效
        currentAccountIdLive.setValue(accountId);

        Long newValue = currentAccountIdLive.getValue();
        FileLogger.d(TAG, "【Repo】setValue 后，currentAccountIdLive.getValue()=" + newValue);
        FileLogger.d(TAG, "【Repo】currentAccountIdLive 变化：" + oldValue + " -> " + newValue);

        // 持久化到 SharedPreferences
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_CURRENT_ACCOUNT_ID, accountId).apply();
        FileLogger.i(TAG, "【Repo】setCurrentAccountId 完成");
    }

    /**
     * 获取当前账本（LiveData，自动响应）
     * 使用 switchMap 实现：currentAccountId 变化时自动重新查询账本
     */
    public LiveData<Account> getCurrentAccount() {
        FileLogger.i(TAG, "【Repo】getCurrentAccount 被调用，返回 currentAccountLive");
        return currentAccountLive;
    }

    /**
     * 强制刷新当前账本 LiveData（已废弃，switchMap 自动响应）
     * 保留仅为向后兼容
     */
    @Deprecated
    public void invalidateCurrentAccount() {
        FileLogger.w(TAG, "【Repo】invalidateCurrentAccount 被调用（已废弃，switchMap 自动响应）");
    }

    // ========== 写入（异步执行） ==========

    /**
     * 插入账本
     * @param callback 插入完成后的回调（可选）
     */
    public void insert(Account account, @Nullable InsertCallback callback) {
        ioExecutor.execute(() -> {
            long id = accountDao.insert(account);
            if (callback != null) {
                callback.onInserted(id);
            }
        });
    }

    /**
     * 更新账本
     */
    public void update(Account account) {
        ioExecutor.execute(() -> accountDao.update(account));
    }

    /**
     * 归档账本（is_archived = true）
     */
    public void archive(long id) {
        ioExecutor.execute(() -> accountDao.archive(id, true, System.currentTimeMillis()));
    }

    /**
     * 取消归档
     */
    public void unarchive(long id) {
        ioExecutor.execute(() -> accountDao.archive(id, false, System.currentTimeMillis()));
    }

    /**
     * 删除账本
     */
    public void delete(Account account) {
        ioExecutor.execute(() -> accountDao.delete(account));
    }

    /**
     * 重命名账本
     */
    public void rename(long id, String newName) {
        ioExecutor.execute(() -> accountDao.updateName(id, newName, System.currentTimeMillis()));
    }

    /**
     * 更新预算
     */
    public void updateBudget(long id, double budget) {
        ioExecutor.execute(() -> accountDao.updateBudget(id, budget, System.currentTimeMillis()));
    }

    /**
     * 批量删除所有已归档账本
     */
    public void deleteAllArchived() {
        ioExecutor.execute(() -> accountDao.deleteAllArchived());
    }

    // ========== 业务级方法 ==========

    /**
     * 创建账本并自动设为当前账本
     */
    public void createAndSetCurrent(Account account, @Nullable InsertCallback callback) {
        ioExecutor.execute(() -> {
            long id = accountDao.insert(account);
            // 创建后自动设为当前账本
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(KEY_CURRENT_ACCOUNT_ID, id)
                    .apply();
            currentAccountIdLive.setValue(id);
            if (callback != null) {
                callback.onInserted(id);
            }
        });
    }

    /**
     * 异步检测账本名称是否存在
     * 在后台线程查询数据库，callback 回调到调用方线程（通常是主线程）
     *
     * @param name 账本名称
     * @param callback 结果回调，true 表示存在
     */
    public void existsByNameAsync(String name, @Nullable ExistsCallback callback) {
        ioExecutor.execute(() -> {
            boolean exists = accountDao.existsByName(name);
            if (callback != null) {
                callback.onResult(exists);
            }
        });
    }

    /**
     * 清理资源（Activity 销毁时调用）
     */
    public void shutdown() {
        ioExecutor.shutdown();
    }

    /**
     * 插入回调接口
     */
    public interface InsertCallback {
        void onInserted(long id);
    }

    /**
     * 存在性查询回调接口
     */
    public interface ExistsCallback {
        void onResult(boolean exists);
    }
}