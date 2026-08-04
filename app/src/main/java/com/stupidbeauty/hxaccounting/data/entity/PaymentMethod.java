package com.stupidbeauty.hxaccounting.data.entity;

/**
 * 支付方式枚举
 */
public enum PaymentMethod {
    CASH("现金"),
    WECHAT("微信"),
    ALIPAY("支付宝"),
    CARD("银行卡"),
    OTHER("其他");

    private final String displayName;

    PaymentMethod(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
