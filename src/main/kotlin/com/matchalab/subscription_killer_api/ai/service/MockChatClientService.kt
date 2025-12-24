//package com.matchalab.subscription_killer_api.ai.service
//
//import com.fasterxml.jackson.core.type.TypeReference
//import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
//import org.springframework.core.io.Resource
//
////@Service
////@Profile("!gcp")
//class MockChatClientService(
//) : ChatClientService {
//
//    private val objectMapper = jacksonObjectMapper()
//
//    override fun <T : Any> call(
//        promptTemplateStream: Resource,
//        params: Map<String, Any>,
//        responseType: Class<T>
//    ): T {
//
//        return responseType.getDeclaredConstructor().newInstance()
//    }
//
//    override fun <T : Any> call(
//        promptTemplateStream: Resource,
//        params: Map<String, Any>,
//        typeRef: TypeReference<T>
//    ): T {
//        return objectMapper.readValue("{}", typeRef)
//    }
//}