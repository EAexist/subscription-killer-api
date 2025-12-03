package com.matchalab.subscription_killer_api.config

import com.matchalab.subscription_killer_api.controller.PingController
import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.servlet.client.MockMvcWebTestClient
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource

private val logger = KotlinLogging.logger {}

@WebMvcTest(PingController::class)
@Import(WebSecurityConfig::class, CorsConfig::class)
class CorsConfigTest {

    @Autowired lateinit var wac: WebApplicationContext
    lateinit var client: WebTestClient

    @Autowired lateinit var corsConfigurationSource: CorsConfigurationSource

    @BeforeEach
    fun setUp() {
        client =
                MockMvcWebTestClient.bindToApplicationContext(wac)
                        .apply(springSecurity())
                        .configureClient()
                        .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                        .build()
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
            logger.debug { "    Config: " + config.toString() }
        }
    }

    @Test
    fun `should return 403 Forbbiden when origin is not allowed by CORS`() {
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
                .isOk()
                .expectHeader()
                .doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)
    }

    @Test
    fun `should return 200 OK when origin is subscription-killer frontend(production)`() {
        assertAllowedCorsGetRequest(
                "https://subscription-killer-git-main-matchalab-project.vercel.app"
        )
    }

    @Test
    fun `should return 200 OK when origin is subscription-killer frontend(preview)`() {
        assertAllowedCorsGetRequest(
                "https://subscription-killer-git-staging-matchalab-project.vercel.app"
        )
    }

    @Test
    fun `should return 200 OK when origin is subscription-killer frontend(local https dev server)`() {
        assertAllowedCorsGetRequest("https://localhost:3000")
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
