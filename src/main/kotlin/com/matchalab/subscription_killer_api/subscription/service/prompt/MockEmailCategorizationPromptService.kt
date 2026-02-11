package com.matchalab.subscription_killer_api.subscription.service.prompt

import com.matchalab.subscription_killer_api.ai.dto.EmailCategorizationResponse
import com.matchalab.subscription_killer_api.subscription.GmailMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Profile("!ai")
@Service
class MockEmailCategorizationPromptServiceImpl(
) : EmailCategorizationPromptService {

    override fun run(messages: List<GmailMessage>): EmailCategorizationResponse {

        val messageIds = messages.map { it.id }

        val sample = EmailCategorizationResponseFactory.createUniqueSample()

        return EmailCategorizationResponse(
            subsStartMsgIds = sample.subsStartMsgIds.filter { it in messageIds },
            subsCancelMsgIds = sample.subsCancelMsgIds.filter { it in messageIds },
            monthlyMsgIds = sample.monthlyMsgIds.filter { it in messageIds },
            annualMsgIds = sample.annualMsgIds.filter { it in messageIds },
        )
    }
}