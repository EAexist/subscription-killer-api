package com.matchalab.subscription_killer_api.guest

import com.fasterxml.jackson.databind.ObjectMapper
import com.matchalab.subscription_killer_api.gmail.MessageFetchPlan
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.subscription.service.gmailclientadapter.GmailClientAdapter
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.io.support.ResourcePatternResolver
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class GuestAppUserGmailClientAdapter(
    private val objectMapper: ObjectMapper,
    private val resourcePatternResolver: ResourcePatternResolver
) : GmailClientAdapter {

    companion object {
        private const val GUEST_APP_USER_EMAILS_DATA_PATH =
            "classpath:static/guest-app-user-emails.jsonl"
    }

    private val idToSampleMessages: Map<String, GmailMessage> =
        loadEmails().sortedBy { it.internalDate }.associateBy { it.id }

    override suspend fun listMessageIds(query: String): List<String> {
        return idToSampleMessages.values.map { it.id }
    }

    override suspend fun getMessages(
        messageIds: List<String>,
        plan: MessageFetchPlan
    ): List<GmailMessage> {
        return messageIds.mapNotNull { idToSampleMessages[it] }
    }

    override suspend fun getFirstMessageId(addresses: List<String>, q: String): String? {
        return idToSampleMessages.values.first { it.senderEmail in addresses }.id
    }

    override suspend fun getFirstMessageId(addresses: List<String>): String? {
        return idToSampleMessages.values.first { it.senderEmail in addresses }.id
    }

    private fun loadEmails(): List<GmailMessage> {
        val emails = loadResource<GmailMessage>(GUEST_APP_USER_EMAILS_DATA_PATH)

        logger.debug { "Loaded ${emails.size} emails" }
        return emails
    }

    private inline fun <reified T> loadResource(path: String): List<T> {
        return try {

            val resources = resourcePatternResolver.getResources(path)

            val datasetFile = resources.firstOrNull()
                ?: throw IllegalStateException("Guest appUser emails not found")

            logger.info { "Loading dataset from: ${datasetFile.filename}" }

            datasetFile.inputStream.bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() }
                    .map { objectMapper.readValue(it, T::class.java) }.toList()
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to load guest appUser emails" }
            emptyList<T>()
        }
    }
}
