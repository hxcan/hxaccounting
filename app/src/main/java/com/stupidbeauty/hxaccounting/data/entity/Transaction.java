package com.stupidbeauty.hxaccounting.data.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 流水实体类
 * 对应数据库 transactions 表
 * 存储每一笔收入支出记录
 */
@Entity(
    tableName = "transactions",
    foreignKeys = {
        @ForeignKey(
            entity = Account.class,
            parentColumns = "id",
            childColumns = "account_id",
            onDelete = ForeignKey.CASCADE
        )
    },
    indices = {
        @Index(value = {"account_id", "transaction_time"}),
        @Index(value = {"transaction_time"})
    }
)
public class Transaction {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    @ColumnInfo(name = "account_id")
    private long accountId;

    @ColumnInfo(name = "amount")
    private double amount;

    @NonNull
    @ColumnInfo(name = "type")
    private String type;

    @Nullable
    @ColumnInfo(name = "category_id")
    private Long categoryId;

    @Nullable
    @ColumnInfo(name = "subcategory")
    private String subcategory;

    @Nullable
    @ColumnInfo(name = "description")
    private String description;

    @Nullable
    @ColumnInfo(name = "recipient")
    private String recipient;

    @NonNull
    @ColumnInfo(name = "payment_method", defaultValue = "OTHER")
    private String paymentMethod;

    @ColumnInfo(name = "transaction_time")
    private long transactionTime;

    @ColumnInfo(name = "is_anomaly", defaultValue = "0")
    private boolean isAnomaly;

    @NonNull
    @ColumnInfo(name = "source", defaultValue = "MANUAL")
    private String source;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    @ColumnInfo(name = "updated_at")
    private long updatedAt;

    public Transaction() {
        this.id = 0L;
        this.accountId = 0L;
        this.amount = 0.0;
        this.type = TransactionType.EXPENSE.name();
        this.categoryId = null;
        this.subcategory = null;
        this.description = null;
        this.recipient = null;
        this.paymentMethod = PaymentMethod.OTHER.name();
        long now = System.currentTimeMillis();
        this.transactionTime = now;
        this.isAnomaly = false;
        this.source = TransactionSource.MANUAL.name();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Transaction(long accountId, double amount, TransactionType type) {
        this();
        this.accountId = accountId;
        this.amount = amount;
        this.type = type.name();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getAccountId() { return accountId; }
    public void setAccountId(long accountId) { this.accountId = accountId; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    @NonNull
    public String getType() { return type; }
    public void setType(@NonNull String type) { this.type = type; }

    public TransactionType getTransactionType() {
        try {
            return TransactionType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return TransactionType.EXPENSE;
        }
    }

    public void setTransactionType(@NonNull TransactionType transactionType) {
        this.type = transactionType.name();
    }

    @Nullable
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(@Nullable Long categoryId) { this.categoryId = categoryId; }

    @Nullable
    public String getSubcategory() { return subcategory; }
    public void setSubcategory(@Nullable String subcategory) { this.subcategory = subcategory; }

    @Nullable
    public String getDescription() { return description; }
    public void setDescription(@Nullable String description) { this.description = description; }

    @Nullable
    public String getRecipient() { return recipient; }
    public void setRecipient(@Nullable String recipient) { this.recipient = recipient; }

    @NonNull
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(@NonNull String paymentMethod) { this.paymentMethod = paymentMethod; }

    public PaymentMethod getPaymentMethodEnum() {
        try {
            return PaymentMethod.valueOf(paymentMethod);
        } catch (IllegalArgumentException e) {
            return PaymentMethod.OTHER;
        }
    }

    public void setPaymentMethodEnum(@NonNull PaymentMethod method) {
        this.paymentMethod = method.name();
    }

    public long getTransactionTime() { return transactionTime; }
    public void setTransactionTime(long transactionTime) { this.transactionTime = transactionTime; }

    public boolean isAnomaly() { return isAnomaly; }
    public void setAnomaly(boolean anomaly) { isAnomaly = anomaly; }

    @NonNull
    public String getSource() { return source; }
    public void setSource(@NonNull String source) { this.source = source; }

    public TransactionSource getSourceEnum() {
        try {
            return TransactionSource.valueOf(source);
        } catch (IllegalArgumentException e) {
            return TransactionSource.MANUAL;
        }
    }

    public void setSourceEnum(@NonNull TransactionSource sourceEnum) {
        this.source = sourceEnum.name();
    }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
