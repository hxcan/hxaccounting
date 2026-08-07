package com.stupidbeauty.hxaccounting.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

/**
 * 预算设置实体类（自适应窗口算法 v2）
 *
 * <p>对应数据库 budget_settings 表。
 * 每个账本最多一条记录（由 UNIQUE 约束保证）。
 *
 * <p>核心字段：
 * <ul>
 *   <li>accountId：账本 ID（主键，关联 accounts.id）</li>
 *   <li>periodDays：预算周期天数，默认 30 天</li>
 *   <li>createdAt / updatedAt：时间戳</li>
 * </ul>
 *
 * <p>v2 设计目的：把"周期天数"从写死的 7 天改为可配置，每个账本独立。
 *
 * @author 未来姐姐
 * @since 2026-08-08
 */
@Entity(
    tableName = "budget_settings",
    foreignKeys = {
        @ForeignKey(
            entity = Account.class,
            parentColumns = "id",
            childColumns = "account_id",
            onDelete = ForeignKey.CASCADE
        )
    }
)
public class BudgetSettings {

    /** 默认周期天数（v2 算法默认值） */
    public static final int DEFAULT_PERIOD_DAYS = 30;

    @PrimaryKey
    @ColumnInfo(name = "account_id")
    private long accountId;

    @ColumnInfo(name = "period_days", defaultValue = "30")
    private int periodDays;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    @ColumnInfo(name = "updated_at")
    private long updatedAt;

    public BudgetSettings() {
        this.accountId = 0L;
        this.periodDays = DEFAULT_PERIOD_DAYS;
        final long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public BudgetSettings(long accountId) {
        this();
        this.accountId = accountId;
    }

    public long getAccountId() { return accountId; }
    public void setAccountId(long accountId) { this.accountId = accountId; }

    public int getPeriodDays() { return periodDays; }
    public void setPeriodDays(int periodDays) { this.periodDays = periodDays; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
