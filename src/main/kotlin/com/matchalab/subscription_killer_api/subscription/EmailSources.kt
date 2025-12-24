package com.matchalab.subscription_killer_api.subscription

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.NaturalId
import org.hibernate.type.SqlTypes
import java.util.*

@Entity
@Table(
    indexes = [
        Index(name = "idx_email_source_lookup", columnList = "targetAddress, is_active")
    ]
)
class EmailSource(

    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    var id: UUID? = null,

    @NaturalId
    @Column(nullable = false, unique = true)
    val targetAddress: String,

    @JdbcTypeCode(SqlTypes.JSON)
    val eventRules: MutableMap<SubscriptionEventType, EmailDetectionRule> = mutableMapOf(),

    var isActive: Boolean = true,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_provider_id", nullable = false)
    var serviceProvider: ServiceProvider? = null,
) {

    val paymentStartRule: EmailDetectionRule? get() = eventRules[SubscriptionEventType.PAID_SUBSCRIPTION_START]
    val paymentCancelRule: EmailDetectionRule? get() = eventRules[SubscriptionEventType.PAID_SUBSCRIPTION_CANCEL]
    val monthlyPaymentRule: EmailDetectionRule? get() = eventRules[SubscriptionEventType.MONTHLY_PAYMENT]
    val annualPaymentRule: EmailDetectionRule? get() = eventRules[SubscriptionEventType.ANNUAL_PAYMENT]

    fun updateEmailDetectionRules(
        newRules: Map<SubscriptionEventType, EmailDetectionRule>,
    ) {
        newRules.forEach { (eventType, rule) ->
            eventRules[eventType] = rule
        }
    }
}
