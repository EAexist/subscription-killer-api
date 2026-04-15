package com.matchalab.subscription_killer_api.service

import com.matchalab.subscription_killer_api.config.GuestAppUserProperties
import com.matchalab.subscription_killer_api.core.dto.AddGoogleAccountCommand
import com.matchalab.subscription_killer_api.domain.AppUser
import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.repository.AppUserRepository
import com.matchalab.subscription_killer_api.subscription.config.MailProperties
import com.matchalab.subscription_killer_api.utils.DateTimeUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.*

private val logger = KotlinLogging.logger {}

@Service
class AppUserService(
    private val appUserRepository: AppUserRepository,
    private val guestAppUserProperties: GuestAppUserProperties,
    private val mailProperties: MailProperties
) {

    fun existsByName(name: String): Boolean {
        return appUserRepository.existsByName(name)
    }

    fun findByIdOrNull(appUserId: UUID): AppUser? {
        return appUserRepository.findByIdOrNull(appUserId)
    }

    fun findByIdOrNotFound(appUserId: UUID): AppUser {
        return findByIdOrNull(appUserId) ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "AppUser appUserId=$appUserId not found"
        )
    }

    fun findGoogleAccountSubjectsByAppUserId(appUserId: UUID): List<String> {
        return appUserRepository.findGoogleAccountSubjectsByAppUserId(appUserId)
    }

    fun findByIdWithGoogleAccounts(appUserId: UUID): AppUser {
        return appUserRepository.findByIdWithGoogleAccounts(appUserId)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "AppUser appUserId=$appUserId not found"
            )
    }

    fun findByGoogleAccounts_Subject(googleSub: String): AppUser? {
        return appUserRepository.findByGoogleAccounts_Subject(googleSub)
    }

    fun getGuestAppUser(): AppUser {
        return findByIdOrNotFound(guestAppUserProperties.id)
    }

    fun findLastEmailSyncedAtByUserId(appUserId: UUID): Instant? {
        return appUserRepository.findLastEmailSyncedAtByUserId(appUserId)
    }

    fun save(appUser: AppUser): AppUser {
        return appUserRepository.save(appUser)
    }

    fun findGoogleAccountsWithFullSubscriptions(appUserId: UUID): List<GoogleAccount> {
        return appUserRepository.findGoogleAccountsWithFullSubscriptions(appUserId)
    }

    fun register(user: OidcUser): AppUser {
        val appUser = AppUser(
            name = user.givenName
        )
        addGoogleAccount(
            appUser,
            AddGoogleAccountCommand(
                user.name, user.givenName, user.email
            )
        )

        return appUserRepository.save(
            appUser
        )
    }

    fun addGoogleAccount(
        appUser: AppUser,
        command: AddGoogleAccountCommand,
    ) {
        appUser.addGoogleAccount(
            GoogleAccount(
                subject = command.subject,
                name = command.name,
                email = command.email,
                refreshToken = command.refreshToken,
                accessToken = command.accessToken,
                expiresAt = command.expiresAt,
                scope = command.scope,
                lastEmailSyncedAt = DateTimeUtils.minusMonthsFromInstant(
                    Instant.now(),
                    mailProperties.analysisMonths
                )
            )
        )
    }
}
