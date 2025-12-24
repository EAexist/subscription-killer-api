package com.matchalab.subscription_killer_api.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource


private val logger = KotlinLogging.logger {}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class WebSecurityConfigIntegrationTest {

    @Autowired
    lateinit var client: WebTestClient

    @Autowired
    lateinit var corsConfigurationSource: CorsConfigurationSource

    @BeforeEach
    fun setUp() {
//        client =
//            MockMvcWebTestClient.bindToApplicationContext(wac)
//                .apply(springSecurity())
//                .configureClient()
//                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
//                .build()
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

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://localhost:3000",
            "https://subscription-killer-git-main-matchalab-project.vercel.app",
            "https://subscription-killer-git-staging-matchalab-project.vercel.app",
        ]
    )
    fun `should only allow subscription-killer frontend origins`(origin: String) {
        assertAllowedCorsGetRequest(origin)
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
