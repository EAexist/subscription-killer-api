package com.matchalab.subscription_killer_api.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.matchalab.subscription_killer_api.core.dto.AppUserResponseDto
import com.matchalab.subscription_killer_api.core.dto.GoogleAccountResponseDto
import com.matchalab.subscription_killer_api.core.dto.LoginRequestDto
import com.matchalab.subscription_killer_api.service.AuthService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
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

// @WebMvcTest(AuthController::class, excludeAutoConfiguration = [SecurityAutoConfiguration::class])
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
    private val fakeGoogleAccountResponseDto = GoogleAccountResponseDto(fakeSubject, fakeName)

    private val expectedAppUserResponseDto: AppUserResponseDto =
            AppUserResponseDto(fakeName, listOf(fakeGoogleAccountResponseDto))

    // @BeforeEach
    // fun setUp() {
    //     `when`(authService.loginOrRegister("FAKE_VALID_FIRST_SEEN_TOKEN"))
    //             .thenReturn(
    //                     LoginOrRegisterResult.Registered(
    //                             AppUserResponseDto(fakeName,
    // listOf(fakeGoogleAccountResponseDto))
    //                     )
    //             )
    // }

    // @Test
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

    // @Test
    @WithMockUser
    fun `should return AppUserDTO and HttpStatus provided by AuthService when requested login`() {
        val requestBody: LoginRequestDto = LoginRequestDto(idToken = "FAKE_VALID_FIRST_SEEN_TOKEN")
        val jsonBody = objectMapper.writeValueAsString(requestBody)
        logger.debug { "jsonBody: ${jsonBody}" }
        val debugDto = objectMapper.readValue(jsonBody, LoginRequestDto::class.java)
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

        val actualAppUserResponseDto: AppUserResponseDto =
                objectMapper.readValue(responseBodyString, AppUserResponseDto::class.java)

        assertThat(actualAppUserResponseDto).isEqualTo(expectedAppUserResponseDto)
    }
}
