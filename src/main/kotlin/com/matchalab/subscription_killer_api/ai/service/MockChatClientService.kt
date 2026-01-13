package com.matchalab.subscription_killer_api.ai.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.matchalab.subscription_killer_api.ai.dto.EmailCategorizationResponse
import com.matchalab.subscription_killer_api.subscription.EmailTemplate
import com.matchalab.subscription_killer_api.subscription.SubscriptionEventType
import com.matchalab.subscription_killer_api.subscription.service.EmailDetectionRuleGenerationDto
import com.matchalab.subscription_killer_api.subscription.service.UpdateEmailDetectionRulesFromAIResultDto
import org.springframework.context.annotation.Profile
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service

@Service
@Profile("!ai")
class MockChatClientService(
) : ChatClientService {

    private val objectMapper = jacksonObjectMapper()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> call(
        promptTemplateStream: Resource,
        params: Map<String, Any>,
        responseType: Class<T>
    ): T {
        val promptTemplate: String = promptTemplateStream.getContentAsString(Charsets.UTF_8).trimIndent()

        if ("{emails}" in promptTemplate) {
            return EmailCategorizationResponse(
                listOf(),
                listOf(),
                listOf(),
                listOf(),
            ) as T
        }

        if ("{categorizedEmails}" in promptTemplate) {
            return UpdateEmailDetectionRulesFromAIResultDto(null, null, null, null) as T
        }

        if ("{currentMonthlyPaymentRule}" in promptTemplate) {
            return UpdateEmailDetectionRulesFromAIResultDto(
                EmailDetectionRuleGenerationDto(
                    SubscriptionEventType.SUBSCRIPTION_START,
                    EmailTemplate(
                        "계정 정보 변경",
                        "새로운 결제 수단 정보"
                    )
                ),
                EmailDetectionRuleGenerationDto(
                    SubscriptionEventType.SUBSCRIPTION_CANCEL,
                    EmailTemplate(
                        "결제 수단을 업데이트",
                        "멤버십이 현재 정지"
                    )
                ),
                null,
                null
            ) as T
        }

        return responseType.getDeclaredConstructor().newInstance()
    }

//    override fun <T : Any> call(
//        promptTemplateStream: Resource,
//        params: Map<String, Any>,
//        typeRef: ParameterizedTypeReference<T>
//    ): T {
//        val promptTemplate: String = promptTemplateStream.getContentAsString(Charsets.UTF_8).trimIndent()
//
//        return objectMapper.readValue("{}", typeRef)
//    }
}