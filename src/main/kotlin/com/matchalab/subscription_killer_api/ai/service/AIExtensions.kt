package com.matchalab.subscription_killer_api.ai.service

import org.springframework.core.io.Resource

inline fun <reified T : Any> ChatClientService.call(
    promptTemplateStream: Resource,
    params: Map<String, Any> = emptyMap(),
): T {
    return call(promptTemplateStream, params, T::class.java)
}

//inline fun <reified T : Any> ChatClientService.callList(
//    promptTemplateStream: Resource,
//    params: Map<String, Any> = emptyMap(),
//): List<T> {
//    val typeRef = object : ParameterizedTypeReference<List<T>>() {}
//    return call(promptTemplateStream, params, typeRef)
//}