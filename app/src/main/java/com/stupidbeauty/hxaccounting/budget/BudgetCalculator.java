package com.stupidbeauty.hxaccounting.budget;

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
}