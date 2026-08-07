package com.stupidbeauty.hxaccounting.budget;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * BudgetCalculator 单元测试（自适应窗口算法 v2）
 *
 * <p>覆盖任务要求的 6 个分支：
 * <ul>
 *   <li>testNoData()：没有流水</li>
 *   <li>testCollectingData()：actualDays == 0（仅今天记了 1 笔）</li>
 *   <li>testColdStart()：0 < actualDays < periodDays（冷启动期）</li>
 *   <li>testStable()：actualDays >= periodDays（稳定期）</li>
 *   <li>testTodayNotIncluded()：今天的不计入分母</li>
 *   <li>testMidGapIncluded()：中间无流水日计入分母</li>
 * </ul>
 *
 * <p>主人 2026-08-08 拍板依据：
 * <ul>
 *   <li>"今天"不计入分母</li>
 *   <li>中间无流水日计入分母</li>
 *   <li>周期默认 30 天</li>
 *   <li>仅 1 天数据：数据积累中</li>
 * </ul>
 *
 * @author 未来姐姐
 * @since 2026-08-08
 */
public class BudgetCalculatorTest {

    /**
     * 固定时区（避免测试机器时区不同导致结果不一致）
     */
    private static final ZoneId TEST_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 把 LocalDate 转成 epoch millis（按 TEST_ZONE 时区）
     */
    private long toEpochMilli(LocalDate date) {
        return date.atStartOfDay(TEST_ZONE).toInstant().toEpochMilli();
    }

    /**
     * 创建一个 ExpenseRecord（按 TEST_ZONE 时区的当天 0 点）
     */
    private ExpenseRecord makeRecord(LocalDate date, double amount) {
        return makeRecord(date, amount, false);
    }

    /**
     * 创建一个 ExpenseRecord（可指定异常标记）
     */
    private ExpenseRecord makeRecord(LocalDate date, double amount, boolean isAnomaly) {
        return new ExpenseRecord(amount, toEpochMilli(date), isAnomaly);
    }

    // ============================================================
    // 1. testNoData：没有流水 → NO_DATA
    // ============================================================

    @Test
    public void testNoData() {
        LocalDate today = LocalDate.of(2026, 8, 8);
        List<ExpenseRecord> empty = new ArrayList<>();

        BudgetResult result = BudgetCalculator.calculateFromHistory(
                empty, 30, 1.0, 0.0, today, false);

        assertNotNull(result);
        assertEquals(BudgetResult.Status.NO_DATA, result.status);
        assertEquals(0.0, result.suggestedBudget, 0.001);
        assertEquals(0, result.actualDays);
    }

    // ============================================================
    // 2. testCollectingData：actualDays == 0 → COLLECTING_DATA
    // ============================================================

    @Test
    public void testCollectingData() {
        LocalDate today = LocalDate.of(2026, 8, 8);
        List<ExpenseRecord> expenses = new ArrayList<>();
        expenses.add(makeRecord(today, 50.0)); // 仅今天记了 1 笔

        BudgetResult result = BudgetCalculator.calculateFromHistory(
                expenses, 30, 1.0, 50.0, today, false);

        assertNotNull(result);
        assertEquals(BudgetResult.Status.COLLECTING_DATA, result.status);
        assertEquals(0, result.actualDays);
        // 积累中状态：suggestedBudget 应为 0
        assertEquals(0.0, result.suggestedBudget, 0.001);
    }

    // ============================================================
    // 3. testColdStart：0 < actualDays < periodDays → 冷启动期
    // ============================================================

    @Test
    public void testColdStart() {
        LocalDate today = LocalDate.of(2026, 8, 8);
        // 5 天前开始记，3 天，每天 10 元 → total = 30
        // actualDays = 5，periodDays = 30 → 冷启动期
        // 期望日均 = 30 / 5 = 6.0
        List<ExpenseRecord> expenses = new ArrayList<>();
        expenses.add(makeRecord(today.minusDays(5), 10.0));
        expenses.add(makeRecord(today.minusDays(3), 10.0));
        expenses.add(makeRecord(today.minusDays(1), 10.0));

        BudgetResult result = BudgetCalculator.calculateFromHistory(
                expenses, 30, 1.0, 0.0, today, false);

        assertNotNull(result);
        assertEquals(BudgetResult.Status.OK, result.status);
        assertEquals(5, result.actualDays);
        assertEquals(30, result.periodDays);
        assertTrue(result.isColdStart);
        assertEquals(6.0, result.suggestedBudget, 0.001);
    }

    // ============================================================
    // 4. testStable：actualDays >= periodDays → 稳定期
    // ============================================================

    @Test
    public void testStable() {
        LocalDate today = LocalDate.of(2026, 8, 8);
        // 35 天前开始记，每天 10 元，共 35 笔记账（中间无空缺）
        // total = 350，actualDays = 35，periodDays = 30 → 稳定期
        // 期望日均 = 350 / 30 = 11.666...
        List<ExpenseRecord> expenses = new ArrayList<>();
        for (int i = 1; i <= 35; i++) {
            expenses.add(makeRecord(today.minusDays(i), 10.0));
        }

        BudgetResult result = BudgetCalculator.calculateFromHistory(
                expenses, 30, 1.0, 0.0, today, false);

        assertNotNull(result);
        assertEquals(BudgetResult.Status.OK, result.status);
        assertEquals(35, result.actualDays);
        assertEquals(30, result.periodDays);
        assertTrue(!result.isColdStart);
        assertEquals(350.0 / 30.0, result.suggestedBudget, 0.001);
    }

