package com.stupidbeauty.hxaccounting.budget;

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
 *
 * <p>这是太极记账相对其他 App 的核心优势：
 * 帮主人实时控制预算，主人在花下一笔钱时会"心里有数"。
 *
 * @author 未来姐姐
 * @since 2026-08-06
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