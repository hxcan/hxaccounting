package com.stupidbeauty.hxaccounting.data.entity;

/**
 * 分类类型枚举
 * 限定分类适用的流水类型
 */
public enum CategoryType {
    EXPENSE("支出"),
    INCOME("收入"),
    ALL("通用");

    private final String displayName;

    CategoryType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
