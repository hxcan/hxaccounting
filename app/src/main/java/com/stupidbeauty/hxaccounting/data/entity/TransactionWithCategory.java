package com.stupidbeauty.hxaccounting.data.entity;

/**
 * 带分类名的流水（PDF 导出用）
 * 通过 JOIN 查询一次性拿到分类名，PDF 渲染不用再查 CategoryRepository
 *
 * 字段名必须与 TransactionDao 中 SQL SELECT 别名完全一致（Room @Query 限制）
 * LEFT JOIN：未分类（category_id=NULL）也能正常返回，categoryName 为 null
 *
 * 关联任务: #918517000679 (子任务-数据层)
 */
public class TransactionWithCategory {

    public long id;
    public long accountId;
    public double amount;

    /**
     * 类型: "INCOME" 或 "EXPENSE"
     * 与 TransactionType 枚举对应，但此处用 String 避免 Room 字段名映射问题
     */
    public String type;

    public Long categoryId;            // 可能为 null（未分类）
    public String categoryName;        // JOIN 查出来，可能为 null
    public String subcategory;         // 可能为 null
    public String description;         // 可能为 null（备注）
    public String recipient;           // 可能为 null（收款人/支付对象）

    /**
     * 支付方式: "CASH" / "WECHAT" / "ALIPAY" / "CARD" / "OTHER"
     * 与 PaymentMethod 枚举对应
     */
    public String paymentMethod;

    public long transactionTime;       // 交易时间（毫秒）
    public boolean isAnomaly;          // 是否异常支出
    public String source;              // 来源: "MANUAL" / "AI" / "VOICE" / "SCREENSHOT"

    public TransactionWithCategory() {
    }
}
