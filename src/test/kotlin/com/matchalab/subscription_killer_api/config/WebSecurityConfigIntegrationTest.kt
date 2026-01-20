package com.matchalab.subscription_killer_api.config

import com.matchalab.subscription_killer_api.security.config.CorsProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

private val logger = KotlinLogging.logger {}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("google-auth")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(
    SharedTestcontainersConfig::class
)
@EnableConfigurationProperties(CorsProperties::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class WebSecurityConfigIntegrationTest(private val corsProperties: CorsProperties) {

    @Autowired
    lateinit var client: WebTestClient

    @Autowired
    lateinit var wac: WebApplicationContext


    @Autowired
    lateinit var corsConfigurationSource: CorsConfigurationSource

    private val oauth2GoogleAuthorizationPath = "/oauth2/authorization/google"
    private val oauth2GoogleRedirectedPath = "/login/oauth2/code/google"
    private val testUserName = "testUserName"

    @BeforeEach
    fun setUp() {
    }

    @Test
    fun inspectLoadedCorsConfig() {
        val request: MockHttpServletRequest = MockHttpServletRequest("OPTIONS", "/ping")
        request.addHeader(
            "Origin",
            "https://subscription-killer-git-main-matchalab-project.vercel.app"
        )
        request.addHeader("Access-Control-Request-Method", "GET")

        val config: CorsConfiguration? = corsConfigurationSource.getCorsConfiguration(request)

        if (config == null) {
            logger.debug { "🚨 ERROR: No CORS Configuration found for /ping" }
        } else {
            logger.debug { "✅ Loaded Config for /ping:" }
            logger.debug { "    Config: $config" }
        }
    }

    @Test
    fun `should return 403 Forbidden for unknown origins`() {
        client.options()
            .uri("/ping")
            .header(HttpHeaders.ORIGIN, "https://www.google.com/")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
            .exchange()
            .expectStatus()
            .isForbidden()

        client.get()
            .uri("/ping")
            .header(HttpHeaders.ORIGIN, "https://www.google.com/")
            .exchange()
            .expectStatus()
            .isForbidden()
    }

    fun allowedOrigins() = corsProperties.allowedOrigins

    @ParameterizedTest
    @MethodSource("allowedOrigins")
    fun `should only allow subscription-killer frontend origins`(origin: String) {
        assertAllowedCorsGetRequest(origin)
    }

    @Test
    fun `when hitting authorization endpoint should redirect to Google with custom params`() {
        client.get().uri(oauth2GoogleAuthorizationPath).exchange()
            .expectStatus()
            .is3xxRedirection()
            .expectHeader().value(HttpHeaders.LOCATION) { location ->
                assertThat(location).contains("accounts.google.com")
                assertThat(location).contains("access_type=offline")
                assertThat(location).contains("prompt=consent%20select_account")
                assertThat(URLDecoder.decode(location, StandardCharsets.UTF_8))
                    .contains("scope=openid email profile https://www.googleapis.com/auth/gmail.readonly")
            }
    }

    @Test
    fun `when unauthenticated user access business path should return 401 Unauthorized`() {
        client.get().uri("/api/v1/appUser").exchange().expectStatus().isUnauthorized()
    }

    fun assertAllowedCorsGetRequest(allowedOrigin: String) {
        client.options()
            .uri("/ping")
            .header(HttpHeaders.ORIGIN, allowedOrigin)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, allowedOrigin)
            .expectHeader()
            .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
            .expectHeader()
            .exists(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)
            .expectHeader()
            .exists(HttpHeaders.ACCESS_CONTROL_MAX_AGE)

        client.get()
            .uri("/ping")
            .header(HttpHeaders.ORIGIN, allowedOrigin)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, allowedOrigin)
    }
}
