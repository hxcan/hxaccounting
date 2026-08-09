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
 * <p>v3.3 修复 (#861640737779)：
 * 配合 MainActivity v3.3，添加 getCurrentAccountId() 公共方法，
 * 让 MainActivity 能在多次回调时正确判断是否需要通知预算 ViewModel。
 *
 * @author 未来姐姐
 * @since 2026-08-06
 * @updated 2026-08-09 v3.3 公共方法 getCurrentAccountId()
 */
public class BudgetViewModel extends AndroidViewModel {

    private static final String TAG = "BudgetViewModel";

    private final BudgetRepository budgetRepository;
    private final ExecutorService ioExecutor;

    private final MutableLiveData<Long> currentAccountIdLive = new MutableLiveData<>();
    private final MutableLiveData<Double> currentRateLive = new MutableLiveData<>(1.0);
    private final MutableLiveData<Integer> windowSizeLive = new MutableLiveData<>(30);

    private final androidx.lifecycle.MediatorLiveData<Boolean> paramsReadyLive =
            new androidx.lifecycle.MediatorLiveData<>();

    private final LiveData<BudgetResult> budgetResultLive;

    public BudgetViewModel(@NonNull Application application) {
        super(application);
        FileLogger.i(TAG, "BudgetViewModel v3.3 初始化");

        TransactionRepository transactionRepository = new TransactionRepository(application);
        this.ioExecutor = Executors.newSingleThreadExecutor();
        this.budgetRepository = new BudgetRepository(
                TaijiDatabase.getInstance(application).transactionDao(),
                ioExecutor);

        paramsReadyLive.addSource(currentAccountIdLive, id -> updateParamsReady());
        paramsReadyLive.addSource(currentRateLive, rate -> updateParamsReady());
        paramsReadyLive.addSource(windowSizeLive, size -> updateParamsReady());

        budgetResultLive = Transformations.switchMap(
                paramsReadyLive,
                ready -> {
                    if (ready == null || !ready) {
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

    public void setCurrentAccountId(long accountId) {
        Long oldId = currentAccountIdLive.getValue();
        FileLogger.i(TAG, "setCurrentAccountId: 旧=" + oldId + ", 新=" + accountId);
        currentAccountIdLive.setValue(accountId);
    }

    /**
     * v3.3 新增：获取当前账本 ID
     * @return 当前账本 ID，如果没有则返回 -1L
     */
    public long getCurrentAccountId() {
        Long id = currentAccountIdLive.getValue();
        return id == null ? -1L : id;
    }

    public void setRate(double rate) {
        if (rate <= 0) {
            return;
        }
        currentRateLive.setValue(rate);
    }

    public void setWindowSize(int days) {
        if (days <= 0) {
            return;
        }
        windowSizeLive.setValue(days);
    }

    public LiveData<BudgetResult> getBudgetResult() {
        return budgetResultLive;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (ioExecutor != null && !ioExecutor.isShutdown()) {
            ioExecutor.shutdown();
        }
    }
}