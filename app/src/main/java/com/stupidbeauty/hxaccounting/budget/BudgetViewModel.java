package com.stupidbeauty.hxaccounting.budget;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

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
 * <p>v3 修复 (#861640737779)：用 {@code Transformations.switchMap} 把
 * {@code currentAccountIdLive} / {@code currentRateLive} / {@code windowSizeLive}
 * 三个参数源组合转换为 {@code BudgetResult} LiveData，账本切换自动响应。
 * <p>之前版本的 bug：{@code rebuildBudgetLive()} 每次重建新 LiveData 实例并
 * 赋值给 {@code budgetResultLive} 字段，但 UI 端 observe 的是旧引用，新数据永远到不了 UI。
 *
 * @author 未来姐姐
 * @since 2026-08-06
 * @updated 2026-08-08 默认窗口由 7 天改为 30 天（对齐 v2 算法默认周期）
 * @updated 2026-08-08 v2 调试日志：账本切换和参数变更加日志（任务 #861693812595）
 * @updated 2026-08-09 v3 switchMap 重构：账本切换真正自动响应（任务 #861640737779）
 */
public class BudgetViewModel extends AndroidViewModel {

    private static final String TAG = "BudgetViewModel";

    private final BudgetRepository budgetRepository;
    private final ExecutorService ioExecutor;

    // v3：用 MediatorLiveData 跟踪三参数的"是否都就绪"
    private final MutableLiveData<Long> currentAccountIdLive = new MutableLiveData<>();
    private final MutableLiveData<Double> currentRateLive = new MutableLiveData<>(1.0);
    /**
     * v2 修复：默认窗口由 7 天改为 30 天，对齐 BudgetCalculator.DEFAULT_PERIOD_DAYS。
     * 主人 2026-08-08 拍板的"周期默认 30 天"在此生效。
     * （每个账本独立的真实周期由 BudgetSettingsViewModel 提供，
     *  这里只是兜底默认值。）
     */
    private final MutableLiveData<Integer> windowSizeLive = new MutableLiveData<>(30);

    // v3：实时反映"三参数是否都就绪"的 MediatorLiveData
    private final androidx.lifecycle.MediatorLiveData<Boolean> paramsReadyLive = new androidx.lifecycle.MediatorLiveData<>();

    // v3：用 switchMap 组合"参数就绪状态"和"账本 ID"为 BudgetResult LiveData
    // 这是真正的响应式 LiveData —— 账本切换会自动重新订阅，UI observe 的是它就不会卡死。
    private final LiveData<BudgetResult> budgetResultLive;

    public BudgetViewModel(@NonNull Application application) {
        super(application);
        FileLogger.i(TAG, "BudgetViewModel v3 初始化");

        // 初始化 Repository（复用 TransactionRepository 的线程池）
        TransactionRepository transactionRepository = new TransactionRepository(application);
        this.ioExecutor = Executors.newSingleThreadExecutor();
        this.budgetRepository = new BudgetRepository(
                TaijiDatabase.getInstance(application).transactionDao(),
                ioExecutor);

        // v3：用 MediatorLiveData 跟踪三参数是否都就绪
        paramsReadyLive.addSource(currentAccountIdLive, id -> updateParamsReady());
        paramsReadyLive.addSource(currentRateLive, rate -> updateParamsReady());
        paramsReadyLive.addSource(windowSizeLive, size -> updateParamsReady());

        // v3：用 switchMap 把 (paramsReady, accountId) 转换为 BudgetResult LiveData
        // 任何时候账本或参数变化，MediatorLiveData 会重新触发，budgetResultLive 自动响应。
        budgetResultLive = Transformations.switchMap(
                androidx.lifecycle.MediatorLiveData.class.cast(
                        androidx.lifecycle.MutableLiveData<Long>.class.cast(currentAccountIdLive) != null
                                ? paramsReadyLive
                                : paramsReadyLive
                ),
                ready -> {
                    if (ready == null || !ready) {
                        FileLogger.d(TAG, "switchMap: 参数未就绪，返回 null");
                        return null;
                    }
                    Long accountId = currentAccountIdLive.getValue();
                    Double rate = currentRateLive.getValue();
                    Integer windowSize = windowSizeLive.getValue();
                    FileLogger.i(TAG, "switchMap: 触发预算重新计算 accountId=" + accountId
                            + ", rate=" + rate + ", windowSize=" + windowSize);
                    return budgetRepository.getBudgetLive(
                            accountId, windowSize, rate, true);
                });
    }

    /**
     * v3：检查三参数是否都就绪，更新 paramsReadyLive
     */
    private void updateParamsReady() {
        Long accountId = currentAccountIdLive.getValue();
        Double rate = currentRateLive.getValue();
        Integer windowSize = windowSizeLive.getValue();

        boolean ready = accountId != null && accountId > 0
                && rate != null && rate > 0
                && windowSize != null && windowSize > 0;

        FileLogger.d(TAG, "updateParamsReady: accountId=" + accountId
                + ", rate=" + rate + ", windowSize=" + windowSize
                + " → ready=" + ready);
        paramsReadyLive.setValue(ready);
    }

    /**
     * 设置当前账本 ID（账本切换时调用）
     *
     * <p>v3 修复：通过 LiveData 链自动触发预算重订阅，无需外部手动 observe 新引用。
     */
    public void setCurrentAccountId(long accountId) {
        Long oldId = currentAccountIdLive.getValue();
        FileLogger.i(TAG, "setCurrentAccountId: 旧=" + oldId + ", 新=" + accountId
                + " (变化=" + (oldId == null || oldId != accountId) + ")");
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
     *
     * <p>v3 修复：返回的是 {@code Transformations.switchMap} 产生的稳定 LiveData，
     * 账本切换时会自动重新订阅新的 BudgetResult，UI 端观察到的引用保持不变。
     */
    public LiveData<BudgetResult> getBudgetResult() {
        return budgetResultLive;
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