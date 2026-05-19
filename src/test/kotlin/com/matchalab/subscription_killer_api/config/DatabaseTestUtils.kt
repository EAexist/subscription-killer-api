package com.matchalab.sublog_api.config

import com.matchalab.sublog_api.core.dto.AddGoogleAccountCommand
import com.matchalab.sublog_api.domain.AppUser
import com.matchalab.sublog_api.domain.GoogleAccount
import com.matchalab.sublog_api.domain.UserRoleType
import com.matchalab.sublog_api.repository.AppUserRepository
import com.matchalab.sublog_api.service.AppUserService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.test.context.TestConfiguration
import java.util.*

private val logger = KotlinLogging.logger {}

@TestConfiguration
class DatabaseTestUtils(
    private val appUserRepository: AppUserRepository,
    private val appUserService: AppUserService,
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
            AddGoogleAccountCommand(
                "TEST_GOOGLE_ACCOUNT_A",
                "TEST_APP_USER_NAME",
                "TEST_GOOGLE_ACCOUNT_A@example.com",
            ),
            AddGoogleAccountCommand(
                "TEST_GOOGLE_ACCOUNT_B",
                "TEST_APP_USER_NAME",
                "TEST_GOOGLE_ACCOUNT_B@example.com",
            ),
        ).forEach {
            appUserService.addGoogleAccount(
                testAppUser,
                it
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
        appUserRepository.deleteById(testAppUserId)
    }
}
