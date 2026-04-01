package com.matchalab.subscription_killer_api.subscription

enum class SubscriptionEventType {
    MONTHLY_PAYMENT,
    ANNUAL_PAYMENT,
    SUBSCRIPTION_START,
    SUBSCRIPTION_CANCEL,
    NOT_A_SUBSCRIPTION_EMAIL
}
