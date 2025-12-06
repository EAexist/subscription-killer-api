package com.matchalab.subscription_killer_api.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.matchalab.subscription_killer_api.core.dto.AppUserResponseDTO
import com.matchalab.subscription_killer_api.core.dto.GoogleAccountResponseDTO
import com.matchalab.subscription_killer_api.core.dto.LoginRequestDTO
import com.matchalab.subscription_killer_api.domain.AuthResult
import com.matchalab.subscription_killer_api.service.AuthService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter

private val logger = KotlinLogging.logger {}

@WebMvcTest(AuthController::class, excludeAutoConfiguration = [SecurityAutoConfiguration::class])
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @MockitoBean private lateinit var authService: AuthService
    @MockitoBean private lateinit var authenticationManager: AuthenticationManager

    @Autowired lateinit var wac: WebApplicationContext

    private val fakeSubject = "fakeSubject"
    private val fakeEmail = "test@mock.com"
    private val fakeName = "fakeName"
    private val fakeGoogleAccountResponseDTO = GoogleAccountResponseDTO(fakeSubject, fakeName)

    private val expectedAppUserResponseDTO: AppUserResponseDTO =
            AppUserResponseDTO(fakeName, listOf(fakeGoogleAccountResponseDTO))

    @BeforeEach
    fun setUp() {
        `when`(authService.loginOrRegister("FAKE_VALID_FIRST_SEEN_TOKEN"))
                .thenReturn(
                        AuthResult.Registered(
                                AppUserResponseDTO(fakeName, listOf(fakeGoogleAccountResponseDTO))
                        )
                )
    }

    @Test
    fun `should load Jackson in test context`() {
        logger.debug { "mockMvc: $mockMvc" }
        logger.debug { "Jackson modules: " + objectMapper.registeredModuleIds }
        logger.debug { "ObjectMapper class: ${objectMapper::class.qualifiedName}" }

        val adapter =
                wac.getBean(
                        "requestMappingHandlerAdapter",
                        RequestMappingHandlerAdapter::class.java
                )
        adapter.messageConverters.forEach { converter ->
            logger.debug { "Converter: ${converter::class.qualifiedName}" }
            if (converter is MappingJackson2HttpMessageConverter) {
                logger.debug { "  ObjectMapper: ${converter.objectMapper}" }
                logger.debug { "  Kotlin modules: ${converter.objectMapper.registeredModuleIds}" }
            }
        }
    }

    @Test
    @WithMockUser
    fun `should return AppUserDTO and HttpStatus provided by AuthService when requested login`() {
        val requestBody: LoginRequestDTO = LoginRequestDTO(idToken = "FAKE_VALID_FIRST_SEEN_TOKEN")
        val jsonBody = objectMapper.writeValueAsString(requestBody)
        logger.debug { "jsonBody: ${jsonBody}" }
        val debugDto = objectMapper.readValue(jsonBody, LoginRequestDTO::class.java)
        logger.debug { "debugDto: ${debugDto}" }

        val result =
                mockMvc.perform(
                                post("/api/v1/auth")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .accept(MediaType.APPLICATION_JSON)
                                        .content(jsonBody)
                        )
                        .andDo(MockMvcResultHandlers.print())
                        .andExpect(status().isCreated)
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andReturn()

        val responseBodyString: String = result.response.contentAsString

        val actualAppUserResponseDTO: AppUserResponseDTO =
                objectMapper.readValue(responseBodyString, AppUserResponseDTO::class.java)

        assertThat(actualAppUserResponseDTO).isEqualTo(expectedAppUserResponseDTO)
    }
}
