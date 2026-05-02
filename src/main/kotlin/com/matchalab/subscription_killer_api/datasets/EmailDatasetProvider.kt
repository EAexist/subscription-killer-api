package com.matchalab.subscription_killer_api.datasets

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.matchalab.subscription_killer_api.ai.toPromptParamString
import com.matchalab.subscription_killer_api.emailtemplate.EmailTemplate
import com.matchalab.subscription_killer_api.emailtemplate.extractAnchors
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import com.matchalab.subscription_killer_api.subscription.SubscriptionEventType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.annotations.VisibleForTesting
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Profile
import org.springframework.core.io.support.ResourcePatternResolver
import org.springframework.stereotype.Component
import java.time.Instant
import javax.annotation.PostConstruct

private val logger = KotlinLogging.logger {}

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class Sample(
    val id: String,
    val companyId: String,
    val templateId: String,
    val subject: String,
    val snippet: String,
    val subscriptionEventType: SubscriptionEventType,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class Template(
    val id: String,
    val subject: String,
    val snippet: String,
    val subscriptionEventType: SubscriptionEventType,
)


@JsonIgnoreProperties(ignoreUnknown = true)
internal data class CompanyEmailSource(
    val email: String,
    val name: String,
)


/**
 * Component responsible for loading and providing email sample dataset
 */
@Component
@Lazy(false)
@Profile("!ai || !gmail")
class EmailDatasetProvider(
    private val objectMapper: ObjectMapper,
    private val resourcePatternResolver: ResourcePatternResolver
) {
    companion object {
        private const val COMPANIES_DATA_PATH = "classpath:data/reference/companies.json"
        private const val TEMPLATES_DATA_PATH = "classpath:data/templates/templates.jsonl"
        private const val EMAILS_DATA_PATH = "classpath:data/emails/samples.jsonl"
    }

    private var currentOffset = 0
    private val batchSize = 50
    private lateinit var companyEmailMap: Map<String, CompanyEmailSource>
    private lateinit var idToEmailSamples: Map<String, EmailSample>
    private lateinit var idToEmailTemplates: Map<String, Template>
    private lateinit var sampleMessageSet: List<GmailMessage>

    /**
     * Returns the loaded email samples
     */
    fun getEmailSamples(): List<EmailSample> = idToEmailSamples.values.toList()

    /**
     * Returns the loaded email samples
     */
    fun getSampleMessageSet(): List<GmailMessage> {
        val fromIndex = currentOffset % sampleMessageSet.size
        val toIndex = (fromIndex + batchSize).coerceAtMost(sampleMessageSet.size)

        val slice = sampleMessageSet.slice(fromIndex until toIndex)

        // Update offset for the next call
        currentOffset = toIndex
        if (currentOffset >= sampleMessageSet.size) currentOffset = 0

        return slice.map { it }
    }

    /**
     * Returns the loaded email samples
     */
    fun getSampleMessagesWithSubscriptionEventType(): List<Pair<GmailMessage, SubscriptionEventType>> {
        val fromIndex = currentOffset % sampleMessageSet.size
        val toIndex = (fromIndex + batchSize).coerceAtMost(sampleMessageSet.size)

        val slice = sampleMessageSet.slice(fromIndex until toIndex)

        // Update offset for the next call
        currentOffset = toIndex
        if (currentOffset >= sampleMessageSet.size) currentOffset = 0

        return slice.map { it to idToEmailSamples[it.id]!!.subscriptionEventType }
    }


    /**
     * Returns the number of loaded samples
     */
    fun getSampleCount(): Int = idToEmailSamples.size

    fun getSubscriptionEventType(id: String): SubscriptionEventType? {
        return idToEmailSamples[id]?.subscriptionEventType
    }

    fun getTemplate(id: String): EmailTemplate? {
        return idToEmailTemplates[id]?.let {
            EmailTemplate(
                it.subject.extractAnchors(),
                it.snippet.extractAnchors()
            )
        }
    }

    fun getSubscriptionEventTypeByTemplateId(id: String): SubscriptionEventType? {
        return idToEmailTemplates[id]?.subscriptionEventType
    }


    fun createEmailParamsFromDataset(): String {
        return idToEmailSamples.values.mapIndexed { index, emailSample ->
            emailSample.message.toPromptParamString(index)
        }.joinToString("\n")
    }


    @PostConstruct
    fun init() {
        this.companyEmailMap = loadCompanies()
        this.idToEmailTemplates = loadTemplates()
        this.idToEmailSamples = loadDataset()
        this.sampleMessageSet = idToEmailSamples.values.filter {
            it.message.senderEmail in companyEmailMap.values.toList()
                .filter { c -> c.name != "Gemini Advanced" }.slice(0..4).map { c -> c.email }
        }.shuffled().map { it.message }

        logger.debug { "EmailDatasetProvider Initialized. sample: ${idToEmailSamples.keys.first()}: ${idToEmailSamples.values.first()}" }
    }

    private fun loadCompanies(): Map<String, CompanyEmailSource> {

        val resource = resourcePatternResolver.getResource(COMPANIES_DATA_PATH)

        if (!resource.exists()) {
            throw RuntimeException("Companies reference file not found at ${resource.description}")
        }

        val rawCompanies: List<Map<String, Any>> = objectMapper.readValue(
            resource.inputStream,
            object : TypeReference<List<Map<String, Any>>>() {}
        )

        return rawCompanies.mapNotNull { company ->
            val id = company["id"] as? String
            val names = @Suppress("UNCHECKED_CAST") (company["aliasNames"] as? Map<String, String>)
            val name = names?.let { it["KO"] ?: it["EN"] }!!
            val emails = company["emailAddresses"] as? List<*>
            val firstEmail = emails?.firstOrNull() as? String

            if (id != null && firstEmail != null) id to CompanyEmailSource(
                firstEmail,
                name
            ) else null
        }.toMap()
    }

    @VisibleForTesting
    internal fun loadEmails(): List<Sample> {
        val emailsPath = EMAILS_DATA_PATH
        val emails = loadResource<Sample>(emailsPath)

        logger.debug { "Loaded ${emails.size} emails" }
        return emails
    }

    @VisibleForTesting
    internal fun loadTemplates(): Map<String, Template> {
        val templatesPath = TEMPLATES_DATA_PATH
        val templates = loadResource<Template>(templatesPath).associateBy { it.id }

        logger.debug { "Loaded ${templates.size} templates" }
        return templates
    }

    private fun loadDataset(): Map<String, EmailSample> {
        return try {
            val emails = loadEmails()
            val templates = this.idToEmailTemplates

            val dataset = emails.mapNotNull { sample ->
                val template = templates[sample.templateId]
                template?.let {
                    sample.id to EmailSample(
                        convertSampleToGmailMessage(sample),
                        sample.subscriptionEventType,
                        it.subject.extractAnchors(),
                        it.snippet.extractAnchors()
                    )
                }
            }.toMap()


            logger.debug { "Loaded ${dataset.size} dataset" }

            dataset
        } catch (e: Exception) {
            logger.error(e) { "Failed to load ground truth dataset" }
            emptyMap()
        }
    }

    private inline fun <reified T> loadResource(path: String): List<T> {
        return try {

            val resources = resourcePatternResolver.getResources(path)

            val datasetFile = resources.firstOrNull()
                ?: throw IllegalStateException("No email dataset found in classpath:data/")

            logger.info { "Loading Ground Truth dataset from: ${datasetFile.filename}" }

            datasetFile.inputStream.bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() }
                    .map { objectMapper.readValue(it, T::class.java) }.toList()
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to load ground truth dataset" }
            emptyList<T>()
        }
    }

    private fun convertSampleToGmailMessage(sample: Sample): GmailMessage {
        val company = companyEmailMap[sample.companyId]
            ?: throw Exception("Company ID '${sample.companyId}' not found")

        return GmailMessage(
            id = sample.id,
            senderEmail = company.email,
            senderName = company.name,
            internalDate = Instant.now(),
            subject = sample.subject,
            snippet = sample.snippet,
            templateId = sample.templateId
        )
    }
}