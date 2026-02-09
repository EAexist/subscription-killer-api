package com.matchalab.subscription_killer_api.config

import com.matchalab.subscription_killer_api.domain.AppUser
import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.domain.UserRoleType
import com.matchalab.subscription_killer_api.repository.AppUserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.TestConfiguration
import java.util.*

private val logger = KotlinLogging.logger {}

@TestConfiguration
@EnableConfigurationProperties(SampleGoogleAccountListProperties::class)
class DatabaseTestUtils(
    private val appUserRepository: AppUserRepository,
    private val googleAccountProperties: SampleGoogleAccountListProperties,
) {
    private val sampleUserName: String = "sampleUserName"
    private lateinit var sampleAppUserId: UUID

    fun setUser(): UUID {

        clear()

        val sampleAppUser =
            AppUser(
                null,
                sampleUserName,
                UserRoleType.USER,
                mutableListOf<GoogleAccount>()
            )

        logger.info { "Configuring Google accounts: ${googleAccountProperties.samples.size} account(s) found" }
        googleAccountProperties.samples.forEachIndexed { index, account ->
            logger.info { "Google Account [$index]: email=${account.email}, subject=${account.subject}, scope=${account.scope}" }
        }

        googleAccountProperties.samples.forEach {
            sampleAppUser.addGoogleAccount(
                GoogleAccount(
                    it.subject,
                    sampleUserName,
                    it.email,
                    it.refreshToken,
                    it.accessToken,
                    it.expiresAt,
                    it.scope
                )
            )
        }

        sampleAppUserId = checkNotNull(appUserRepository.saveAndFlush(sampleAppUser).id) {
            "🚨 Exception: sampleAppUserId is null."
        }

        if (!appUserRepository.existsById(sampleAppUserId)) {
            throw IllegalStateException("🚨 Setup failed: Data not found in DB before request")
        }

        return sampleAppUserId
    }

    fun clear() {
        appUserRepository.deleteAll()
    }
}
