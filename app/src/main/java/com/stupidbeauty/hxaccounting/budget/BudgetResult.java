package com.stupidbeauty.hxaccounting.budget;

import java.time.LocalDate;

/**
 * 预算结果：今日剩余预算（自适应窗口算法 v2）
 *
 * <p>包含：
 * <ul>
 *   <li>status：状态（NO_DATA / COLLECTING_DATA / OK）</li>
 *   <li>suggestedBudget：今日建议预算（日均 × 倍率）</li>
 *   <li>todaySpent：今日已花（流水表实时 SUM）</li>
 *   <li>remaining：今日剩余（suggested - spent）</li>
 *   <li>usagePercent：使用百分比（spent / suggested × 100）</li>
 *   <li>actualDays：实际记账天数（第一笔记账日 → 今天，不含今天）</li>
 *   <li>periodDays：周期（默认 30 天）</li>
 *   <li>isColdStart：是否冷启动期（actualDays < periodDays）</li>
 *   <li>today：基准日期</li>
 * </ul>
 *
 * <p>状态说明：
 * <ul>
 *   <li>NO_DATA：账本还没有任何流水</li>
 *   <li>COLLECTING_DATA：仅今天记了 1 笔（actualDays == 0），显示"数据积累中"</li>
 *   <li>OK：正常状态，可以计算剩余预算</li>
 * </ul>
 *
 * <p>这是太极记账相对其他 App 的核心优势：
 * 帮主人实时控制预算，主人在花下一笔钱时会"心里有数"。
 *
 * @author 未来姐姐
 * @since 2026-08-06
 * @updated 2026-08-08 自适应窗口算法 v2
 */
public class BudgetResult {

    /**
     * 预算状态
     */
    public enum Status {
        /** 账本还没有任何流水 */
        NO_DATA,
        /** 仅今天记了 1 笔（actualDays == 0），数据积累中 */
        COLLECTING_DATA,
        /** 正常状态 */
        OK
    }

    public final Status status;
    public final double suggestedBudget;
    public final double todaySpent;
    public final double remaining;
    public final double usagePercent;
    public final int actualDays;
    public final int periodDays;
    public final boolean isColdStart;
    public final LocalDate today;

    /**
     * 完整构造函数（OK 状态）
     */
    public BudgetResult(double suggestedBudget, double todaySpent, int actualDays, int periodDays, LocalDate today) {
        this.status = Status.OK;
        this.suggestedBudget = suggestedBudget;
        this.todaySpent = todaySpent;
        this.remaining = suggestedBudget - todaySpent;
        this.usagePercent = suggestedBudget > 0
                ? (todaySpent / suggestedBudget) * 100.0
                : 0.0;
        this.actualDays = actualDays;
        this.periodDays = periodDays;
        this.isColdStart = actualDays < periodDays;
        this.today = today;
    }

    /**
     * 私有构造函数（用于 NO_DATA / COLLECTING_DATA 状态）
     */
    private BudgetResult(Status status, LocalDate today) {
        this.status = status;
        this.suggestedBudget = 0.0;
        this.todaySpent = 0.0;
        this.remaining = 0.0;
        this.usagePercent = 0.0;
        this.actualDays = 0;
        this.periodDays = 0;
        this.isColdStart = false;
        this.today = today;
    }

    /**
     * 静态工厂：空数据状态（账本还没有流水）
     */
    public static BudgetResult noData(LocalDate today) {
        return new BudgetResult(Status.NO_DATA, today);
    }

    /**
     * 静态工厂：数据积累中（仅今天记了 1 笔）
     */
    public static BudgetResult collectingData(LocalDate today) {
        return new BudgetResult(Status.COLLECTING_DATA, today);
    }

    /**
     * 静态工厂：正常状态
     */
    public static BudgetResult ok(double suggestedBudget, double todaySpent, int actualDays, int periodDays, LocalDate today) {
        return new BudgetResult(suggestedBudget, todaySpent, actualDays, periodDays, today);
    }
}
