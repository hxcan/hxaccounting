package com.stupidbeauty.hxaccounting.budget;

import java.util.List;

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
 * 预算结果：今日剩余预算
 *
 * <p>包含：
 * <ul>
 *   <li>suggestedBudget：今日建议预算（日均 × 倍率）</li>
 *   <li>todaySpent：今日已花（流水表实时 SUM）</li>
 *   <li>remaining：今日剩余（suggested - spent）</li>
 *   <li>usagePercent：使用百分比（spent / suggested × 100）</li>
 * </ul>
 */
public class BudgetResult {
    public final double suggestedBudget;
    public final double todaySpent;
    public final double remaining;
    public final double usagePercent;

    public BudgetResult(double suggestedBudget, double todaySpent) {
        this.suggestedBudget = suggestedBudget;
        this.todaySpent = todaySpent;
        this.remaining = suggestedBudget - todaySpent;
        this.usagePercent = suggestedBudget > 0
                ? (todaySpent / suggestedBudget) * 100.0
                : 0.0;
    }
}

/**
 * 预算计算器
 *
 * <p>负责根据历史支出计算：
 * <ul>
 *   <li>日均支出（calculateDailyAvg）</li>
 *   <li>建议日预算（calculateDailyBudget）</li>
 *   <li>今日剩余预算（calculateTodayRemaining）</li>
 * </ul>
 *
 * <p>核心公式：
 * <pre>
 *   明日预算   = 历史日均支出 × 倍率
 *   今日剩余   = 今日建议预算 - 今日已花
 * </pre>
 *
 * <p>这是太极记账相对其他 App 的核心优势：
 * 帮主人实时控制预算，主人在花下一笔钱时会"心里有数"。
 *
 * @author 未来姐姐
 * @since 2026-08-06
 */
public class BudgetCalculator {

    /**
     * 默认倍率（1.0 = 等于日均）
     */
    public static final double DEFAULT_RATE = 1.0;

    /**
     * 默认窗口大小（天）
     */
    public static final int DEFAULT_WINDOW_SIZE = 7;

    /**
     * 计算日均支出
     *
     * <p>从给定窗口（天数）内的支出记录中：
     * <ol>
     *   <li>排除异常支出（isAnomaly == true）</li>
     *   <li>只保留支出（amount > 0）</li>
     *   <li>总金额 ÷ 窗口天数 = 日均</li>
     * </ol>
     *
     * @param expenses       窗口内的支出记录列表
     * @param windowSize     窗口大小（天），建议 7 或 30
     * @param excludeAnomaly 是否排除异常支出
     * @return 日均支出（元/天）。如果窗口内无有效支出，返回 0。
     */
    public static double calculateDailyAvg(
            List<ExpenseRecord> expenses,
            int windowSize,
            boolean excludeAnomaly) {

        if (windowSize <= 0) {
            throw new IllegalArgumentException("windowSize must be > 0, got: " + windowSize);
        }

        if (expenses == null || expenses.isEmpty()) {
            return 0.0;
        }

        double total = 0.0;
        for (ExpenseRecord record : expenses) {
            if (record.amount <= 0) {
                continue;
            }
            if (excludeAnomaly && record.isAnomaly) {
                continue;
            }
            total += record.amount;
        }

        return total / windowSize;
    }

    /**
     * 计算建议日预算
     *
     * <p>公式：日均支出 × 倍率
     *
     * <p>典型用法：
     * <ul>
     *   <li>倍率 1.0 → 等于日均（最常用）</li>
     *   <li>倍率 0.8 → 节省模式（打八折）</li>
     *   <li>倍率 1.2 → 宽松模式（多花两成）</li>
     * </ul>
     *
     * @param dailyAvg 历史日均支出（元/天）
     * @param rate     倍率（必须 &gt; 0）
     * @return 建议日预算（元）
     */
    public static double calculateDailyBudget(double dailyAvg, double rate) {
        if (rate <= 0) {
            throw new IllegalArgumentException("rate must be > 0, got: " + rate);
        }
        return dailyAvg * rate;
    }

    /**
     * 计算今日剩余预算
     *
     * <p>公式：今日建议预算 - 今日已花
     *
     * <p>这是主人最关心的数字！
     * 主人在花下一笔钱时会"心里有数"。
     *
     * <p>UI 显示建议：
     * <pre>
     * 剩余 ¥20 \/ ¥50 (40%)
     * ████░░░░░░
     * </pre>
     *
     * @param suggestedBudget 今日建议预算（元）
     * @param todaySpent      今日已花（元，从流水表实时 SUM）
     * @return BudgetResult 包含建议、已花、剩余、使用百分比
     */
    public static BudgetResult calculateTodayRemaining(
            double suggestedBudget, double todaySpent) {
        return new BudgetResult(suggestedBudget, todaySpent);
    }

    /**
     * 一站式：从历史支出直接算出今日剩余预算
     *
     * <p>组合调用：
     * <ol>
     *   <li>calculateDailyAvg → 日均</li>
     *   <li>calculateDailyBudget(日均, 倍率) → 建议预算</li>
     *   <li>calculateTodayRemaining(建议, 今日已花) → 剩余</li>
     * </ol>
     *
     * @param expenses       窗口内的历史支出记录
     * @param windowSize     窗口大小（天）
     * @param rate           倍率（&gt; 0）
     * @param todaySpent     今日已花（元）
     * @param excludeAnomaly 是否排除异常支出
     * @return BudgetResult
     */
    public static BudgetResult calculateFromHistory(
            List<ExpenseRecord> expenses,
            int windowSize,
            double rate,
            double todaySpent,
            boolean excludeAnomaly) {

        double dailyAvg = calculateDailyAvg(expenses, windowSize, excludeAnomaly);
        double suggested = calculateDailyBudget(dailyAvg, rate);
        return calculateTodayRemaining(suggested, todaySpent);
    }
}