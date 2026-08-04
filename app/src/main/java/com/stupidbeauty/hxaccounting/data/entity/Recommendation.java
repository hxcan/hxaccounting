package com.stupidbeauty.hxaccounting.data.entity;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 推荐记录实体类
 * 对应数据库 recommendations 表
 * 记录平替推荐（未来CPS佣金）—— 商业化预留
 */
@Entity(
    tableName = "recommendations",
    foreignKeys = {
        @ForeignKey(
            entity = Transaction.class,
            parentColumns = "id",
            childColumns = "transaction_id",
            onDelete = ForeignKey.SET_NULL
        )
    },
    indices = {
        @Index(value = {"category"}),
        @Index(value = {"clicked_at"})
    }
)
public class Recommendation {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    @ColumnInfo(name = "transaction_id")
    private Long transactionId;

    @androidx.annotation.NonNull
    @ColumnInfo(name = "category")
    private String category;

    @Nullable
    @ColumnInfo(name = "product_id")
    private String productId;

    @Nullable
    @ColumnInfo(name = "product_name")
    private String productName;

    @Nullable
    @ColumnInfo(name = "product_url")
    private String productUrl;

    @ColumnInfo(name = "original_price")
    private Double originalPrice;

    @ColumnInfo(name = "current_price")
    private Double currentPrice;

    @ColumnInfo(name = "commission_rate")
    private Double commissionRate;

    @ColumnInfo(name = "estimated_commission")
    private Double estimatedCommission;

    @Nullable
    @ColumnInfo(name = "platform")
    private String platform;

    @ColumnInfo(name = "clicked_at")
    private Long clickedAt;

    @ColumnInfo(name = "purchased_at")
    private Long purchasedAt;

    @ColumnInfo(name = "actual_commission")
    private Double actualCommission;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    public Recommendation() {
        this.id = 0L;
        this.transactionId = null;
        this.category = "";
        this.productId = null;
        this.productName = null;
        this.productUrl = null;
        this.originalPrice = null;
        this.currentPrice = null;
        this.commissionRate = null;
        this.estimatedCommission = null;
        this.platform = null;
        this.clickedAt = null;
        this.purchasedAt = null;
        this.actualCommission = null;
        this.createdAt = System.currentTimeMillis();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getProductUrl() { return productUrl; }
    public void setProductUrl(String productUrl) { this.productUrl = productUrl; }

    public Double getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(Double originalPrice) { this.originalPrice = originalPrice; }

    public Double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(Double currentPrice) { this.currentPrice = currentPrice; }

    public Double getCommissionRate() { return commissionRate; }
    public void setCommissionRate(Double commissionRate) { this.commissionRate = commissionRate; }

    public Double getEstimatedCommission() { return estimatedCommission; }
    public void setEstimatedCommission(Double estimatedCommission) { this.estimatedCommission = estimatedCommission; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public Long getClickedAt() { return clickedAt; }
    public void setClickedAt(Long clickedAt) { this.clickedAt = clickedAt; }

    public Long getPurchasedAt() { return purchasedAt; }
    public void setPurchasedAt(Long purchasedAt) { this.purchasedAt = purchasedAt; }

    public Double getActualCommission() { return actualCommission; }
    public void setActualCommission(Double actualCommission) { this.actualCommission = actualCommission; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
