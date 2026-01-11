package com.matchalab.subscription_killer_api.ai.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.matchalab.subscription_killer_api.subscription.SubscriptionEventType
import com.matchalab.subscription_killer_api.subscription.service.EmailDetectionRuleGenerationDto
import com.matchalab.subscription_killer_api.subscription.service.FilterAndCategorizeEmailsTaskResponse
import com.matchalab.subscription_killer_api.subscription.service.UpdateEmailDetectionRulesFromAIResultDto
import org.springframework.context.annotation.Profile
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service

@Service
@Profile("!ai && !prod")
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
            return FilterAndCategorizeEmailsTaskResponse(
                paymentStartMessages = listOf(),
                paymentCancelMessages = listOf(),
                monthlyPaymentMessages = listOf(),
                annualPaymentMessages = listOf(),
            ) as T
        }

        if ("{categorizedEmails}" in promptTemplate) {
            return UpdateEmailDetectionRulesFromAIResultDto(null, null, null, null) as T
        }

        if ("{currentMonthlyPaymentRule}" in promptTemplate) {
            return UpdateEmailDetectionRulesFromAIResultDto(
                EmailDetectionRuleGenerationDto(
                    SubscriptionEventType.PAID_SUBSCRIPTION_START,
                    listOf("계정 정보 변경"),
                    "계정 정보 변경",
                    listOf("새로운 결제 수단 정보"),
                    "새로운 결제 수단 정보"
                ),
                EmailDetectionRuleGenerationDto(
                    SubscriptionEventType.PAID_SUBSCRIPTION_CANCEL,
                    listOf("결제 수단을 업데이트"),
                    "결제 수단을 업데이트",
                    listOf("멤버십이 현재 정지"),
                    "멤버십이 현재 정지"
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