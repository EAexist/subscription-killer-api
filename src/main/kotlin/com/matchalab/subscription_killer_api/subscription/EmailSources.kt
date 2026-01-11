package com.matchalab.subscription_killer_api.subscription

import com.matchalab.subscription_killer_api.subscription.service.EmailDetectionRuleGenerationDto
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.NaturalId
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

    @NaturalId
    @Column(nullable = false, unique = true)
    val targetAddress: String,

    @JdbcTypeCode(SqlTypes.JSON)
    val eventRules: MutableList<EmailDetectionRule> = mutableListOf(),

    var isActive: Boolean = true,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_provider_id", nullable = false)
    var serviceProvider: ServiceProvider? = null,
) {

    val paymentStartRule: EmailDetectionRule? get() = eventRules.find { (it.eventType == SubscriptionEventType.PAID_SUBSCRIPTION_START) && it.isActive }
    val paymentCancelRule: EmailDetectionRule? get() = eventRules.find { (it.eventType == SubscriptionEventType.PAID_SUBSCRIPTION_CANCEL) && it.isActive }
    val monthlyPaymentRule: EmailDetectionRule? get() = eventRules.find { (it.eventType == SubscriptionEventType.MONTHLY_PAYMENT) && it.isActive }
    val annualPaymentRule: EmailDetectionRule? get() = eventRules.find { (it.eventType == SubscriptionEventType.ANNUAL_PAYMENT) && it.isActive }

    fun addEmailDetectionRules(
        newRules: Map<SubscriptionEventType, EmailDetectionRuleGenerationDto>,
    ) {
        val updatedAt = Instant.now()
        newRules.entries.forEach { (eventType, newRule) ->
            val existingRule = eventRules.find { (it.eventType == eventType) && it.isActive }
            existingRule?.let {
                it.isActive = false
            }
            eventRules.add(
                EmailDetectionRule.createActive(newRule, updatedAt)
            )
        }
    }

}
