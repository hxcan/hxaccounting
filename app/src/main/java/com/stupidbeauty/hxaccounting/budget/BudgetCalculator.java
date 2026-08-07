package com.stupidbeauty.hxaccounting.budget;

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
 * 预算计算器（自适应窗口算法 v2）
 *
 * <p>负责根据历史支出计算预算，支持 3 个状态：
 * <ul>
 *   <li>NO_DATA：账本还没有流水</li>
 *   <li>COLLECTING_DATA：仅今天记了 1 笔（actualDays == 0）</li>
 *   <li>OK：正常状态，按自适应窗口计算</li>
 * </ul>
 *
 * <p><b>核心算法（自适应窗口 v2）</b>：
 * <pre>
 *   actualDays = 第一笔记账日 → 今天（不含今天）的天数
 *   if actualDays == 0:
 *       return COLLECTING_DATA
 *   elif actualDays < periodDays:
 *       dailyAvg = 总支出 / actualDays    // 冷启动期
 *   else:
 *       dailyAvg = 总支出 / periodDays    // 稳定期
 *   suggested = dailyAvg * rate
 * </pre>
 *
 * <p>关键设计点：
 * <ul>
 *   <li>✅ "今天"不计入分母（避免自己创造日均）</li>
 *   <li>✅ 中间无流水日计入分母（按主人原话）</li>
 *   <li>✅ 仅 1 天数据时显示"数据积累中"而不是乱算</li>
 *   <li>✅ 满周期后切回正常计算</li>
 * </ul>
 *
 * <p>这是太极记账相对其他 App 的核心优势：
 * 帮主人实时控制预算，主人在花下一笔钱时会"心里有数"。
 *
 * @author 未来姐姐
 * @since 2026-08-06
 * @updated 2026-08-08 自适应窗口算法 v2
 */
public class BudgetCalculator {

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
     * 一站式：从历史支出直接算出今日剩余预算（自适应窗口算法 v2）
     *
     * <p>算法步骤：
     * <ol>
     *   <li>如果流水为空 → NO_DATA</li>
     *   <li>计算 actualDays（第一笔记账日 → 今天，不含今天）</li>
     *   <li>如果 actualDays == 0 → COLLECTING_DATA</li>
     *   <li>根据 actualDays 和 periodDays 决定用哪个窗口</li>
     *   <li>计算日均 × 倍率 = 建议预算</li>
     *   <li>计算今日已花 → 剩余预算</li>
     * </ol>
     *
     * @param expenses   历史支出记录（全部，不限时间）
     * @param periodDays 周期（天），主人可在设置里调整
     * @param rate       倍率（> 0）
     * @param todaySpent 今日已花（元）
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

        // 1. 空数据检查
        if (expenses == null || expenses.isEmpty()) {
            return BudgetResult.noData(today);
        }

        // 2. 过滤有效支出
        List<ExpenseRecord> validExpenses = expenses.stream()
                .filter(r -> r != null && r.amount > 0)
                .filter(r -> !excludeAnomaly || !r.isAnomaly)
                .collect(Collectors.toList());

        if (validExpenses.isEmpty()) {
            return BudgetResult.noData(today);
        }

        // 3. 获取第一笔记账日期
        LocalDate firstDate = validExpenses.stream()
                .map(r -> Instant.ofEpochMilli(r.timestamp).atZone(SYSTEM_ZONE).toLocalDate())
                .min(LocalDate::compareTo)
                .orElseThrow();

        // 4. 计算已过去的天数（不含今天）
        // ChronoUnit.DAYS.between(a, b) = b - a，但不包含 b
        int actualDays = (int) ChronoUnit.DAYS.between(firstDate, today);

        // 5. 仅今天记了 1 笔（actualDays == 0）→ 数据积累中
        if (actualDays == 0) {
            return BudgetResult.collectingData(today);
        }

        // 6. 计算总支出
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

        // 8. 建议预算 = 日均 × 倍率
        double suggestedBudget = dailyAvg * rate;

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
