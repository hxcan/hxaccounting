package com.stupidbeauty.hxaccounting.data.entity;

/**
 * 账本类型枚举
 * 对应数据库 accounts.type 字段
 */
public enum AccountType {
    CASH("现金"),
    SAVINGS("储蓄"),
    CREDIT("信用卡"),
    OTHER("其他");

    private final String displayName;

    AccountType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
