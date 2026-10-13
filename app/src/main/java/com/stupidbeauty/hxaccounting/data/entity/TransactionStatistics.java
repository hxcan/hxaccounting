package com.stupidbeauty.hxaccounting.data.entity;

/**
 * 账本收支统计汇总（PDF 导出用）
 * 不入库，纯数据传输对象
 *
 * 字段说明：
 * - totalIncome: 收入合计（排除异常流水 is_anomaly=1）
 * - totalExpense: 支出合计（排除异常流水）
 * - balance: 余额 = totalIncome - totalExpense
 * - transactionCount: 全部流水笔数（含异常）
 * - anomalyCount: 异常流水笔数
 * - incomeCount: 收入笔数（含异常）
 * - expenseCount: 支出笔数（含异常）
 *
 * 与 BudgetCalculator 保持一致：统计时排除异常流水（is_anomaly=0）
 * 关联任务: #918517000679 (子任务-数据层)
 */
public class TransactionStatistics {

    public double totalIncome;      // 收入合计（排除异常）
    public double totalExpense;     // 支出合计（排除异常）
    public double balance;          // 余额 = totalIncome - totalExpense
    public int transactionCount;    // 全部流水笔数（含异常）
    public int anomalyCount;       // 异常流水笔数
    public int incomeCount;        // 收入笔数
    public int expenseCount;       // 支出笔数

    public TransactionStatistics() {
        this.totalIncome = 0.0;
        this.totalExpense = 0.0;
        this.balance = 0.0;
        this.transactionCount = 0;
        this.anomalyCount = 0;
        this.incomeCount = 0;
        this.expenseCount = 0;
    }

    @Override
    public String toString() {
        return "TransactionStatistics{" +
                "totalIncome=" + totalIncome +
                ", totalExpense=" + totalExpense +
                ", balance=" + balance +
                ", transactionCount=" + transactionCount +
                ", anomalyCount=" + anomalyCount +
                ", incomeCount=" + incomeCount +
                ", expenseCount=" + expenseCount +
                '}';
    }
}
