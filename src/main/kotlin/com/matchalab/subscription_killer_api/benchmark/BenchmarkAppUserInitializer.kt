package com.matchalab.subscription_killer_api.benchmark

import com.matchalab.subscription_killer_api.domain.AppUser
import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.service.AppUserService
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
@Profile("benchmark")
@EnableConfigurationProperties(BenchmarkGoogleAccountListProperties::class)
class BenchmarkAppUserInitializer(
    private val appUserService: AppUserService,
    private val benchmarkGoogleAccountListProperties: BenchmarkGoogleAccountListProperties,
) : CommandLineRunner {

    val benchmarkAppUserName: String = "BENCHMARK_SAMPLE_APP_USER_NAME"

    @Transactional
    override fun run(vararg args: String?) {

        if (!appUserService.existsByName(benchmarkAppUserName)) {
            val benchmarkUser = AppUser(
                name = benchmarkAppUserName
            )

            benchmarkGoogleAccountListProperties.samples.forEach {
                benchmarkUser.addGoogleAccount(
                    GoogleAccount(
                        it.subject,
                        benchmarkAppUserName,
                        it.email,
                        it.refreshToken,
                        it.accessToken,
                        it.expiresAt,
                        it.scope
                    )
                )
            }

            appUserService.save(benchmarkUser)
            println("✅ Benchmark user initialized with Real API access.")
        }
    }
}