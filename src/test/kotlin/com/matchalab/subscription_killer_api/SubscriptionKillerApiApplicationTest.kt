package com.matchalab.subscription_killer_api

import com.matchalab.subscription_killer_api.config.SharedTestcontainersConfig
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(SharedTestcontainersConfig::class)
class SubscriptionKillerApiApplicationTests {

    @Test
    fun contextLoads() {
    }

}
