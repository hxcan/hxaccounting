package com.stupidbeauty.hxaccounting.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.stupidbeauty.hxaccounting.R;
import com.stupidbeauty.hxaccounting.budget.BudgetSettingsViewModel;
import com.stupidbeauty.hxaccounting.data.entity.BudgetSettings;
import com.stupidbeauty.hxaccounting.utils.FileLogger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 预算设置页面（v2 自适应窗口算法）
 *
 * <p>功能：
 * <ul>
 *   <li>显示当前账本</li>
 *   <li>主人选择预算周期（7 / 30 / 90 天 / 自定义 7-365）</li>
 *   <li>保存到 budget_settings 表</li>
 * </ul>
 *
 * <p>当前账本通过 Intent extra "account_id" / "account_name" 传入，
 * 也可后续扩展为账本选择器。
 *
 * @author 未来姐姐
 * @since 2026-08-08
 */
public class BudgetSettingsActivity extends AppCompatActivity {

    private static final String TAG = "BudgetSettingsActivity";

    public static final String EXTRA_ACCOUNT_ID = "account_id";
    public static final String EXTRA_ACCOUNT_NAME = "account_name";

    private long accountId = 0L;
    private String accountName = "";

    private TextView tvCurrentAccountName;
    private RadioGroup rgPeriodDays;
    private RadioButton rbPeriod7;
    private RadioButton rbPeriod30;
    private RadioButton rbPeriod90;
    private RadioButton rbPeriodCustom;
    private EditText etCustomPeriod;
    private MaterialButton btnSaveSettings;
    private MaterialToolbar toolbar;

    private BudgetSettingsViewModel viewModel;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FileLogger.i(TAG, "BudgetSettingsActivity onCreate");

        setContentView(R.layout.activity_budget_settings);

        // 接收 Intent 参数
        accountId = getIntent().getLongExtra(EXTRA_ACCOUNT_ID, 0L);
        accountName = getIntent().getStringExtra(EXTRA_ACCOUNT_NAME);
        if (accountName == null) accountName = "";

        // 绑定视图
        toolbar = findViewById(R.id.toolbar);
        tvCurrentAccountName = findViewById(R.id.tvCurrentAccountName);
        rgPeriodDays = findViewById(R.id.rgPeriodDays);
        rbPeriod7 = findViewById(R.id.rbPeriod7);
        rbPeriod30 = findViewById(R.id.rbPeriod30);
        rbPeriod90 = findViewById(R.id.rbPeriod90);
        rbPeriodCustom = findViewById(R.id.rbPeriodCustom);
        etCustomPeriod = findViewById(R.id.etCustomPeriod);
        btnSaveSettings = findViewById(R.id.btnSaveSettings);

        // 工具栏返回按钮
        toolbar.setNavigationOnClickListener(v -> finish());

        // 显示当前账本
        tvCurrentAccountName.setText(
                accountId <= 0 ? "（未选择）" : accountName);

        // RadioGroup 切换监听（控制自定义输入框显隐）
        rgPeriodDays.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbPeriodCustom) {
                etCustomPeriod.setVisibility(View.VISIBLE);
            } else {
                etCustomPeriod.setVisibility(View.GONE);
            }
        });

        // 初始化 ViewModel 并读取当前设置
        viewModel = new ViewModelProvider(this).get(BudgetSettingsViewModel.class);
        loadCurrentSettings();

        // 保存按钮
        btnSaveSettings.setOnClickListener(v -> onSaveClicked());
    }

    /**
     * 读取该账本当前的预算设置，并回填到 UI
     */
    private void loadCurrentSettings() {
        if (accountId <= 0) {
            FileLogger.w(TAG, "accountId 无效，跳过读取");
            return;
        }
        viewModel.getSettingsFor(accountId).observe(this, settings -> {
            if (settings == null) return;
            FileLogger.i(TAG, "读取到设置：periodDays=" + settings.getPeriodDays());
            applySettingsToUi(settings);
        });
    }

    /**
     * 把 BudgetSettings 反映射到 RadioGroup
     */
    private void applySettingsToUi(BudgetSettings settings) {
        int days = settings.getPeriodDays();
        if (days == 7) {
            rbPeriod7.setChecked(true);
        } else if (days == 30) {
            rbPeriod30.setChecked(true);
        } else if (days == 90) {
            rbPeriod90.setChecked(true);
        } else {
            rbPeriodCustom.setChecked(true);
            etCustomPeriod.setText(String.valueOf(days));
        }
    }

    /**
     * 保存按钮点击
     */
    private void onSaveClicked() {
        int checkedId = rgPeriodDays.getCheckedRadioButtonId();
        if (checkedId == View.NO_ID) {
            Toast.makeText(this, "请选择周期", Toast.LENGTH_SHORT).show();
            return;
        }

        int periodDays;
        if (checkedId == R.id.rbPeriod7) {
            periodDays = 7;
        } else if (checkedId == R.id.rbPeriod30) {
            periodDays = 30;
        } else if (checkedId == R.id.rbPeriod90) {
            periodDays = 90;
        } else {
            // 自定义
            String text = etCustomPeriod.getText().toString().trim();
            if (TextUtils.isEmpty(text)) {
                Toast.makeText(this, R.string.hint_custom_period, Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                periodDays = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                Toast.makeText(this, R.string.error_period_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            if (periodDays < 7 || periodDays > 365) {
                Toast.makeText(this, R.string.error_period_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (accountId <= 0) {
            Toast.makeText(this, R.string.error_no_account, Toast.LENGTH_SHORT).show();
            return;
        }

        FileLogger.i(TAG, "保存设置：accountId=" + accountId + ", periodDays=" + periodDays);
        viewModel.setPeriodDays(accountId, periodDays);
        Toast.makeText(this, R.string.msg_settings_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ioExecutor != null && !ioExecutor.isShutdown()) {
            ioExecutor.shutdown();
        }
    }
}
