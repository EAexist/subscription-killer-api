package com.matchalab.subscription_killer_api.ai.service

import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.io.Resource

interface ChatClientService {
    fun <T : Any> call(
        promptTemplateStream: Resource,
        params: Map<String, Any> = emptyMap(),
        responseType: Class<T>
    ): T

    fun <T : Any> call(
        promptTemplateStream: Resource,
        params: Map<String, Any> = emptyMap(),
        typeRef: ParameterizedTypeReference<T>
    ): T
}
