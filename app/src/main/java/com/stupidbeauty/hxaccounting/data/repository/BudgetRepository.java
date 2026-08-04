package com.stupidbeauty.hxaccounting.data.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;

import com.stupidbeauty.hxaccounting.data.dao.BudgetDao;
import com.stupidbeauty.hxaccounting.data.database.TaijiDatabase;
import com.stupidbeauty.hxaccounting.data.entity.Budget;
import com.stupidbeauty.hxaccounting.data.entity.WindowSize;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 预算数据仓库
 * 封装 BudgetDao 的业务逻辑
 * 包含预算配置的核心CRUD
 */
public class BudgetRepository {

    private final BudgetDao budgetDao;
    private final ExecutorService ioExecutor;

    public BudgetRepository(Context context) {
        TaijiDatabase db = TaijiDatabase.getInstance(context);
        this.budgetDao = db.budgetDao();
        this.ioExecutor = Executors.newSingleThreadExecutor();
    }

    public LiveData<Budget> getBudget(long accountId) {
        return budgetDao.getByAccountId(accountId);
    }

    /**
     * 创建或更新预算配置（基于accountId的唯一约束）
     */
    public void saveBudget(long accountId, WindowSize windowSize, double rate,
                            boolean anomalyExcluded, boolean enabled) {
        ioExecutor.execute(() -> {
            Budget budget = new Budget(accountId);
            budget.setWindowSizeEnum(windowSize);
            budget.setRate(rate);
            budget.setAnomalyExcluded(anomalyExcluded);
            budget.setEnabled(enabled);
            budget.setUpdatedAt(System.currentTimeMillis());
            budgetDao.insert(budget);
        });
    }

    /**
     * 更新倍率（用户调节系数）
     */
    public void updateRate(long accountId, double rate) {
        ioExecutor.execute(() -> budgetDao.updateRate(accountId, rate, System.currentTimeMillis()));
    }

    /**
     * 更新窗口大小
     */
    public void updateWindowSize(long accountId, WindowSize windowSize) {
        ioExecutor.execute(() ->
            budgetDao.updateWindowSize(accountId, windowSize.name(), System.currentTimeMillis()));
    }

    /**
     * 开关预算
     */
    public void setEnabled(long accountId, boolean enabled) {
        ioExecutor.execute(() -> budgetDao.setEnabled(accountId, enabled, System.currentTimeMillis()));
    }

    /**
     * 是否排除异常支出
     */
    public void setAnomalyExcluded(long accountId, boolean excluded) {
        ioExecutor.execute(() ->
            budgetDao.setAnomalyExcluded(accountId, excluded, System.currentTimeMillis()));
    }
}
