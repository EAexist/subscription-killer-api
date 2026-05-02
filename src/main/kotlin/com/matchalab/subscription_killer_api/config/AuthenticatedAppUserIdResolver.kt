package com.matchalab.subscription_killer_api.config

import com.matchalab.subscription_killer_api.security.CustomOidcUser
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.MethodParameter
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.util.*

@Component
class AuthenticatedAppUserIdResolver : HandlerMethodArgumentResolver {

    private val logger = KotlinLogging.logger {}

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        //@TODO Use AppUSerId Custom Object Instead of UUID
        parameter.parameterType == UUID::class.java &&
                parameter.hasParameterAnnotation(AuthenticatedUser::class.java)

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?
    ): UUID {
//        logger.debug { "AuthenticatedAppUserIdResolver - Resolving argument" }

        val authentication = SecurityContextHolder.getContext().authentication
//        logger.debug { "AuthenticatedAppUserIdResolver - Authentication: $authentication" }

        if (authentication == null || !authentication.isAuthenticated) {
//            logger.debug { "AuthenticatedAppUserIdResolver - No authentication found" }
            throw AuthenticationCredentialsNotFoundException("User is not authenticated")
        }

        val appUserId = (authentication.principal as? CustomOidcUser)?.appUserId
//        logger.debug { "AuthenticatedAppUserIdResolver - AppUserId: $appUserId" }

        return appUserId ?: throw AuthenticationCredentialsNotFoundException("Invalid principal")
    }
}