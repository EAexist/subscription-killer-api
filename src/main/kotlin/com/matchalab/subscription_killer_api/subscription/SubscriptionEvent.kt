package com.matchalab.subscription_killer_api.subscription

import java.time.Instant

class SubscriptionEvent(
    var internalDate: Instant? = null,
    var type: SubscriptionEventType,
    var isMonthlyRecurring: Boolean = false,
) {
}
//@Entity
//class SubscriptionEvent(
//
//    @Id @GeneratedValue(strategy = GenerationType.AUTO)
//    var id: UUID? = null,
//    var internalDate: Instant? = null,
//    var type: SubscriptionEventType,
//) {
//}
