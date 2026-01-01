package com.matchalab.subscription_killer_api.config

import com.matchalab.subscription_killer_api.security.CustomOidcUser
import org.springframework.core.MethodParameter
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.core.Authentication
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.util.*

@Component
class AuthenticatedUserIdResolver(
    private val authenticationPrincipalResolver: AuthenticationPrincipalArgumentResolver
) : HandlerMethodArgumentResolver {

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

        val principal = webRequest.userPrincipal as? Authentication
            ?: throw AuthenticationCredentialsNotFoundException("User is not authenticated")

        val appUserId = (principal.principal as? CustomOidcUser)?.appUserId
            ?: throw AuthenticationCredentialsNotFoundException("Invalid principal")

        return appUserId
    }
}