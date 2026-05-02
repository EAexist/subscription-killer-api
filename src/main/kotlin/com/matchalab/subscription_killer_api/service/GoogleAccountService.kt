package com.matchalab.subscription_killer_api.service

import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.repository.GoogleAccountRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import kotlin.jvm.optionals.getOrNull

private val logger = KotlinLogging.logger {}

@Service
class GoogleAccountService(
    private val googleAccountRepository: GoogleAccountRepository,
) {
    fun save(googleAccount: GoogleAccount): GoogleAccount {
        return googleAccountRepository.save(googleAccount)
    }

    fun findById(subject: String): GoogleAccount {
        return googleAccountRepository.findById(subject).getOrNull()
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "GoogleAccount subject=$subject not found"
            )
    }

    @Transactional
    fun getLastEmailSyncedAt(subject: String): Instant? {
        val googleAccount = findById(subject)
        val lastEmailSyncedAt = googleAccount.lastEmailSyncedAt
        return lastEmailSyncedAt
    }

    fun findByIdWithSubscriptions(subject: String): GoogleAccount {
        return googleAccountRepository.findByIdWithSubscriptions(subject)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "GoogleAccount subject=$subject not found"
            )
    }
}
