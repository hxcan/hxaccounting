package com.stupidbeauty.hxaccounting.budget;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * BudgetCalculator 单元测试
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>正常窗口期日均计算</li>
 *   <li>空数据边界</li>
 *   <li>全异常支出（应被排除）</li>
 *   <li>windowSize 非法值</li>
 *   <li>倍率非法值</li>
 *   <li>calculateFromHistory 完整链路</li>
 *   <li>BudgetResult 使用百分比</li>
 * </ul>
 *
 * @author 未来姐姐
 */
public class BudgetCalculatorTest {

    // ========== calculateDailyAvg 测试 ==========

    /**
     * 正常场景：7天窗口、总支出 700 → 日均 100
     */
    @Test
    public void testDailyAvg_normalWindow() {
        List<ExpenseRecord> expenses = Arrays.asList(
                new ExpenseRecord(100, 0L, false),
                new ExpenseRecord(200, 0L, false),
                new ExpenseRecord(400, 0L, false)
        );
        double result = BudgetCalculator.calculateDailyAvg(expenses, 7, true);
        assertEquals(100.0, result, 0.001);
    }

    /**
     * 边界：空列表 → 返回 0
     */
    @Test
    public void testDailyAvg_emptyList() {
        double result = BudgetCalculator.calculateDailyAvg(
                Collections.emptyList(), 7, true);
        assertEquals(0.0, result, 0.001);
    }

    /**
     * 边界：null 列表 → 返回 0
     */
    @Test
    public void testDailyAvg_nullList() {
        double result = BudgetCalculator.calculateDailyAvg(null, 7, true);
        assertEquals(0.0, result, 0.001);
    }

    /**
     * 异常支出被排除：3 条都是异常 → 日均 0
     */
    @Test
    public void testDailyAvg_allAnomalyExcluded() {
        List<ExpenseRecord> expenses = Arrays.asList(
                new ExpenseRecord(100, 0L, true),
                new ExpenseRecord(200, 0L, true),
                new ExpenseRecord(300, 0L, true)
        );
        double result = BudgetCalculator.calculateDailyAvg(expenses, 7, true);
        assertEquals(0.0, result, 0.001);
    }

    /**
     * 异常支出被排除：混合场景
     * 总支出 1000，其中 300 是异常
     * 排除后 700 / 7 = 100
     */
    @Test
    public void testDailyAvg_mixedWithAnomaly() {
        List<ExpenseRecord> expenses = Arrays.asList(
                new ExpenseRecord(100, 0L, false),
                new ExpenseRecord(300, 0L, true),   // 异常，应排除
                new ExpenseRecord(600, 0L, false)
        );
        double result = BudgetCalculator.calculateDailyAvg(expenses, 7, true);
        assertEquals(100.0, result, 0.001);
    }

    /**
     * 异常支出不被排除（excludeAnomaly=false）
     * 总支出 1000 / 7 ≈ 142.857
     */
    @Test
    public void testDailyAvg_includeAnomaly() {
        List<ExpenseRecord> expenses = Arrays.asList(
                new ExpenseRecord(100, 0L, false),
                new ExpenseRecord(300, 0L, true),
                new ExpenseRecord(600, 0L, false)
        );
        double result = BudgetCalculator.calculateDailyAvg(expenses, 7, false);
        assertEquals(1000.0 / 7.0, result, 0.001);
    }

    /**
     * 负数金额（收入）被忽略
     */
    @Test
    public void testDailyAvg_ignoreNegative() {
        List<ExpenseRecord> expenses = Arrays.asList(
                new ExpenseRecord(-500, 0L, false),  // 收入，忽略
                new ExpenseRecord(700, 0L, false)   // 支出
        );
        double result = BudgetCalculator.calculateDailyAvg(expenses, 7, true);
        assertEquals(100.0, result, 0.001);
    }

    /**
     * 边界：windowSize = 0 → 抛 IllegalArgumentException
     */
    @Test
    public void testDailyAvg_zeroWindowSize() {
        List<ExpenseRecord> expenses = Arrays.asList(
                new ExpenseRecord(100, 0L, false)
        );
        assertThrows(IllegalArgumentException.class, () ->
                BudgetCalculator.calculateDailyAvg(expenses, 0, true));
    }

    /**
     * 边界：windowSize < 0 → 抛 IllegalArgumentException
     */
    @Test
    public void testDailyAvg_negativeWindowSize() {
        List<ExpenseRecord> expenses = Arrays.asList(
                new ExpenseRecord(100, 0L, false)
        );
        assertThrows(IllegalArgumentException.class, () ->
                BudgetCalculator.calculateDailyAvg(expenses, -1, true));
    }

    // ========== calculateDailyBudget 测试 ==========

    /**
     * 正常：日均 100 × 1.0 = 100
     */
    @Test
    public void testDailyBudget_defaultRate() {
        double result = BudgetCalculator.calculateDailyBudget(100.0, 1.0);
        assertEquals(100.0, result, 0.001);
    }

    /**
     * 节省模式：日均 100 × 0.8 = 80
     */
    @Test
    public void testDailyBudget_saveMode() {
        double result = BudgetCalculator.calculateDailyBudget(100.0, 0.8);
        assertEquals(80.0, result, 0.001);
    }

    /**
     * 宽松模式：日均 100 × 1.2 = 120
     */
    @Test
    public void testDailyBudget_looseMode() {
        double result = BudgetCalculator.calculateDailyBudget(100.0, 1.2);
        assertEquals(120.0, result, 0.001);
    }

