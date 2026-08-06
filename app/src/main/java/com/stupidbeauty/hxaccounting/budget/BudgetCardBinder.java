package com.stupidbeauty.hxaccounting.budget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;

import com.google.android.material.card.MaterialCardView;
import com.stupidbeauty.hxaccounting.R;
import com.stupidbeauty.hxaccounting.utils.FileLogger;

import java.util.Locale;

/**
 * 预算状态卡片绑定器
 *
 * <p>把 card_budget_status.xml 中的视图与 BudgetViewModel 绑定。
 * 用静态方法 + 显式 LifecycleOwner，避免强耦合 Activity。
 *
 * <p>UI 显示规则：
 * <ul>
 *   <li>剩余 ≥ 0 → 绿色进度条 + "剩余"标签</li>
 *   <li>剩余 < 0 → 红色进度条 + "已超支"标签</li>
 *   <li>使用率 ≥ 100% → 进度条变红</li>
 * </ul>
 *
 * @author 未来姐姐
 * @since 2026-08-06
 */
public class BudgetCardBinder {

    private static final String TAG = "BudgetCardBinder";

    private final MaterialCardView cardView;
    private final TextView tvRemaining;
    private final TextView tvRemainingLabel;
    private final TextView tvSuggested;
    private final TextView tvSpent;
    private final TextView tvWindowLabel;
    private final TextView tvUsagePercent;
    private final TextView btnAdjustRate;
    private final ProgressBar progressUsage;

    private final Context context;
    private final LifecycleOwner lifecycleOwner;
    private final BudgetViewModel viewModel;

    public BudgetCardBinder(Context context, LifecycleOwner owner,
                            MaterialCardView cardView, BudgetViewModel viewModel) {
        this.context = context;
        this.lifecycleOwner = owner;
        this.cardView = cardView;
        this.viewModel = viewModel;

        this.tvRemaining = cardView.findViewById(R.id.tvBudgetRemaining);
        this.tvRemainingLabel = cardView.findViewById(R.id.tvBudgetRemainingLabel);
        this.tvSuggested = cardView.findViewById(R.id.tvBudgetSuggested);
        this.tvSpent = cardView.findViewById(R.id.tvBudgetSpent);
        this.tvWindowLabel = cardView.findViewById(R.id.tvBudgetWindowLabel);
        this.tvUsagePercent = cardView.findViewById(R.id.tvBudgetUsagePercent);
        this.btnAdjustRate = cardView.findViewById(R.id.btnAdjustRate);
        this.progressUsage = cardView.findViewById(R.id.progressBudgetUsage);

        setupAdjustRateButton();
    }

    /**
     * 绑定 LiveData 到 UI
     */
    public void bind() {
        FileLogger.i(TAG, "绑定 BudgetViewModel LiveData 到 UI");
        LiveData<BudgetResult> liveData = viewModel.getBudgetResult();
        if (liveData == null) {
            FileLogger.w(TAG, "LiveData 为 null（账本未就绪？）");
            showEmptyState();
            return;
        }
        liveData.observe(lifecycleOwner, this::render);
    }

    /**
     * 渲染 BudgetResult 到 UI
     */
    private void render(BudgetResult result) {
        if (result == null) {
            showEmptyState();
            return;
        }

        FileLogger.d(TAG, String.format(Locale.US,
                "render: 建议=¥%.2f, 已花=¥%.2f, 剩余=¥%.2f, 使用率=%.1f%%",
                result.suggestedBudget, result.todaySpent,
                result.remaining, result.usagePercent));

        // 大字：剩余金额
        tvRemaining.setText(String.format(Locale.getDefault(),
                "%s¥%.2f",
                result.remaining < 0 ? "-" : "",
                Math.abs(result.remaining)));

        // 剩余标签
        if (result.remaining < 0) {
            tvRemainingLabel.setText("⚠ 已超支");
            tvRemaining.setTextColor(0xFFFF6B6B);  // 红
        } else {
            tvRemainingLabel.setText("剩余");
            tvRemaining.setTextColor(0xFFFFFFFF);  // 白
        }

        // 旁边：建议 / 已花
        tvSuggested.setText(String.format(Locale.getDefault(),
                "¥%.2f", result.suggestedBudget));
        tvSpent.setText(String.format(Locale.getDefault(),
                "¥%.2f", result.todaySpent));

        // 进度条（最大 100，但允许超支显示 100%）
        int progress = (int) Math.min(100, Math.round(result.usagePercent));
        progressUsage.setProgress(progress);
        // 颜色：使用率 < 80% 绿，80-100% 黄，> 100% 红
        if (result.usagePercent > 100) {
            progressUsage.setProgressTintList(ColorStateList.valueOf(0xFFFF6B6B));
        } else if (result.usagePercent > 80) {
            progressUsage.setProgressTintList(ColorStateList.valueOf(0xFFFFB84D));
        } else {
            progressUsage.setProgressTintList(ColorStateList.valueOf(0xFF26DE81));
        }

        // 使用率百分比
        tvUsagePercent.setText(String.format(Locale.getDefault(),
                "已用 %.0f%%", result.usagePercent));

        // 窗口标签
        tvWindowLabel.setText(String.format(Locale.getDefault(),
                "近7天 × 1.0"));
    }

    /**
     * 空状态显示（账本未选择 / 无数据）
     */
    private void showEmptyState() {
        tvRemaining.setText("¥--");
        tvRemainingLabel.setText("选择账本后显示");
        tvSuggested.setText("¥0.00");
        tvSpent.setText("¥0.00");
        tvUsagePercent.setText("已用 --");
        progressUsage.setProgress(0);
    }

    /**
     * 调整倍率按钮
     */
    private void setupAdjustRateButton() {
        btnAdjustRate.setOnClickListener(v -> showRateAdjustDialog());
    }

    /**
     * 显示倍率调整对话框
     */
    private void showRateAdjustDialog() {
        final String[] rates = {"0.5", "0.8", "1.0", "1.2", "1.5", "2.0"};
        final String[] labels = {
                "0.5（半价，极度节省）",
                "0.8（节省模式）",
                "1.0（标准，等于日均）",
                "1.2（宽松，多花两成）",
                "1.5（特别宽松）",
                "2.0（双倍预算）"
        };

        new AlertDialog.Builder(context)
                .setTitle("调整预算倍率")
                .setItems(labels, (dialog, which) -> {
                    double newRate = Double.parseDouble(rates[which]);
                    viewModel.setRate(newRate);
                    FileLogger.i(TAG, "倍率调整为: " + newRate);
                    Toast.makeText(context,
                            String.format(Locale.getDefault(), "倍率已设为 %.1f", newRate),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}