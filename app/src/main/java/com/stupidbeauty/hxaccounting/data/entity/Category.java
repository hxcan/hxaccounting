package com.stupidbeauty.hxaccounting.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 分类实体类
 * 对应数据库 categories 表
 * 支出/收入的分类管理，支持二级分类
 */
@Entity(
    tableName = "categories",
    foreignKeys = {
        @ForeignKey(
            entity = Category.class,
            parentColumns = "id",
            childColumns = "parent_id",
            onDelete = ForeignKey.SET_NULL
        )
    },
    indices = {
        @Index(value = {"parent_id"}),
        @Index(value = {"type"})
    }
)
public class Category {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    @NonNull
    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "parent_id")
    private Long parentId;

    @NonNull
    @ColumnInfo(name = "icon", defaultValue = "tag")
    private String icon;

    @NonNull
    @ColumnInfo(name = "color", defaultValue = "#666666")
    private String color;

    @NonNull
    @ColumnInfo(name = "type", defaultValue = "EXPENSE")
    private String type;

    @ColumnInfo(name = "sort_order", defaultValue = "0")
    private int sortOrder;

    @ColumnInfo(name = "is_system", defaultValue = "0")
    private boolean isSystem;

    @ColumnInfo(name = "is_archived", defaultValue = "0")
    private boolean isArchived;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    public Category() {
        this.id = 0L;
        this.name = "";
        this.parentId = null;
        this.icon = "tag";
        this.color = "#666666";
        this.type = CategoryType.EXPENSE.name();
        this.sortOrder = 0;
        this.isSystem = false;
        this.isArchived = false;
        this.createdAt = System.currentTimeMillis();
    }

    public Category(@NonNull String name, @NonNull CategoryType type) {
        this();
        this.name = name;
        this.type = type.name();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    @NonNull
    public String getName() { return name; }
    public void setName(@NonNull String name) { this.name = name; }

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }

    @NonNull
    public String getIcon() { return icon; }
    public void setIcon(@NonNull String icon) { this.icon = icon; }

    @NonNull
    public String getColor() { return color; }
    public void setColor(@NonNull String color) { this.color = color; }

    @NonNull
    public String getType() { return type; }
    public void setType(@NonNull String type) { this.type = type; }

    public CategoryType getCategoryType() {
        try {
            return CategoryType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return CategoryType.EXPENSE;
        }
    }

    public void setCategoryType(@NonNull CategoryType categoryType) {
        this.type = categoryType.name();
    }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public boolean isSystem() { return isSystem; }
    public void setSystem(boolean system) { isSystem = system; }

    public boolean isArchived() { return isArchived; }
    public void setArchived(boolean archived) { isArchived = archived; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
