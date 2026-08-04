package com.stupidbeauty.hxaccounting.data.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 账本实体类
 * 对应数据库 accounts 表
 * 存储账本的多账本隔离数据
 */
@Entity(tableName = "accounts")
public class Account {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    @NonNull
    @ColumnInfo(name = "name")
    private String name;

    @NonNull
    @ColumnInfo(name = "type")
    private String type;

    @NonNull
    @ColumnInfo(name = "color", defaultValue = "#000000")
    private String color;

    @NonNull
    @ColumnInfo(name = "icon", defaultValue = "wallet")
    private String icon;

    @ColumnInfo(name = "budget", defaultValue = "0")
    private double budget;

    @ColumnInfo(name = "sort_order", defaultValue = "0")
    private int sortOrder;

    @ColumnInfo(name = "is_archived", defaultValue = "0")
    private boolean isArchived;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    @ColumnInfo(name = "updated_at")
    private long updatedAt;

    public Account() {
        this.id = 0L;
        this.name = "";
        this.type = AccountType.CASH.name();
        this.color = "#000000";
        this.icon = "wallet";
        this.budget = 0.0;
        this.sortOrder = 0;
        this.isArchived = false;
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Account(@NonNull String name, @NonNull AccountType type) {
        this();
        this.name = name;
        this.type = type.name();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    @NonNull
    public String getName() { return name; }
    public void setName(@NonNull String name) { this.name = name; }

    @NonNull
    public String getType() { return type; }
    public void setType(@NonNull String type) { this.type = type; }

    public AccountType getAccountType() {
        try {
            return AccountType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return AccountType.OTHER;
        }
    }

    public void setAccountType(@NonNull AccountType accountType) {
        this.type = accountType.name();
    }

    @NonNull
    public String getColor() { return color; }
    public void setColor(@NonNull String color) { this.color = color; }

    @NonNull
    public String getIcon() { return icon; }
    public void setIcon(@NonNull String icon) { this.icon = icon; }

    public double getBudget() { return budget; }
    public void setBudget(double budget) { this.budget = budget; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public boolean isArchived() { return isArchived; }
    public void setArchived(boolean archived) { isArchived = archived; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
