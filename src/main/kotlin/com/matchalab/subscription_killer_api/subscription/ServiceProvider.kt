package com.matchalab.subscription_killer_api.subscription

import com.matchalab.subscription_killer_api.subscription.providers.core.PaymentCycle
import jakarta.persistence.*
import java.util.*

@Entity
class ServiceProvider(
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    var id: UUID? = null,

    val displayName: String,

    @ElementCollection
    @CollectionTable(
        name = "serviceProvider_aliasNames",
        joinColumns = [JoinColumn(name = "service_provider_id")],
        uniqueConstraints = [
            UniqueConstraint(columnNames = ["locale_key", "alias_name"])
        ],
        indexes = [
            Index(name = "idx_alias_lookup", columnList = "alias_name")
        ]
    )
    @MapKeyColumn(name = "locale_key")
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "alias_name")
    val aliasNames: MutableMap<String, String> = mutableMapOf(),

    @OneToMany(mappedBy = "serviceProvider", cascade = [CascadeType.ALL], orphanRemoval = true)
    val emailSources: MutableList<EmailSource> = mutableListOf(),

    @Enumerated(EnumType.STRING)
    val paymentCycle: PaymentCycle? = null,

    @OneToMany(mappedBy = "serviceProvider", cascade = [CascadeType.ALL])
    val subscriptions: List<Subscription> = mutableListOf(),
) {
    val activeEmailSources: List<EmailSource> get() = emailSources.filter { it.isActive }
    val emailSearchAddresses: List<String> get() = activeEmailSources.map { it.targetAddress }
    val emailSearchAliasNames: Map<String, String>? get() = if (isEmailDetectionRuleComplete()) aliasNames else null

    fun isPaymentStartRulePresent(): Boolean {
        return emailSources.any { it.paymentStartRule != null }
    }

    fun isPaymentCancelRulePresent(): Boolean {
        return emailSources.any { it.paymentCancelRule != null }
    }

    fun isMonthlyPaymentRulePresent(): Boolean {
        return emailSources.any { it.monthlyPaymentRule != null }
    }

    fun isEmailDetectionRuleAvailable(): Boolean {
        return isPaymentStartRulePresent() || isMonthlyPaymentRulePresent()
    }

    fun isEmailDetectionRuleComplete(): Boolean {
        return (isPaymentStartRulePresent() && isPaymentCancelRulePresent()) || isMonthlyPaymentRulePresent()
    }

    fun addAllEmailSources(newEmailSources: List<EmailSource>) {
        newEmailSources.forEach { it.serviceProvider = this }
        this.emailSources.addAll(newEmailSources)
    }
}