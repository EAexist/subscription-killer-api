package com.matchalab.subscription_killer_api.service

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.matchalab.subscription_killer_api.domain.AppUser
import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.domain.LoginOrRegisterResult
import com.matchalab.subscription_killer_api.repository.AppUserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class AppUserService(private val appUserRepository: AppUserRepository) {
    @Transactional
    fun findByGoogleAccountSubjectOrRegister(
            payload: GoogleIdToken.Payload
    ): LoginOrRegisterResult {
        val existingAppUser: AppUser? =
                appUserRepository.findByGoogleAccounts_Subject(payload.subject)

        return existingAppUser?.let { appUser -> LoginOrRegisterResult.LoggedIn(appUser) }
                ?: run {
                    val newUser = AppUser(name = payload["name"] as String)
                    val newGoogleAccount = GoogleAccount(payload)

                    newUser.addGoogleAccount(newGoogleAccount)
                    val savedUser = appUserRepository.save(newUser)

                    LoginOrRegisterResult.Registered(savedUser)
                }

        // return appUserRepository.findByGoogleAccounts_Subject(payload.subject).orElseGet {
        //     val newUser = AppUser(name = payload.get("name") as String)
        //     val newGoogleAccount = GoogleAccount(payload)
        //     newUser.addGoogleAccount(newGoogleAccount)
        //     val savedUser = appUserRepository.save(newUser)
        //     return@orElseGet savedUser
        // }
    }
}
