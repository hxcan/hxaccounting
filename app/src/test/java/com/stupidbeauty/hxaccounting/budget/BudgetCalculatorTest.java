package com.stupidbeauty.hxaccounting.budget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * BudgetCalculator 单元测试
 *
 * <p>覆盖场景：
 * <ol>
 *   <li>正常窗口期支出</li>
 *   <li>空数据 / null</li>
 *   <li>异常支出被排除</li>
 *   <li>边界：windowSize <= 0 抛异常</li>
 *   <li>倍率计算（0.8 / 1.0 / 1.2）</li>
 *   <li>完整链路 calculateFromHistory</li>
 *   <li>今日剩余 + 使用百分比</li>
 * </ol>
 *
 * @author 未来姐姐
 * @since 2026-08-06
 */
public class BudgetCalculatorTest {

    /**
     * 测试 1：正常窗口期支出
     * 7 天窗口，总支出 700，日均应该是 100
     */
    @Test
    public void calculateDailyAvg_normalWindow() {
        List<ExpenseRecord> expenses = Arrays.asList(
                new ExpenseRecord(100.0, 0L, false),
                new ExpenseRecord(150.0, 0L, false),
                new ExpenseRecord(80.0, 0L, false),
                new ExpenseRecord(120.0, 0L, false),
                new ExpenseRecord(90.0, 0L, false),
                new ExpenseRecord(60.0, 0L, false),
                new ExpenseRecord(100.0, 0L, false)
        );
        double avg = BudgetCalculator.calculateDailyAvg(expenses, 7, true);
        assertEquals(100.0, avg, 0.001);
    }

    /**
     * 测试 2：空列表返回 0
     */
    @Test
    public void calculateDailyAvg_emptyList() {
        double avg = BudgetCalculator.calculateDailyAvg(
                Collections.<ExpenseRecord>emptyList(), 7, true);
        assertEquals(0.0, avg, 0.001);
    }

    /**
     * 测试 3：null 列表返回 0（不抛异常）
     */
    @Test
    public void calculateDailyAvg_nullList() {
        double avg = BudgetCalculator.calculateDailyAvg(null, 7, true);
        assertEquals(0.0, avg, 0.001);
    }

    /**
     * 测试 4：异常支出被排除（excludeAnomaly=true）
     * 总支出 700，其中 200 是异常，剩 500 ÷ 7 ≈ 71.43
     */
    @Test
    public void calculateDailyAvg_excludeAnomaly() {
        List<ExpenseRecord> expenses = Arrays.asList(
                new ExpenseRecord(100.0, 0L, false),
                new ExpenseRecord(150.0, 0L, false),
                new ExpenseRecord(80.0, 0L, false),
                new ExpenseRecord(120.0, 0L, false),
                new ExpenseRecord(90.0, 0L, false),
                new ExpenseRecord(60.0, 0L, false),
                new ExpenseRecord(100.0, 0L, true)  // 异常
        );
        double avg = BudgetCalculator.calculateDailyAvg(expenses, 7, true);
        assertEquals((100 + 150 + 80 + 120 + 90 + 60) / 7.0, avg, 0.001);
    }

    /**
     * 测试 5：异常支出不被排除（excludeAnomaly=false）
     * 总支出 700（含异常）÷ 7 = 100
     */
    @Test
    public void calculateDailyAvg_includeAnomaly() {
        List<ExpenseRecord> expenses = Arrays.asList(
                new ExpenseRecord(100.0, 0L, false),
                new ExpenseRecord(150.0, 0L, false),
                new ExpenseRecord(80.0, 0L, false),
                new ExpenseRecord(120.0, 0L, false),
                new ExpenseRecord(90.0, 0L, false),
                new ExpenseRecord(60.0, 0L, false),
                new ExpenseRecord(100.0, 0L, true)  // 异常但保留
        );
        double avg = BudgetCalculator.calculateDailyAvg(expenses, 7, false);
        assertEquals(700.0 / 7.0, avg, 0.001);
    }

    /**
     * 测试 6：windowSize <= 0 抛 IllegalArgumentException
     */
    @Test
    public void calculateDailyAvg_invalidWindowSize() {
        assertThrows(IllegalArgumentException.class, () ->
                BudgetCalculator.calculateDailyAvg(
                        Collections.<ExpenseRecord>emptyList(), 0, true));
        assertThrows(IllegalArgumentException.class, () ->
                BudgetCalculator.calculateDailyAvg(
                        Collections.<ExpenseRecord>emptyList(), -1, true));
    }

    /**
     * 测试 7：负金额不计入支出（只算 amount > 0）
     */
    @Test
    public void calculateDailyAvg_negativeAmountIgnored() {
        List<ExpenseRecord> expenses = Arrays.asList(
                new ExpenseRecord(100.0, 0L, false),
                new ExpenseRecord(-50.0, 0L, false),  // 负数（收入？退款？）
                new ExpenseRecord(200.0, 0L, false)
        );
        double avg = BudgetCalculator.calculateDailyAvg(expenses, 7, true);
        // (100 + 200) / 7 = 42.857
        assertEquals(300.0 / 7.0, avg, 0.001);
    }

    // ============== calculateDailyBudget 测试 ==============

