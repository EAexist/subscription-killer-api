package com.matchalab.subscription_killer_api.service

import com.matchalab.subscription_killer_api.repository.AppUserRepository
import com.matchalab.subscription_killer_api.security.CustomUserDetails
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException

private val logger = KotlinLogging.logger {}

// @Service
class CustomUserDetailsService(private val appUserRepository: AppUserRepository) :
        UserDetailsService {

    companion object {
        private const val NO_PASSWORD = "{noop}"
    }

    override fun loadUserByUsername(username: String): UserDetails {

        val userId =
                try {
                    UUID.fromString(username)
                } catch (e: IllegalArgumentException) {
                    throw UsernameNotFoundException("Invalid user ID format: $username", e)
                }

        val appUser =
                appUserRepository.findById(userId).orElseThrow {
                    UsernameNotFoundException("User not found with ID: $username")
                }

        logger.info { "appUser.id: ${appUser.id}\nuserAccount.userRole: ${appUser.userRole}" }

        return CustomUserDetails(appUser)
        // User.builder()
        //         .username(appUser.id.toString()) // Username is the UUID string
        //         .password(NO_PASSWORD)
        //         .authorities(appUser.userRole.authority)
        //         .build()
    }
}
