package com.stupidbeauty.hxaccounting.budget;

import com.stupidbeauty.hxaccounting.utils.FileLogger;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 单条支出记录（用于预算计算）
 *
 * <p>这是 BudgetCalculator 的输入数据结构的简化版。
 * 完整的事务实体在 {@code TransactionEntity} 中，
 * 这里只取预算计算需要的字段。
 */
class ExpenseRecord {
    /** 金额（单位：元） */
    public final double amount;
    /** 时间戳（毫秒） */
    public final long timestamp;
    /** 是否异常支出（异常支出会被排除，不计入日均） */
    public final boolean isAnomaly;

    public ExpenseRecord(double amount, long timestamp, boolean isAnomaly) {
        this.amount = amount;
        this.timestamp = timestamp;
        this.isAnomaly = isAnomaly;
    }
}

/**
 * 预算计算器（自适应窗口算法 v2.1）
 *
 * <p>负责根据历史支出计算预算，支持 3 个状态：
 * <ul>
 *   <li>NO_DATA：账本还没有流水</li>
 *   <li>COLLECTING_DATA：仅今天记了 1 笔（actualDays == 0）</li>
 *   <li>OK：正常状态，按自适应窗口计算</li>
 * </ul>
 *
 * <p><b>核心算法（自适应窗口 v2.1）</b>：
 * <pre>
 *   actualDays = 第一笔记账日 → 昨天（不含今天）的天数
 *   total      = 历史支出总和（不含今天的流水）
 *   if actualDays == 0:
 *       return COLLECTING_DATA
 *   elif actualDays < periodDays:
 *       dailyAvg = total / actualDays    // 冷启动期
 *   else:
 *       dailyAvg = total / periodDays    // 稳定期
 *   suggested = dailyAvg * rate
 *   remaining = suggested - todaySpent   // 今日已花单独算
 * </pre>
 *
 * <p><b>v2.1 关键修复（2026-08-08 主人验收反馈）</b>：
 * <ul>
 *   <li>✅ "今天"既不计入分子也不计入分母</li>
 *   <li>✅ 分子：窗口期内的支出（严格按"昨天为止"过滤）</li>
 *   <li>✅ 分母：第一笔记账日 → 昨天（不含今天）的天数</li>
 *   <li>✅ 今日已花（todaySpent）单独用于算 remaining</li>
 *   <li>✅ 避免"分子含今天 + 分母不含今天"造成的日均放大 bug</li>
 * </ul>
 *
 * <p><b>主人原话（2026-08-08）</b>：
 * "今天正是此刻正在发生的事情，就是我们能够通过控制来缩减开支或者保持开支
 *  或者说扩大开支的手段。"
 *
 * <p>理解：今天的预算不是历史算出来的——而是通过历史日均**指导**主人今天该花多少。
 * 今天的实际支出由主人实时控制。所以"今天"完全不应该参与"建议预算"的计算公式。
 * 它只参与"剩余预算 = 建议预算 - 今日已花"这一步。
 *
 * @author 未来姐姐
 * @since 2026-08-06
 * @updated 2026-08-08 自适应窗口算法 v2.1（今天完全不算）
 * @updated 2026-08-08 v2 调试日志：关键分支加 FileLogger 输出（任务 #861693812595）
 */
public class BudgetCalculator {

    private static final String TAG = "BudgetCalculator";

    /**
     * 默认倍率（1.0 = 等于日均）
     */
    public static final double DEFAULT_RATE = 1.0;

    /**
     * 默认周期（30 天，主人 2026-08-08 拍板）
     */
    public static final int DEFAULT_PERIOD_DAYS = 30;