    /**
     * 测试 8：倍率 1.0 = 等于日均
     */
    @Test
    public void calculateDailyBudget_rateOne() {
        assertEquals(100.0,
                BudgetCalculator.calculateDailyBudget(100.0, 1.0), 0.001);
    }

    /**
     * 测试 9：倍率 0.8 = 节省模式（八折）
     */
    @Test
    public void calculateDailyBudget_rateLow() {
        assertEquals(80.0,
                BudgetCalculator.calculateDailyBudget(100.0, 0.8), 0.001);
    }

    /**
     * 测试 10：倍率 1.2 = 宽松模式（多花两成）
     */
    @Test
    public void calculateDailyBudget_rateHigh() {
        assertEquals(120.0,
                BudgetCalculator.calculateDailyBudget(100.0, 1.2), 0.001);
    }

    /**
     * 测试 11：rate <= 0 抛异常
     */
    @Test
    public void calculateDailyBudget_invalidRate() {
        assertThrows(IllegalArgumentException.class, () ->
                BudgetCalculator.calculateDailyBudget(100.0, 0));
        assertThrows(IllegalArgumentException.class, () ->
                BudgetCalculator.calculateDailyBudget(100.0, -1.0));
    }

    // ============== calculateTodayRemaining 测试 ==============

    /**
     * 测试 12：正常剩余计算
     * 建议 ¥100，已花 ¥30，剩余 ¥70，使用率 30%
     */
    @Test
    public void calculateTodayRemaining_normal() {
        BudgetResult result = BudgetCalculator.calculateTodayRemaining(100.0, 30.0);
        assertEquals(100.0, result.suggestedBudget, 0.001);
        assertEquals(30.0, result.todaySpent, 0.001);
        assertEquals(70.0, result.remaining, 0.001);
        assertEquals(30.0, result.usagePercent, 0.001);
    }

    /**
     * 测试 13：超支场景（remaining 为负）
     * 建议 ¥100，已花 ¥150，超支 ¥50，使用率 150%
     */
    @Test
    public void calculateTodayRemaining_overspend() {
        BudgetResult result = BudgetCalculator.calculateTodayRemaining(100.0, 150.0);
        assertEquals(-50.0, result.remaining, 0.001);
        assertEquals(150.0, result.usagePercent, 0.001);
    }

    /**
     * 测试 14：建议预算为 0 时，使用百分比为 0（避免除零）
     */
    @Test
    public void calculateTodayRemaining_zeroBudget() {
        BudgetResult result = BudgetCalculator.calculateTodayRemaining(0.0, 50.0);
        assertEquals(-50.0, result.remaining, 0.001);
        assertEquals(0.0, result.usagePercent, 0.001);
    }

    // ============== calculateFromHistory 一站式测试 ==============

    /**
     * 测试 15：完整链路
     * 历史日均 ¥100 × 倍率 1.0 = 建议 ¥100
     * 今日已花 ¥30 → 剩余 ¥70（30%）
     */
    @Test
    public void calculateFromHistory_fullChain() {
        List<ExpenseRecord> expenses = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            expenses.add(new ExpenseRecord(100.0, 0L, false));
        }

        BudgetResult result = BudgetCalculator.calculateFromHistory(
                expenses, 7, 1.0, 30.0, true);

        assertEquals(100.0, result.suggestedBudget, 0.001);
        assertEquals(30.0, result.todaySpent, 0.001);
        assertEquals(70.0, result.remaining, 0.001);
        assertEquals(30.0, result.usagePercent, 0.001);
    }

    /**
     * 测试 16：空历史数据 → 剩余 = -todaySpent
     */
    @Test
    public void calculateFromHistory_emptyData() {
        BudgetResult result = BudgetCalculator.calculateFromHistory(
                Collections.<ExpenseRecord>emptyList(), 7, 1.0, 50.0, true);
        assertEquals(0.0, result.suggestedBudget, 0.001);
        assertEquals(50.0, result.todaySpent, 0.001);
        assertEquals(-50.0, result.remaining, 0.001);
    }

    /**
     * 测试 17：教育基金场景（主人实际用例）
     * 历史日均 ¥50，倍率 1.0，已花 ¥30
     * 预期：剩余 ¥20（40%）
     */
    @Test
    public void calculateFromHistory_educationFundScenario() {
        List<ExpenseRecord> expenses = Arrays.asList(
                new ExpenseRecord(50.0, 0L, false),
                new ExpenseRecord(50.0, 0L, false),
                new ExpenseRecord(50.0, 0L, false),
                new ExpenseRecord(50.0, 0L, false),
                new ExpenseRecord(50.0, 0L, false),
                new ExpenseRecord(50.0, 0L, false),
                new ExpenseRecord(50.0, 0L, false)
        );

        BudgetResult result = BudgetCalculator.calculateFromHistory(
                expenses, 7, 1.0, 30.0, true);

        assertEquals(50.0, result.suggestedBudget, 0.001);
        assertEquals(30.0, result.todaySpent, 0.001);
        assertEquals(20.0, result.remaining, 0.001);
        assertEquals(60.0, result.usagePercent, 0.001);  // 30/50 = 60%

        // 验证主人最关心的：剩余 ¥20
        assertTrue("剩余应该 > 0", result.remaining > 0);
    }
}