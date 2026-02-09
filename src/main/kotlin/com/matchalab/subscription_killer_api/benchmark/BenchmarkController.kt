package com.matchalab.subscription_killer_api.benchmark

import com.matchalab.subscription_killer_api.repository.EmailSourceRepository
import com.matchalab.subscription_killer_api.service.AppUserService
import com.matchalab.subscription_killer_api.subscription.service.SubscriptionAnalysisService
import io.micrometer.observation.ObservationRegistry
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/benchmark")
@Profile("benchmark")
class BenchmarkController(
    private val subscriptionAnalysisService: SubscriptionAnalysisService,
    private val appUserService: AppUserService,
    private val emailSourceRepository: EmailSourceRepository,
    private val benchmarkGoogleAccountListProperties: BenchmarkGoogleAccountListProperties,
    private val observationRegistry: ObservationRegistry
) {

    @PostMapping("/analyze")
    @Transactional
    suspend fun analyze(): ResponseEntity<Unit> {

        val benchmarkAppUser =
            appUserService.findByGoogleAccounts_Subject(benchmarkGoogleAccountListProperties.samples.first().subject)!!

        subscriptionAnalysisService.analyze(benchmarkAppUser.id!!)

        emailSourceRepository.clearAllEventRules()
        emailSourceRepository.clearAllAnalyzedMessageIds()

        return ResponseEntity.ok().build()
    }
}