    /**
     * 边界：rate = 0 → 抛 IllegalArgumentException
     */
    @Test
    public void testDailyBudget_zeroRate() {
        assertThrows(IllegalArgumentException.class, () ->
                BudgetCalculator.calculateDailyBudget(100.0, 0.0));
    }

    /**
     * 边界：rate < 0 → 抛 IllegalArgumentException
     */
    @Test
    public void testDailyBudget_negativeRate() {
        assertThrows(IllegalArgumentException.class, () ->
                BudgetCalculator.calculateDailyBudget(100.0, -1.0));
    }

    // ========== calculateTodayRemaining / BudgetResult 测试 ==========

    /**
     * 正常：建议 100，已花 30 → 剩余 70，使用率 30%
     */
    @Test
    public void testTodayRemaining_normal() {
        BudgetResult result = BudgetCalculator.calculateTodayRemaining(100.0, 30.0);
        assertEquals(100.0, result.suggestedBudget, 0.001);
        assertEquals(30.0, result.todaySpent, 0.001);
        assertEquals(70.0, result.remaining, 0.001);
        assertEquals(30.0, result.usagePercent, 0.001);
    }

    /**
     * 已花超支：剩余为负
     */
    @Test
    public void testTodayRemaining_overBudget() {
        BudgetResult result = BudgetCalculator.calculateTodayRemaining(100.0, 150.0);
        assertEquals(-50.0, result.remaining, 0.001);
        assertEquals(150.0, result.usagePercent, 0.001);
    }

    /**
     * 一分钱没花：使用率 0%
     */
    @Test
    public void testTodayRemaining_zeroSpent() {
        BudgetResult result = BudgetCalculator.calculateTodayRemaining(100.0, 0.0);
        assertEquals(100.0, result.remaining, 0.001);
        assertEquals(0.0, result.usagePercent, 0.001);
    }

    /**
     * 建议预算为 0：使用率直接为 0（避免除零）
     */
    @Test
    public void testTodayRemaining_zeroBudget() {
        BudgetResult result = BudgetCalculator.calculateTodayRemaining(0.0, 50.0);
        assertEquals(-50.0, result.remaining, 0.001);
        assertEquals(0.0, result.usagePercent, 0.001);
    }

    // ========== calculateFromHistory 完整链路测试 ==========

    /**
     * 完整链路示例：
     * - 7 天窗口，3 条支出：100/200/400（共 700，假设都没异常）
     * - 日均 = 700 / 7 = 100
     * - 建议预算 = 100 × 1.0 = 100
     * - 今日已花 30 → 剩余 70
     */
    @Test
    public void testFromHistory_normalCase() {
        List<ExpenseRecord> expenses = Arrays.asList(
                new ExpenseRecord(100, 0L, false),
                new ExpenseRecord(200, 0L, false),
                new ExpenseRecord(400, 0L, false)
        );
        BudgetResult result = BudgetCalculator.calculateFromHistory(
                expenses, 7, 1.0, 30.0, true);
        assertEquals(100.0, result.suggestedBudget, 0.001);
        assertEquals(30.0, result.todaySpent, 0.001);
        assertEquals(70.0, result.remaining, 0.001);
    }

    /**
     * 完整链路：节省模式（倍率 0.8）
     * - 日均 100 → 建议 80 → 剩余 80 - 30 = 50
     */
    @Test
    public void testFromHistory_saveMode() {
        List<ExpenseRecord> expenses = Arrays.asList(
                new ExpenseRecord(700, 0L, false)
        );
        BudgetResult result = BudgetCalculator.calculateFromHistory(
                expenses, 7, 0.8, 30.0, true);
        assertEquals(80.0, result.suggestedBudget, 0.001);
        assertEquals(50.0, result.remaining, 0.001);
    }

    /**
     * 完整链路：主人最关心的真实场景
     * - 日均 ¥50（教育基金，过去一周）
     * - 倍率 1.0
     * - 今日已花 ¥30
     * - 剩余 ¥20
     */
    @Test
    public void testFromHistory_realScenario() {
        List<ExpenseRecord> expenses = new ArrayList<>();
        // 7 天总支出 350 → 日均 50
        for (int i = 0; i < 7; i++) {
            expenses.add(new ExpenseRecord(50.0, 0L, false));
        }
        BudgetResult result = BudgetCalculator.calculateFromHistory(
                expenses, 7, 1.0, 30.0, true);
        assertEquals(50.0, result.suggestedBudget, 0.001);
        assertEquals(30.0, result.todaySpent, 0.001);
        assertEquals(20.0, result.remaining, 0.001);
        assertEquals(60.0, result.usagePercent, 0.001);

        // 验证主人原话场景
        assertTrue("剩余应该让主人心里有数", result.remaining >= 0);
    }

    /**
     * 完整链路：空数据
     * - 日均 0 → 建议 0 → 剩余 -已花
     */
    @Test
    public void testFromHistory_emptyData() {
        BudgetResult result = BudgetCalculator.calculateFromHistory(
                Collections.emptyList(), 7, 1.0, 30.0, true);
        assertEquals(0.0, result.suggestedBudget, 0.001);
        assertEquals(-30.0, result.remaining, 0.001);
    }

    // ========== 常量测试 ==========

    @Test
    public void testConstants() {
        assertEquals(1.0, BudgetCalculator.DEFAULT_RATE, 0.001);
        assertEquals(7, BudgetCalculator.DEFAULT_WINDOW_SIZE);
    }
}