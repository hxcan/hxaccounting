package com.stupidbeauty.hxaccounting.budget;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * BudgetCalculator 单元测试（自适应窗口算法 v2.1）
 *
 * <p>覆盖任务要求的 6 个分支：
 * <ul>
 *   <li>testNoData()：没有流水</li>
 *   <li>testCollectingData()：仅今天记了 1 笔（actualDays == 0）</li>
 *   <li>testColdStart()：0 < actualDays < periodDays（冷启动期）</li>
 *   <li>testStable()：actualDays >= periodDays（稳定期）</li>
 *   <li>testTodayFullyExcluded()：今天的支出既不计入分子也不计入分母</li>
 *   <li>testMidGapIncluded()：中间无流水日计入分母</li>
 * </ul>
 *
 * <p>主人 2026-08-08 验收反馈（v2.1 修复）：
 * <ul>
 *   <li>"今天"既不计入分子也不计入分母</li>
 *   <li>"今天正是此刻正在发生的事情，就是我们能够通过控制来缩减开支或者保持开支
 *       或者说扩大开支的手段"</li>
 * </ul>
 *
 * <p>v2.1 关键修复：
 * <ul>
 *   <li>分子：仅历史支出（严格剔除今天的流水）</li>
 *   <li>分母：第一笔记账日 → 昨天（不含今天）</li>
 *   <li>今日已花（todaySpent）只参与 remaining = suggested - todaySpent</li>
 * </ul>
 *
 * @author 未来姐姐
 * @since 2026-08-08
 */
public class BudgetCalculatorTest {

    /**
     * 测试用时区：与 BudgetCalculator 内部的 SYSTEM_ZONE 保持一致，
     * 避免"测试用 Shanghai，生产用 UTC"导致的 day offset 错位。
     */
    private static final ZoneId TEST_ZONE = ZoneId.systemDefault();

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
    // 2. testCollectingData：仅今天记了 1 笔 → COLLECTING_DATA
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
        // 5 天前开始记，3 天，每天 10 元 → 历史 total = 30（不含今天）
        // actualDays = 5（不含今天），periodDays = 30 → 冷启动期
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
        // 历史 total = 350（不含今天），actualDays = 35，periodDays = 30 → 稳定期
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
    // 5. testTodayFullyExcluded（v2.1 主人验收反馈）：
    //    今天既不计入分子也不计入分母
    // ============================================================

    @Test
    public void testTodayFullyExcluded() {
        LocalDate today = LocalDate.of(2026, 8, 8);
        // 历史流水：3 天前 10 元 + 2 天前 10 元 + 1 天前 10 元（不含今天）
        // 历史 total = 30
        // actualDays = 3（不含今天，因为今天没被计入）
        // periodDays = 30 → 冷启动期
        // 期望日均 = 30 / 3 = 10
        // 今天另花了 100 元，但不影响日均，只影响 remaining
        // remaining = 10 - 100 = -90
        List<ExpenseRecord> expenses = new ArrayList<>();
        expenses.add(makeRecord(today.minusDays(3), 10.0));
        expenses.add(makeRecord(today.minusDays(2), 10.0));
        expenses.add(makeRecord(today.minusDays(1), 10.0));
        expenses.add(makeRecord(today, 100.0)); // 今天的支出

        BudgetResult result = BudgetCalculator.calculateFromHistory(
                expenses, 30, 1.0, 100.0, today, false);

        assertNotNull(result);
        assertEquals(BudgetResult.Status.OK, result.status);
        // v2.1 关键断言：分子不含今天
        // total = 10 + 10 + 10 = 30（今天的 100 元被剔除）
        assertEquals(10.0, result.suggestedBudget, 0.001);
        // 分母不含今天
        assertEquals(3, result.actualDays);
        // remaining = suggested - todaySpent = 10 - 100 = -90
        assertEquals(10.0 - 100.0, result.remaining, 0.001);
    }

    // ============================================================
    // 6. testMidGapIncluded：中间无流水日计入分母
    // ============================================================

    @Test
    public void testMidGapIncluded() {
        LocalDate today = LocalDate.of(2026, 8, 8);
        // 5 天前记了 1 笔（20 元），3 天前记了 1 笔（30 元）（不含今天）
        // 主人原话：中间无流水日计入分母
        // actualDays = 5（第一笔记账日 = today-5，不含今天）
        List<ExpenseRecord> expenses = new ArrayList<>();
        expenses.add(makeRecord(today.minusDays(5), 20.0));
        expenses.add(makeRecord(today.minusDays(3), 30.0));

        BudgetResult result = BudgetCalculator.calculateFromHistory(
                expenses, 30, 1.0, 0.0, today, false);

        assertNotNull(result);
        assertEquals(BudgetResult.Status.OK, result.status);
        // 分母按"第一笔记账日 → 今天（不含）"算，不是按流水数
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

    // ============================================================
    // 11. v2.1 新增：实主人验收场景的回归测试
    //     昨天记 ¥371.24，今天又花了 ¥300.37
    //     → 日均 = 371.24 / 1 = 371.24（不含今天）
    //     → remaining = 371.24 - 300.37 = 70.87
    // ============================================================

    @Test
    public void testMasterAcceptanceScenario() {
        LocalDate today = LocalDate.of(2026, 8, 8);
        // 主人截图数据：昨天支出 371.24，今天支出 300.37
        List<ExpenseRecord> expenses = new ArrayList<>();
        expenses.add(makeRecord(today.minusDays(1), 371.24)); // 昨天
        expenses.add(makeRecord(today, 300.37));              // 今天（应被剔除）

        BudgetResult result = BudgetCalculator.calculateFromHistory(
                expenses, 30, 1.0, 300.37, today, false);

        assertNotNull(result);
        assertEquals(BudgetResult.Status.OK, result.status);
        // 分母 = 1（只有昨天一天历史）
        assertEquals(1, result.actualDays);
        // 日均 = 371.24 / 1 = 371.24（今天的 300.37 被剔除）
        assertEquals(371.24, result.suggestedBudget, 0.01);
        // remaining = 371.24 - 300.37 = 70.87
        assertEquals(70.87, result.remaining, 0.01);
    }
}
