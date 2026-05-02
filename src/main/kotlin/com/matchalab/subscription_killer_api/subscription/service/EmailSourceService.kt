package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.emailtemplate.service.EmailTemplateMatchService
import com.matchalab.subscription_killer_api.repository.EmailSourceRepository
import com.matchalab.subscription_killer_api.subscription.EmailSource
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.subscription.SubscriptionEventRule
import com.matchalab.subscription_killer_api.subscription.SubscriptionEventType
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.EntityNotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

private val logger = KotlinLogging.logger {}

@Service
class EmailSourceService(
    private val emailSourceRepository: EmailSourceRepository,
    private val serviceProviderService: ServiceProviderService,
    private val emailTemplateMatchService: EmailTemplateMatchService,
) {

    fun findById(id: UUID): EmailSource {
        return emailSourceRepository.findByIdOrNull(id) ?: throw EntityNotFoundException(
            "EmailSource not found"
        )
    }
    
    fun findAllById(ids: List<UUID>): List<EmailSource> {
        return emailSourceRepository.findAllById(ids)
    }

    @Transactional
    suspend fun getEmailSource(
        gmailMessage: GmailMessage,
    ): EmailSource? {

        val serviceProviders =
            serviceProviderService.findByActiveEmailAddressesInWithEmailSources(listOf(gmailMessage.senderEmail))

        val sources = serviceProviders
            .flatMap { it.emailSources }
            .filter { it.isActive && it.targetAddress == gmailMessage.senderEmail }

        val source = sources.find { emailSource ->
            val discriminator = emailSource.subjectDiscriminator
            discriminator == null ||
                    gmailMessage.subject.contains(discriminator, true) ||
                    gmailMessage.snippet.contains(discriminator, true)
        } ?: return null

        return emailSourceRepository.findByIdWithServiceProvider(source.id!!)
    }

    @Transactional
    fun addSubscriptionEventRules(
        emailSourceId: UUID,
        newRules: List<SubscriptionEventRuleGenerationDto>,
    ): MutableList<SubscriptionEventRule> {
        val emailSource = findById(emailSourceId)
        val addedRules = emailSource.addSubscriptionEventRules(newRules)

//        logger.debug {
//            "[addSubscriptionEventRules] ✅ Added newRules: $addedRules"
//        }

        return addedRules
    }

    fun matchMessageToEvent(
        emailSource: EmailSource,
        message: GmailMessage
    ): SubscriptionEventType? {
        return emailSource.eventRules
            .filter { it.isActive }
            .find { emailTemplateMatchService.matchMessage(it.template, message) }?.eventType
    }

}
