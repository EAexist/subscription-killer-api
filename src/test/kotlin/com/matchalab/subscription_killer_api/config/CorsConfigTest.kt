package com.matchalab.subscription_killer_api.config

import com.matchalab.subscription_killer_api.controller.PingController
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.servlet.client.MockMvcWebTestClient
import org.springframework.web.context.WebApplicationContext

@ExtendWith(SpringExtension::class)
@WebAppConfiguration("classpath:META-INF/web-resources")
@ContextConfiguration(
        classes = [WebSecurityConfig::class, CorsConfig::class, PingController::class]
)
class CorsConfigTest {

    companion object {
        private val log = LoggerFactory.getLogger(CorsConfigTest::class.java)
    }

    @Autowired lateinit var wac: WebApplicationContext
    lateinit var client: WebTestClient

    @BeforeEach
    fun setUp() {
        client =
                MockMvcWebTestClient.bindToApplicationContext(wac)
                        .configureClient()
                        .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                        .build()
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
                .valueEquals("accessControlAllowOrigin", allowedOrigin)
    }
}
