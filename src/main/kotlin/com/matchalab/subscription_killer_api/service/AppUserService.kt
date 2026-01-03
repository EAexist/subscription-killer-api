package com.matchalab.subscription_killer_api.service

import com.matchalab.subscription_killer_api.domain.AppUser
import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.repository.AppUserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.*

private val logger = KotlinLogging.logger {}

@Service
class AppUserService(private val appUserRepository: AppUserRepository) {

    fun findByIdOrNull(appUserId: UUID): AppUser? {
        return appUserRepository.findByIdOrNull(appUserId)
    }

    fun findByIdOrNotFound(appUserId: UUID): AppUser {
        return findByIdOrNull(appUserId) ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "User profile not found"
        )
    }

    fun findGoogleAccountSubjectsByAppUserId(appUserId: UUID): List<String> {
        return appUserRepository.findGoogleAccountSubjectsByAppUserId(appUserId)
    }

    fun findByIdWithAccounts(appUserId: UUID): AppUser? {
        return appUserRepository.findByIdWithAccounts(appUserId)
    }

    fun findByGoogleAccounts_Subject(googleSub: String): AppUser? {
        return appUserRepository.findByGoogleAccounts_Subject(googleSub)
    }

    fun register(user: OidcUser): AppUser {
        val appUser = AppUser(
            name = user.givenName
        )
        appUser.addGoogleAccount(GoogleAccount(subject = user.name, name = user.givenName, email = user.email))

        return appUserRepository.save(
            appUser
        )
    }

//    fun getAppUser(): AppUser {
//        val count = appUserRepository.count()
//        logger.debug { "\uD83D\uDE80 Total users in DB: $count" }
//        val appUserId = getAppUserId()
//        return appUserRepository.findById(appUserId)
//            .orElseThrow { EntityNotFoundException("User not found with id $appUserId") }
//    }

//    fun getAppUser(appUserId: UUID): AppUser {
//        return appUserRepository.findById(appUserId)
//            .orElseThrow { EntityNotFoundException("User not found with id $appUserId") }
//    }
//    @Transactional
//    fun findByGoogleAccountSubjectOrRegister(
//        payload: GoogleIdToken.Payload
//    ): LoginOrRegisterResult {
//        val existingAppUser: AppUser? =
//            appUserRepository.findByGoogleAccounts_Subject(payload.subject)
//
//        return existingAppUser?.let { appUser -> LoginOrRegisterResult.LoggedIn(appUser) }
//            ?: run {
//                val newUser = AppUser(name = payload["name"] as String)
//                val newGoogleAccount = GoogleAccount(payload)
//
//                newUser.addGoogleAccount(newGoogleAccount)
//                val savedUser = appUserRepository.save(newUser)
//
//                LoginOrRegisterResult.Registered(savedUser)
//            }
//
//        // return appUserRepository.findByGoogleAccounts_Subject(payload.subject).orElseGet {
//        //     val newUser = AppUser(name = payload.get("name") as String)
//        //     val newGoogleAccount = GoogleAccount(payload)
//        //     newUser.addGoogleAccount(newGoogleAccount)
//        //     val savedUser = appUserRepository.save(newUser)
//        //     return@orElseGet savedUser
//        // }
//    }
}
