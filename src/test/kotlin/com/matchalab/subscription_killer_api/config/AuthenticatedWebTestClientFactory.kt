package com.matchalab.subscription_killer_api.perf

import com.matchalab.subscription_killer_api.config.SampleGoogleAccountProperties
import com.matchalab.subscription_killer_api.domain.AppUser
import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.domain.UserRoleType
import com.matchalab.subscription_killer_api.repository.AppUserRepository
import com.matchalab.subscription_killer_api.security.CustomOidcUser
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.TestComponent
import org.springframework.http.HttpHeaders
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.session.Session
import org.springframework.session.SessionRepository
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.*

private val logger = KotlinLogging.logger {}

data class AuthenticatedClientSetup(
    val appUserId: UUID,
    val client: WebTestClient,
)

@TestComponent
@EnableConfigurationProperties(SampleGoogleAccountProperties::class)
class AuthenticatedClientFactory(
    private val appUserRepository: AppUserRepository,
    private val sessionRepository: SessionRepository<out Session>,
    private val webTestClient: WebTestClient,
    private val sampleGoogleAccountProperties: SampleGoogleAccountProperties,
) {
    private val localhost = "https://localhost:3000"
    private val sampleUserName: String = "sampleUserName"
    private lateinit var sampleAppUserId: UUID

    fun create(port: Int): AuthenticatedClientSetup {

        clear()

        val sampleAppUser =
            AppUser(
                null,
                sampleUserName,
                UserRoleType.USER,
                mutableListOf<GoogleAccount>()
            )
        sampleAppUser.addGoogleAccount(
            GoogleAccount(
                sampleGoogleAccountProperties.subject ?: "fakeSubject",
                sampleUserName,
                "sampleUserEmail",
                sampleGoogleAccountProperties.refreshToken,
                sampleGoogleAccountProperties.accessToken,
                sampleGoogleAccountProperties.expiresAt,
                sampleGoogleAccountProperties.scope
            )
        )

        sampleAppUserId = checkNotNull(appUserRepository.saveAndFlush(sampleAppUser).id) {
            "🚨 Exception: sampleAppUserId is null."
        }

        val principal: OidcUser = CustomOidcUser(sampleAppUserId, listOf(SimpleGrantedAuthority("ROLE_USER")))
        val auth = OAuth2AuthenticationToken(
            principal, principal.authorities, "google"
        )

        @Suppress("UNCHECKED_CAST")
        val repo = sessionRepository as SessionRepository<Session>
        val session = repo.createSession()
        val context: SecurityContext = SecurityContextHolder.createEmptyContext().apply {
            authentication = auth
        }

        session.setAttribute("SPRING_SECURITY_CONTEXT", context)
        val encodedSessionId = Base64.getEncoder().encodeToString(session.id.toByteArray())
        repo.save(session)

        if (!appUserRepository.existsById(sampleAppUserId)) {
            throw IllegalStateException("🚨 Setup failed: Data not found in DB before request")
        }
        if (repo.findById(session.id) == null) {
            throw IllegalStateException("🚨 Exception: Data not found in setup")
        }

        val authedClient = webTestClient.mutate().baseUrl("http://localhost:$port")
            .defaultHeader(HttpHeaders.ORIGIN, "http://localhost:$port")
            .defaultCookie("SESSION", encodedSessionId)
            .build()

        return AuthenticatedClientSetup(sampleAppUserId, authedClient)
    }

    fun clear() {
        appUserRepository.deleteAll()
    }
}
