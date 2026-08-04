package com.stupidbeauty.hxaccounting.data.entity;

/**
 * 流水来源枚举
 * 标识这条数据是怎么进入系统的
 * 未来用于 OCR / 数据迁移的扩展
 */
public enum TransactionSource {
    MANUAL("手动录入"),
    OCR("OCR识别"),
    IMPORT("数据导入");

    private final String displayName;

    TransactionSource(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
