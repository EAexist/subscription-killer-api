package com.matchalab.subscription_killer_api.config

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.ContextHierarchy
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.servlet.client.MockMvcWebTestClient
import org.springframework.web.context.WebApplicationContext

@ExtendWith(value = [SpringExtension::class])
@WebAppConfiguration("classpath:META-INF/web-resources")
@ContextHierarchy(value = [ContextConfiguration(classes = [WebSecurityConfig::class])])
class CorsConfigTest @Autowired constructor() {

    @Autowired lateinit var wac: WebApplicationContext
    lateinit var client: WebTestClient

    @BeforeEach
    fun setUp() {
        client = MockMvcWebTestClient.bindToApplicationContext(wac).configureClient().build()
    }

    @Test
    fun `should return 403 Forbbiden when origin is not allowed by CORS`() {
        client.get()
                .uri("/ping")
                .header(HttpHeaders.ORIGIN, "https://www.google.com/")
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectHeader()
                .valueEquals("accessControlAllowOrigin", null)
    }

    @Test
    fun `should return 200 OK when origin is subscription-killer frontend(production)`() {
        client.get()
                .uri("/ping")
                .header(
                        HttpHeaders.ORIGIN,
                        "https://subscription-killer-git-main-matchalab-project.vercel.app"
                )
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals(
                        "accessControlAllowOrigin",
                        "https://subscription-killer-git-main-matchalab-project.vercel.app"
                )
    }

    @Test
    fun `should return 200 OK when origin is subscription-killer frontend(preview)`() {
        client.get()
                .uri("/ping")
                .header(
                        HttpHeaders.ORIGIN,
                        "https://subscription-killer-git-staging-matchalab-project.vercel.app"
                )
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals(
                        "accessControlAllowOrigin",
                        "https://subscription-killer-git-staging-matchalab-project.vercel.app"
                )
    }

    @Test
    fun `should return 200 OK when origin is subscription-killer frontend(local https dev server)`() {
        client.get()
                .uri("/ping")
                .header(HttpHeaders.ORIGIN, "https://localhost:3000")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals("accessControlAllowOrigin", "https://localhost:3000")
    }
}
