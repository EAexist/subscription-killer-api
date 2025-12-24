package com.matchalab.subscription_killer_api.subscription

enum class SubscriptionEventType {
    MONTHLY_PAYMENT,
    ANNUAL_PAYMENT,
    PAID_SUBSCRIPTION_START,
    PAID_SUBSCRIPTION_CANCEL,
    UNKNOWN
}