    /**
     * 系统默认时区（避免重复创建）
     */
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    /**
     * 一站式：从历史支出直接算出今日剩余预算（自适应窗口算法 v2.1）
     *
     * <p>算法步骤：
     * <ol>
     *   <li>如果流水为空 → NO_DATA</li>
     *   <li>过滤出"今天以前"的支出（严格剔除今天的流水）</li>
     *   <li>如果过滤后为空 → COLLECTING_DATA（今天才刚开始记账）</li>
     *   <li>计算 actualDays（第一笔记账日 → 昨天，不含今天）</li>
     *   <li>如果 actualDays == 0 → COLLECTING_DATA</li>
     *   <li>根据 actualDays 和 periodDays 决定用哪个窗口</li>
     *   <li>计算日均 × 倍率 = 建议预算（基于历史，不含今天）</li>
     *   <li>计算 remaining = suggested - todaySpent（今天单独算）</li>
     * </ol>
     *
     * @param expenses   历史支出记录（全部，含今天的——会在内部过滤掉）
     * @param periodDays 周期（天），主人可在设置里调整
     * @param rate       倍率（> 0）
     * @param todaySpent 今日已花（元）——只用于算 remaining，不参与日均
     * @param today      基准日期（通常 = LocalDate.now()，但允许测试时注入）
     * @param excludeAnomaly 是否排除异常支出
     * @return BudgetResult（含 status、suggested、remaining 等）
     */
    public static BudgetResult calculateFromHistory(
            List<ExpenseRecord> expenses,
            int periodDays,
            double rate,
            double todaySpent,
            LocalDate today,
            boolean excludeAnomaly) {

        if (periodDays <= 0) {
            throw new IllegalArgumentException("periodDays must be > 0, got: " + periodDays);
        }
        if (rate <= 0) {
            throw new IllegalArgumentException("rate must be > 0, got: " + rate);
        }

        // v2 调试：入口参数日志（任务 #861693812595 - 排查空状态显示问题）
        FileLogger.i(TAG, "calculateFromHistory 入口: expenses.size=" + (expenses == null ? 0 : expenses.size())
                + ", periodDays=" + periodDays + ", rate=" + rate
                + ", todaySpent=" + todaySpent + ", today=" + today
                + ", excludeAnomaly=" + excludeAnomaly);

        // 1. 空数据检查
        if (expenses == null || expenses.isEmpty()) {
            FileLogger.w(TAG, "calculateFromHistory: 原始 expenses 为空，返回 NO_DATA");
            return BudgetResult.noData(today);
        }

        // 2. 过滤有效支出 + v2.1 修复：剔除今天的流水
        //    "今天"是主人实时控制的变量，不应该污染历史日均
        final LocalDate todayFinal = today;
        List<ExpenseRecord> validExpenses = expenses.stream()
                .filter(r -> r != null && r.amount > 0)
                .filter(r -> !excludeAnomaly || !r.isAnomaly)
                .filter(r -> {
                    LocalDate recordDate = Instant.ofEpochMilli(r.timestamp)
                            .atZone(SYSTEM_ZONE).toLocalDate();
                    return recordDate.isBefore(todayFinal); // 严格小于今天
                })
                .collect(Collectors.toList());

        if (validExpenses.isEmpty()) {
            // v2 调试：所有流水都在今天（今天才刚开始记账）
            FileLogger.w(TAG, "calculateFromHistory: 过滤今天后 validExpenses 为空，返回 COLLECTING_DATA"
                    + " (原始 expenses.size=" + expenses.size() + ")");
            return BudgetResult.collectingData(today);
        }

        // 3. 获取第一笔记账日期
        LocalDate firstDate = validExpenses.stream()
                .map(r -> Instant.ofEpochMilli(r.timestamp).atZone(SYSTEM_ZONE).toLocalDate())
                .min(LocalDate::compareTo)
                .orElseThrow();

        // 4. 计算已过去的天数（不含今天，因为今天的数据已全部被过滤掉）
        //    ChronoUnit.DAYS.between(a, b) = b - a，但不包含 b
        //    现在 lastDate < today，所以 between(firstDate, today) 给出正确的不含今天的天数
        int actualDays = (int) ChronoUnit.DAYS.between(firstDate, today);

        // v2 调试：第一笔记账日和实际天数
        FileLogger.d(TAG, "calculateFromHistory: firstDate=" + firstDate
                + ", today=" + today + ", actualDays=" + actualDays
                + ", validExpenses.size=" + validExpenses.size());

        // 5. 仅今天记了 1 笔（actualDays == 0）→ 数据积累中
        //    防御性检查：万一过滤后还有 lastDate == today 的边角情况
        if (actualDays == 0) {
            FileLogger.w(TAG, "calculateFromHistory: actualDays==0，返回 COLLECTING_DATA"
                    + " (firstDate=" + firstDate + ", today=" + today + ")");
            return BudgetResult.collectingData(today);
        }

        // 6. 计算总支出（不含今天）
        double total = validExpenses.stream()
                .mapToDouble(r -> r.amount)
                .sum();

        // 7. 自适应窗口：冷启动期用 actualDays，稳定期用 periodDays
        double dailyAvg;
        if (actualDays < periodDays) {
            dailyAvg = total / actualDays;
        } else {
            dailyAvg = total / periodDays;
        }

        // 8. 建议预算 = 日均 × 倍率（基于历史，今天不参与）
        double suggestedBudget = dailyAvg * rate;

        // v2 调试：OK 分支输出关键计算结果
        FileLogger.i(TAG, String.format(java.util.Locale.US,
                "calculateFromHistory: OK 分支 total=%.2f, dailyAvg=%.2f, suggested=%.2f"
                        + " (actualDays=%d, periodDays=%d, isColdStart=%s)",
                total, dailyAvg, suggestedBudget, actualDays, periodDays,
                actualDays < periodDays));

        return BudgetResult.ok(suggestedBudget, todaySpent, actualDays, periodDays, today);
    }

    /**
     * 便捷重载：使用默认倍率 1.0
     */
    public static BudgetResult calculateFromHistory(
            List<ExpenseRecord> expenses,
            int periodDays,
            double todaySpent,
            LocalDate today,
            boolean excludeAnomaly) {
        return calculateFromHistory(expenses, periodDays, DEFAULT_RATE, todaySpent, today, excludeAnomaly);
    }

    /**
     * 最简重载：使用默认倍率 + 默认周期 30 天
     */
    public static BudgetResult calculateFromHistory(
            List<ExpenseRecord> expenses,
            double todaySpent,
            LocalDate today,
            boolean excludeAnomaly) {
        return calculateFromHistory(expenses, DEFAULT_PERIOD_DAYS, DEFAULT_RATE, todaySpent, today, excludeAnomaly);
    }
}