    // ============================================================
    // 5. testTodayNotIncluded：今天的支出不计入分母
    // ============================================================

    @Test
    public void testTodayNotIncluded() {
        LocalDate today = LocalDate.of(2026, 8, 8);
        // 3 天前开始记，每天 10 元，今天也记了一笔 100 元（异常大）
        // 期望：today 这 100 元仍计入分子（total），但 today 不计入分母
        // actualDays = 3，periodDays = 30 → 冷启动期
        // total = 10*3 + 100 = 130
        // 日均 = 130 / 3 ≈ 43.333...
        List<ExpenseRecord> expenses = new ArrayList<>();
        expenses.add(makeRecord(today.minusDays(3), 10.0));
        expenses.add(makeRecord(today.minusDays(2), 10.0));
        expenses.add(makeRecord(today.minusDays(1), 10.0));
        expenses.add(makeRecord(today, 100.0));

        BudgetResult result = BudgetCalculator.calculateFromHistory(
                expenses, 30, 1.0, 100.0, today, false);

        assertNotNull(result);
        assertEquals(BudgetResult.Status.OK, result.status);
        // 分母是 3（不含今天）
        assertEquals(3, result.actualDays);
        // 日均 = 130 / 3 ≈ 43.333
        assertEquals(130.0 / 3.0, result.suggestedBudget, 0.001);
        // remaining = 43.333 - 100（今天已花）= -56.666
        assertEquals(130.0 / 3.0 - 100.0, result.remaining, 0.001);
    }

    // ============================================================
    // 6. testMidGapIncluded：中间无流水日计入分母
    // ============================================================

    @Test
    public void testMidGapIncluded() {
        LocalDate today = LocalDate.of(2026, 8, 8);
        // 5 天前记了 1 笔，3 天前记了 1 笔（中间 4 天、2 天没流水）
        // 主人原话：中间无流水日计入分母
        // actualDays = 5（第一笔记账日 = today-5）
        List<ExpenseRecord> expenses = new ArrayList<>();
        expenses.add(makeRecord(today.minusDays(5), 20.0));
        expenses.add(makeRecord(today.minusDays(3), 30.0));

        BudgetResult result = BudgetCalculator.calculateFromHistory(
                expenses, 30, 1.0, 0.0, today, false);

        assertNotNull(result);
        assertEquals(BudgetResult.Status.OK, result.status);
        // 分母按"第一笔记账日 → 今天"算，不是按流水数
        assertEquals(5, result.actualDays);
        // total = 50，日均 = 50 / 5 = 10
        assertEquals(10.0, result.suggestedBudget, 0.001);
    }

    // ============================================================
    // 7. 额外测试：异常支出排除（excludeAnomaly=true）
    // ============================================================

    @Test
    public void testExcludeAnomaly() {
        LocalDate today = LocalDate.of(2026, 8, 8);
        List<ExpenseRecord> expenses = new ArrayList<>();
        expenses.add(makeRecord(today.minusDays(5), 10.0, false));
        expenses.add(makeRecord(today.minusDays(3), 10.0, false));
        expenses.add(makeRecord(today.minusDays(1), 500.0, true)); // 异常支出

        BudgetResult result = BudgetCalculator.calculateFromHistory(
                expenses, 30, 1.0, 0.0, today, true);

        assertNotNull(result);
        assertEquals(BudgetResult.Status.OK, result.status);
        // total 应排除异常 = 20，不是 520
        assertEquals(20.0 / 5.0, result.suggestedBudget, 0.001);
    }

    // ============================================================
    // 8. 额外测试：倍率生效（rate = 1.5）
    // ============================================================

    @Test
    public void testRateMultiplier() {
        LocalDate today = LocalDate.of(2026, 8, 8);
        List<ExpenseRecord> expenses = new ArrayList<>();
        expenses.add(makeRecord(today.minusDays(5), 10.0));
        expenses.add(makeRecord(today.minusDays(3), 10.0));
        expenses.add(makeRecord(today.minusDays(1), 10.0));

        BudgetResult result = BudgetCalculator.calculateFromHistory(
                expenses, 30, 1.5, 0.0, today, false);

        assertNotNull(result);
        // 日均 = 30 / 5 = 6，倍率 1.5 → 9
        assertEquals(9.0, result.suggestedBudget, 0.001);
    }

    // ============================================================
    // 9. 额外测试：参数校验（periodDays <= 0 抛异常）
    // ============================================================

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidPeriodDays() {
        LocalDate today = LocalDate.of(2026, 8, 8);
        List<ExpenseRecord> expenses = new ArrayList<>();
        expenses.add(makeRecord(today.minusDays(1), 10.0));

        BudgetCalculator.calculateFromHistory(
                expenses, 0, 1.0, 0.0, today, false);
    }

    // ============================================================
    // 10. 额外测试：参数校验（rate <= 0 抛异常）
    // ============================================================

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidRate() {
        LocalDate today = LocalDate.of(2026, 8, 8);
        List<ExpenseRecord> expenses = new ArrayList<>();
        expenses.add(makeRecord(today.minusDays(1), 10.0));

        BudgetCalculator.calculateFromHistory(
                expenses, 30, 0.0, 0.0, today, false);
    }
}
