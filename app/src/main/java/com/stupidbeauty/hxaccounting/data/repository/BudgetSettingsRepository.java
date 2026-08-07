package com.stupidbeauty.hxaccounting.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.stupidbeauty.hxaccounting.data.dao.BudgetSettingsDao;
import com.stupidbeauty.hxaccounting.data.database.TaijiDatabase;
import com.stupidbeauty.hxaccounting.data.entity.BudgetSettings;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 预算设置仓库（v2 周期配置）
 *
 * <p>职责：
 * <ul>
 *   <li>封装 BudgetSettingsDao 的访问</li>
 *   <li>提供 LiveData&lt;BudgetSettings&gt; 给 ViewModel 观察</li>
 *   <li>提供同步获取 / 保存设置的方法</li>
 *   <li>内部使用单线程 Executor 做后台 IO</li>
 * </ul>
 *
 * <p>如果某个账本还没有设置记录，调用方应使用 {@link #ensureDefaultSettings(long)}
 * 在后台插入一条默认 30 天的记录。
 *
 * @author 未来姐姐
 * @since 2026-08-08
 */
public class BudgetSettingsRepository {

    private static final String TAG = "BudgetSettingsRepository";

    private final BudgetSettingsDao dao;
    private final ExecutorService ioExecutor;

    public BudgetSettingsRepository(Context context) {
        this(TaijiDatabase.getInstance(context).budgetSettingsDao(),
                Executors.newSingleThreadExecutor());
    }

    /** 测试或共享 Executor 时使用的构造器 */
    public BudgetSettingsRepository(BudgetSettingsDao dao, ExecutorService ioExecutor) {
        this.dao = dao;
        this.ioExecutor = ioExecutor;
    }

    /**
     * 观察指定账本的设置（异步 LiveData）
     */
    public LiveData<BudgetSettings> getSettingsLive(long accountId) {
        return dao.getByAccountId(accountId);
    }

    /**
     * 同步获取指定账本的设置
     * 注意：必须在后台线程调用
     */
    public BudgetSettings getSettingsSync(long accountId) {
        return dao.getByAccountIdSync(accountId);
    }

    /**
     * 保存设置（INSERT OR REPLACE）
     */
    public void saveSettings(BudgetSettings settings) {
        settings.setUpdatedAt(System.currentTimeMillis());
        ioExecutor.execute(() -> dao.insert(settings));
    }

    /**
     * 快速更新周期天数
     */
    public void updatePeriodDays(long accountId, int periodDays) {
        ioExecutor.execute(() ->
                dao.updatePeriodDays(accountId, periodDays, System.currentTimeMillis()));
    }

    /**
     * 如果该账本还没有设置记录，则插入一条默认 30 天配置
     * 用于首次访问账本时的兜底逻辑
     */
    public void ensureDefaultSettings(long accountId) {
        ioExecutor.execute(() -> {
            BudgetSettings existing = dao.getByAccountIdSync(accountId);
            if (existing == null) {
                dao.insert(new BudgetSettings(accountId));
            }
        });
    }

    public void shutdown() {
        if (ioExecutor != null && !ioExecutor.isShutdown()) {
            ioExecutor.shutdown();
        }
    }
}
