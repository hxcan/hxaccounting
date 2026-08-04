package com.stupidbeauty.hxaccounting.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

/**
 * 预算配置实体类
 * 对应数据库 budgets 表
 * 核心算法载体：存储动态倍率+日均窗口的配置
 *
 * 核心算法：次日预算 = 历史日均支出 × 倍率
 */
@Entity(
    tableName = "budgets",
    foreignKeys = {
        @ForeignKey(
            entity = Account.class,
            parentColumns = "id",
            childColumns = "account_id",
            onDelete = ForeignKey.CASCADE
        )
    }
)
public class Budget {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    @ColumnInfo(name = "account_id")
    private long accountId;

    @NonNull
    @ColumnInfo(name = "window_size", defaultValue = "1M")
    private String windowSize;

    @ColumnInfo(name = "rate", defaultValue = "1.0")
    private double rate;

    @ColumnInfo(name = "anomaly_excluded", defaultValue = "1")
    private boolean anomalyExcluded;

    @ColumnInfo(name = "enabled", defaultValue = "1")
    private boolean enabled;

    @ColumnInfo(name = "updated_at")
    private long updatedAt;

    public Budget() {
        this.id = 0L;
        this.accountId = 0L;
        this.windowSize = WindowSize.ONE_MONTH.name();
        this.rate = 1.0;
        this.anomalyExcluded = true;
        this.enabled = true;
        this.updatedAt = System.currentTimeMillis();
    }

    public Budget(long accountId) {
        this();
        this.accountId = accountId;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getAccountId() { return accountId; }
    public void setAccountId(long accountId) { this.accountId = accountId; }

    @NonNull
    public String getWindowSize() { return windowSize; }
    public void setWindowSize(@NonNull String windowSize) { this.windowSize = windowSize; }

    public WindowSize getWindowSizeEnum() {
        try {
            return WindowSize.valueOf(windowSize);
        } catch (IllegalArgumentException e) {
            return WindowSize.ONE_MONTH;
        }
    }

    public void setWindowSizeEnum(@NonNull WindowSize size) {
        this.windowSize = size.name();
    }

    public double getRate() { return rate; }
    public void setRate(double rate) { this.rate = rate; }

    public boolean isAnomalyExcluded() { return anomalyExcluded; }
    public void setAnomalyExcluded(boolean anomalyExcluded) { this.anomalyExcluded = anomalyExcluded; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
