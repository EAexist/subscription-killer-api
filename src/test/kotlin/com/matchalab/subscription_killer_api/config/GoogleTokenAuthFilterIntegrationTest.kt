package com.matchalab.subscription_killer_api.config

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.matchalab.subscription_killer_api.core.dto.AppUserResponseDto
import com.matchalab.subscription_killer_api.core.dto.GoogleAccountResponseDto
import com.matchalab.subscription_killer_api.domain.AppUser
import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.repository.AppUserRepository
import com.matchalab.subscription_killer_api.service.TokenVerifierService
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.`when`
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import java.util.*

//@SpringBootTest
@AutoConfigureMockMvc
class GoogleTokenAuthFilterIntegrationTest
//@Autowired
constructor(val appUserRepository: AppUserRepository, val client: WebTestClient) {

    lateinit var customClient: WebTestClient

    @MockitoBean
    private lateinit var googleVerifierService: TokenVerifierService

    private val fakeSubject = "fakeSubject"
    private val fakeEmail = "test@gmail.com"
    private val fakeName = "fakeName"

    private var fakePayload =
        GoogleIdToken.Payload().apply {
            subject = fakeSubject
            email = fakeEmail
        }

    private val expectedGoogleAccountResponseDto: GoogleAccountResponseDto =
        GoogleAccountResponseDto(fakeSubject, fakeName)
    private val expectedAppUserResponseDto: AppUserResponseDto =
        AppUserResponseDto(fakeName, listOf(expectedGoogleAccountResponseDto))

    @BeforeEach
    fun setUp() {
        fakePayload.set("name", fakeName)
        customClient =
            client.mutate()
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
    fun clearRepositories() {
        appUserRepository.deleteAll()
    }

    //    @Test
    fun `should return 400 BadRequest when requested login with invalid LoginRequestDto format`() {

        val requestBody: Map<String, Any> = Collections.emptyMap()
        customClient
            .post()
            .uri("/appUser")
            .bodyValue(requestBody)
            .exchange()
            .expectStatus()
            .isBadRequest()
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo(HttpServletResponse.SC_BAD_REQUEST)
            .jsonPath("$.error")
            .isEqualTo("Invalid Request Body")
    }

    //    @Test
    fun `should return 401 Unauthorized when requested login with invalid Google id token`() {

        val requestBody = mapOf("idToken" to "INVALID_TOKEN")
        customClient
            .post()
            .uri("/appUser")
            .bodyValue(requestBody)
            .exchange()
            .expectStatus()
            .isUnauthorized()
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo(HttpServletResponse.SC_UNAUTHORIZED)
            .jsonPath("$.error")
            .isEqualTo("Authentication Failed")
    }

    //    @Test
    fun `should return new AppUser DTO when requested login with valid and first-seen Google id token`() {

        val requestBody = mapOf("idToken" to "FAKE_VALID_FIRST_SEEN_TOKEN")

        customClient
            .post()
            .uri("/appUser")
            .bodyValue(requestBody)
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody<AppUserResponseDto>()
            .consumeWith { result ->
                val appUserResponseDto: AppUserResponseDto = result.responseBody!!

                assertThat(appUserResponseDto).isEqualTo(expectedAppUserResponseDto)
            }
    }

    //    @Test
    fun `should return matching AppUser DTO when requested login with valid and existing Google id token`() {

        val appUser: AppUser = AppUser(null, fakeName)
        appUser.addGoogleAccount(GoogleAccount(fakeSubject, fakeName, fakeEmail))
        appUserRepository.save(appUser)

        val requestBody = mapOf("idToken" to "FAKE_VALID_EXISTING_TOKEN")

        customClient
            .post()
            .uri("/appUser")
            .bodyValue(requestBody)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody<AppUserResponseDto>()
            .consumeWith { result ->
                val appUserResponseDto: AppUserResponseDto = result.responseBody!!

                assertThat(appUserResponseDto).isEqualTo(expectedAppUserResponseDto)
            }
    }
}
