package com.matchalab.subscription_killer_api.ai.service

data class ExtractEmailTemplatesResponse(
    val result: List<ExtractEmailTemplatesResponseItem> = listOf()
)

data class ExtractEmailTemplatesResponseItem(
    val m: Int,
    val j: List<String>,
    val p: List<String>,
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
