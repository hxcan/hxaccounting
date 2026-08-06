package com.stupidbeauty.hxaccounting.budget;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.stupidbeauty.hxaccounting.data.database.TaijiDatabase;
import com.stupidbeauty.hxaccounting.data.repository.TransactionRepository;
import com.stupidbeauty.hxaccounting.utils.FileLogger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 预算 ViewModel
 *
 * <p>职责：
 * <ul>
 *   <li>持有 BudgetRepository 实例</li>
 *   <li>暴露 LiveData&lt;BudgetResult&gt; 给 UI</li>
 *   <li>管理预算参数（窗口大小、倍率）</li>
 *   <li>账本切换时自动重订阅</li>
 * </ul>
 *
 * @author 未来姐姐
 * @since 2026-08-06
 */
public class BudgetViewModel extends AndroidViewModel {

    private static final String TAG = "BudgetViewModel";

    private final BudgetRepository budgetRepository;
    private final ExecutorService ioExecutor;
    private final MutableLiveData<Long> currentAccountIdLive = new MutableLiveData<>();
    private final MutableLiveData<Double> currentRateLive = new MutableLiveData<>(1.0);
    private final MutableLiveData<Integer> windowSizeLive = new MutableLiveData<>(7);
    private LiveData<BudgetResult> budgetResultLive;

    public BudgetViewModel(@NonNull Application application) {
        super(application);
        FileLogger.i(TAG, "BudgetViewModel 初始化");

        // 初始化 Repository（复用 TransactionRepository 的线程池）
        TransactionRepository transactionRepository = new TransactionRepository(application);
        this.ioExecutor = Executors.newSingleThreadExecutor();
        this.budgetRepository = new BudgetRepository(
                TaijiDatabase.getInstance(application).transactionDao(),
                ioExecutor);

        // 当账本或参数变化时，重订阅预算结果
        // 用 MediatorLiveData 组合三个源
        currentAccountIdLive.observeForever(accountId -> rebuildBudgetLive());
        currentRateLive.observeForever(rate -> rebuildBudgetLive());
        windowSizeLive.observeForever(size -> rebuildBudgetLive());
    }

    /**
     * 设置当前账本 ID（账本切换时调用）
     */
    public void setCurrentAccountId(long accountId) {
        FileLogger.d(TAG, "setCurrentAccountId: " + accountId);
        currentAccountIdLive.setValue(accountId);
    }

    /**
     * 设置倍率（用户调整时调用）
     */
    public void setRate(double rate) {
        if (rate <= 0) {
            FileLogger.w(TAG, "setRate 收到非法值: " + rate + "，忽略");
            return;
        }
        FileLogger.d(TAG, "setRate: " + rate);
        currentRateLive.setValue(rate);
    }

    /**
     * 设置窗口大小（天）
     */
    public void setWindowSize(int days) {
        if (days <= 0) {
            FileLogger.w(TAG, "setWindowSize 收到非法值: " + days + "，忽略");
            return;
        }
        FileLogger.d(TAG, "setWindowSize: " + days);
        windowSizeLive.setValue(days);
    }

    /**
     * 获取预算结果 LiveData
     * UI observe 此 LiveData，自动响应账本切换、流水变化、参数调整
     */
    public LiveData<BudgetResult> getBudgetResult() {
        return budgetResultLive;
    }

    /**
     * 重建预算 LiveData（参数或账本变化时调用）
     */
    private void rebuildBudgetLive() {
        Long accountId = currentAccountIdLive.getValue();
        Double rate = currentRateLive.getValue();
        Integer windowSize = windowSizeLive.getValue();

        if (accountId == null || accountId <= 0
                || rate == null || rate <= 0
                || windowSize == null || windowSize <= 0) {
            FileLogger.d(TAG, "rebuildBudgetLive: 参数未就绪，跳过");
            budgetResultLive = null;
            return;
        }

        FileLogger.i(TAG, "rebuildBudgetLive: accountId=" + accountId
                + ", rate=" + rate + ", windowSize=" + windowSize);
        budgetResultLive = budgetRepository.getBudgetLive(
                accountId, windowSize, rate, true);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        FileLogger.i(TAG, "BudgetViewModel onCleared");
        if (ioExecutor != null && !ioExecutor.isShutdown()) {
            ioExecutor.shutdown();
        }
    }
}