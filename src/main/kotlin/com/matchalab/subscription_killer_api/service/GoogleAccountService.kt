package com.matchalab.subscription_killer_api.service

import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.repository.GoogleAccountRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.*
import kotlin.jvm.optionals.getOrNull

private val logger = KotlinLogging.logger {}

@Service
class GoogleAccountService(
    private val googleAccountRepository: GoogleAccountRepository,
) {
    fun findById(subject: String): GoogleAccount {
        return googleAccountRepository.findById(subject).getOrNull()
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "GoogleAccount subject=$subject not found"
            )
    }

    fun getAnalyzeAfter(subject: String): Instant {
        return findById(subject).lastEmailSyncedAt
    }

    fun existsAnalyzedSubscriptionByAppUserId(appUserId: UUID): Boolean {
        return googleAccountRepository.existsAnalyzedSubscriptionByAppUserId(appUserId)
    }

    fun findByIdWithSubscriptions(subject: String): GoogleAccount {
        return googleAccountRepository.findByIdWithSubscriptions(subject)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "GoogleAccount subject=$subject not found"
            )
    }
}
