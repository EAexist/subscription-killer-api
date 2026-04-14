package com.matchalab.subscription_killer_api.subscription

import com.matchalab.subscription_killer_api.subscription.service.SubscriptionEventRuleGenerationDto
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
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

    @Column(nullable = false)
    val targetAddress: String,

    @Column(nullable = true)
    val subjectDiscriminator: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    val eventRules: MutableList<SubscriptionEventRule> = mutableListOf(),

    var isActive: Boolean = true,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_provider_id", nullable = false)
    var serviceProvider: ServiceProvider,

    ) {

    fun hasPaymentStartRule() = eventRules.any { it.eventType == SubscriptionEventType.SUBSCRIPTION_START_OR_PAYMENT && it.isActive && !it.isMonthlyRecurring }
    fun hasPaymentCancelRule() = eventRules.any { it.eventType == SubscriptionEventType.SUBSCRIPTION_CANCEL && it.isActive }
    fun hasMonthlyPaymentRule() = eventRules.any { it.eventType == SubscriptionEventType.SUBSCRIPTION_START_OR_PAYMENT && it.isActive && it.isMonthlyRecurring }
//    fun hasAnnualPaymentRule() = eventRules.any { it.eventType == SubscriptionEventType.SUBSCRIPTION_START_OR_PAYMENT && it.isActive }

    fun addSubscriptionEventRules(
        newRules: List<SubscriptionEventRuleGenerationDto>,
    ): MutableList<SubscriptionEventRule> {
        val updatedAt = Instant.now()
        newRules.forEach {
            eventRules.add(
                SubscriptionEventRule.createActive(it, updatedAt)
            )
        }
        return eventRules
    }
}
