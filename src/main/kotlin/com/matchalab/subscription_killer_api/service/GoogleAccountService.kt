package com.matchalab.subscription_killer_api.service

import com.matchalab.subscription_killer_api.config.GuestAppUserProperties
import com.matchalab.subscription_killer_api.domain.AppUser
import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.repository.AppUserRepository
import com.matchalab.subscription_killer_api.repository.GoogleAccountRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.*
import kotlin.jvm.optionals.getOrNull

private val logger = KotlinLogging.logger {}

@Service
class GoogleAccountService(
    private val googleAccountRepository: GoogleAccountRepository,
) {
    fun findById(subject: String): GoogleAccount {
        return googleAccountRepository.findById(subject).getOrNull() ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "GoogleAccount subject=$subject not found"
        )
    }

    fun existsAnalyzedSubscriptionByAppUserId(appUserId: UUID): Boolean {
        return googleAccountRepository.existsAnalyzedSubscriptionByAppUserId(appUserId)
    }

    fun findByIdWithSubscriptions(subject: String): GoogleAccount {
        return googleAccountRepository.findByIdWithSubscriptions(subject) ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "GoogleAccount subject=$subject not found"
        )
    }
}
