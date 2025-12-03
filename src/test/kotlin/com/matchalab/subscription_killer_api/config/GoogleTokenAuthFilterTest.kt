package com.matchalab.subscription_killer_api.config

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.matchalab.subscription_killer_api.core.dto.AppUserResponseDTO
import com.matchalab.subscription_killer_api.core.dto.GoogleAccountResponseDTO
import com.matchalab.subscription_killer_api.domain.AppUser
import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.repository.AppUserRepository
import com.matchalab.subscription_killer_api.service.TokenVerifierService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.client.MockMvcWebTestClient
import org.springframework.web.context.WebApplicationContext

@SpringBootTest
@AutoConfigureMockMvc
class GoogleTokenAuthFilterTest
@Autowired
constructor(
        val appUserRepository: AppUserRepository,
        val mockMvc: MockMvc,
) {

    @Autowired lateinit var wac: WebApplicationContext
    lateinit var client: WebTestClient

    @MockitoBean private lateinit var googleVerifierService: TokenVerifierService

    private val fakeSubject = "fakeSubject"
    private val fakeEmail = "test@mock.com"
    private val fakeName = "fakeName"

    private var fakePayload =
            GoogleIdToken.Payload().apply {
                subject = fakeSubject
                email = fakeEmail
            }

    private val expectedGoogleAccountResponseDTO: GoogleAccountResponseDTO =
            GoogleAccountResponseDTO(fakeSubject, fakeName)
    private val expectedAppUserResponseDTO: AppUserResponseDTO =
            AppUserResponseDTO(fakeName, listOf(expectedGoogleAccountResponseDTO))

    @BeforeEach
    fun setUp() {
        fakePayload.set("name", fakeName)
        client =
                MockMvcWebTestClient.bindToApplicationContext(wac)
                        .configureClient()
                        .defaultHeader(HttpHeaders.ORIGIN, "https://localhost:3000")
                        .baseUrl("/api/v1")
                        .build()

        `when`(googleVerifierService.verifyToken("INVALID_TOKEN"))
                .thenThrow(RuntimeException("Token verification failed"))

        `when`(googleVerifierService.verifyToken("FAKE_VALID_FIRST_SEEN_TOKEN"))
                .thenReturn(fakePayload)

        `when`(googleVerifierService.verifyToken("FAKE_VALID_EXISTING_TOKEN"))
                .thenReturn(fakePayload)
    }

    @AfterEach
    fun cleanDatabase() {
        appUserRepository.deleteAll()
    }

    @Test
    fun `should return 401 Unauthorized when requested login with invalid Google id token`() {

        val requestBody = mapOf("idToken" to "FAKE_INVALID_TOKEN")
        client.post()
                .uri("/auth")
                .bodyValue(requestBody)
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .isEmpty()
    }

    @Test
    fun `should return new AppUser DTO when requested login with valid and first-seen Google id token`() {

        val requestBody = mapOf("idToken" to "FAKE_VALID_FIRST_SEEN_TOKEN")

        client.post()
                .uri("/auth")
                .bodyValue(requestBody)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody<AppUserResponseDTO>()
                .consumeWith { result ->
                    val appUserResponseDTO: AppUserResponseDTO = result.responseBody!!

                    assertThat(appUserResponseDTO).isEqualTo(expectedAppUserResponseDTO)
                }
    }

    @Test
    fun `should return matching AppUser DTO when requested login with valid and existing Google id token`() {

        val appUser: AppUser = AppUser(null, fakeName)
        appUser.addGoogleAccount(GoogleAccount(fakeSubject, fakeName, null))
        appUserRepository.save(appUser)

        val requestBody = mapOf("idToken" to "FAKE_VALID_EXISTING_TOKEN")

        client.post()
                .uri("/auth")
                .bodyValue(requestBody)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody<AppUserResponseDTO>()
                .consumeWith { result ->
                    val appUserResponseDTO: AppUserResponseDTO = result.responseBody!!

                    assertThat(appUserResponseDTO).isEqualTo(expectedAppUserResponseDTO)
                }
    }
}
