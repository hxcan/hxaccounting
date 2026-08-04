package com.stupidbeauty.hxaccounting.data.entity;

/**
 * 流水类型枚举
 * 收入 / 支出
 */
public enum TransactionType {
    INCOME("收入"),
    EXPENSE("支出");

    private final String displayName;

    TransactionType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
