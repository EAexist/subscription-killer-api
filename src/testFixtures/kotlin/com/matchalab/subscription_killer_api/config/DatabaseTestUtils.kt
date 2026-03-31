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
class DatabaseTestUtils(
    private val appUserRepository: AppUserRepository,
) {
    private val testUserName: String = "TEST_APP_USER_NAME"
    private lateinit var testAppUserId: UUID

    fun setUser(): UUID {

        val testAppUser =
            AppUser(
                null,
                testUserName,
                UserRoleType.USER,
                mutableListOf<GoogleAccount>()
            )

        listOf(
            GoogleAccount(
                "TEST_GOOGLE_ACCOUNT_A",
                "TEST_APP_USER_NAME",
                "TEST_GOOGLE_ACCOUNT_A@example.com",
            ),
            GoogleAccount(
                "TEST_GOOGLE_ACCOUNT_B",
                "TEST_APP_USER_NAME",
                "TEST_GOOGLE_ACCOUNT_B@example.com",
            ),
        ).forEach {
            testAppUser.addGoogleAccount(
                GoogleAccount(
                    it.subject,
                    testUserName,
                    it.email,
                    it.refreshToken,
                    it.accessToken,
                    it.expiresAt,
                    it.scope
                )
            )
        }

        testAppUserId = checkNotNull(appUserRepository.saveAndFlush(testAppUser).id) {
            "🚨 Exception: testAppUserId is null."
        }

        if (!appUserRepository.existsById(testAppUserId)) {
            throw IllegalStateException("🚨 Setup failed: Data not found in DB before request")
        }

        return testAppUserId
    }

    fun clear() {
        appUserRepository.deleteById (testAppUserId)
    }
}
