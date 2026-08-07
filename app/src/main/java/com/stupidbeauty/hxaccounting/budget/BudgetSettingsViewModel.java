package com.stupidbeauty.hxaccounting.budget;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.stupidbeauty.hxaccounting.data.database.TaijiDatabase;
import com.stupidbeauty.hxaccounting.data.entity.BudgetSettings;
import com.stupidbeauty.hxaccounting.data.repository.BudgetSettingsRepository;

/**
 * 预算设置 ViewModel（v2 周期配置）
 *
 * <p>职责：
 * <ul>
 *   <li>持有 BudgetSettingsRepository 实例</li>
 *   <li>暴露 LiveData&lt;BudgetSettings&gt; 给 UI 观察</li>
 *   <li>提供保存周期天数的方法</li>
 *   <li>账本切换时切换被观察的 settings</li>
 * </ul>
 *
 * <p>注意：本 ViewModel 不修改 BudgetViewModel。两者职责分离：
 * <ul>
 *   <li>BudgetViewModel 管"算预算结果"</li>
 *   <li>BudgetSettingsViewModel 管"账本的预算周期配置"</li>
 * </ul>
 * 接入 BudgetCardBinder 时由 UI 层把两者组合（Step 7+ 再做）。
 *
 * @author 未来姐姐
 * @since 2026-08-08
 */
public class BudgetSettingsViewModel extends AndroidViewModel {

    private static final String TAG = "BudgetSettingsViewModel";

    private final BudgetSettingsRepository repository;
    private LiveData<BudgetSettings> currentSettingsLive;

    public BudgetSettingsViewModel(@NonNull Application application) {
        super(application);
        this.repository = new BudgetSettingsRepository(application);
    }

    /**
     * 观察指定账本的设置
     * @param accountId 账本 ID
     * @return LiveData&lt;BudgetSettings&gt;，账本切换时调用此方法切换数据源
     */
    public LiveData<BudgetSettings> getSettingsFor(long accountId) {
        currentSettingsLive = repository.getSettingsLive(accountId);
        // 首次访问兜底：确保该账本有默认配置
        repository.ensureDefaultSettings(accountId);
        return currentSettingsLive;
    }

    /**
     * 保存周期天数
     */
    public void setPeriodDays(long accountId, int periodDays) {
        if (periodDays <= 0) {
            return;  // 非法值直接忽略
        }
        repository.updatePeriodDays(accountId, periodDays);
    }

    /**
     * 保存完整设置对象
     */
    public void saveSettings(BudgetSettings settings) {
        if (settings == null) return;
        repository.saveSettings(settings);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (repository != null) {
            repository.shutdown();
        }
    }
}
