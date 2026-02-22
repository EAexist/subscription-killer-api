package com.matchalab.subscription_killer_api.ai.service

class ExtractEmailTemplatesResponse(
    val result: List<Map<String, String>>
)

interface ChatClientService {

    fun categorizeEmails(
        prompt: String,
        params: Map<String, String>,
        dataCount: Int?
    ): Map<String, List<Int>>

    fun extractEmailTemplates(
        prompt: String,
        params: Map<String, String>,
        dataCount: Int?
    ): ExtractEmailTemplatesResponse
}
