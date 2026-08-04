package com.stupidbeauty.hxaccounting.data.entity;

/**
 * 日均窗口大小枚举
 * 计算"历史日均支出"时使用的时间窗口
 */
public enum WindowSize {
    ONE_MONTH("近1个月", 30),
    THREE_MONTHS("近3个月", 90),
    SIX_MONTHS("近6个月", 180),
    ONE_YEAR("近1年", 365),
    ALL("全部时间", 1825);

    private final String displayName;
    private final int days;

    WindowSize(String displayName, int days) {
        this.displayName = displayName;
        this.days = days;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDays() {
        return days;
    }
}
