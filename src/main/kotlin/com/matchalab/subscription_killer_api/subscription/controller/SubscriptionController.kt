package com.matchalab.subscription_killer_api.controller

import com.matchalab.subscription_killer_api.config.AuthenticatedUser
import com.matchalab.subscription_killer_api.service.AppUserService
import com.matchalab.subscription_killer_api.subscription.dto.ReportUpdateEligibilityDto
import com.matchalab.subscription_killer_api.subscription.dto.SubscriptionReportResponseDto
import com.matchalab.subscription_killer_api.subscription.progress.service.ProgressService
import com.matchalab.subscription_killer_api.subscription.service.SubscriptionAnalysisService
import com.matchalab.subscription_killer_api.subscription.service.SubscriptionReportService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.kotlin.asContextElement
import io.micrometer.observation.ObservationRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Duration
import java.time.Instant
import java.util.*

private val logger = KotlinLogging.logger {}

@ConfigurationProperties(prefix = "app")
data class AppProperties(val minRequestIntervalSeconds: Long)

@RestController
@RequestMapping("/api/v1/reports")
class SubscriptionController(
    private val analysisService: SubscriptionAnalysisService,
    private val appUserService: AppUserService,
    private val progressService: ProgressService,
    private val reportService: SubscriptionReportService,
    private val observationRegistry: ObservationRegistry,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    @GetMapping
    fun getReport(@AuthenticatedUser appUserId: UUID): ResponseEntity<SubscriptionReportResponseDto> {
        val report = reportService.getReport(appUserId)
        logger.debug { report }
        return report?.let {
            ResponseEntity.ok(it)
        } ?: ResponseEntity.noContent().build()
    }

    @PostMapping("/updates")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun analyze(@AuthenticatedUser appUserId: UUID): ResponseEntity<Any> {

        val newReportGeneratedAt = appUserService.claimReportQuota(appUserId)

        if (newReportGeneratedAt == null) {
            val reportUpdateEligibility: ReportUpdateEligibilityDto =
                appUserService.getReportUpdateEligibility(appUserId)

            val secondsUntilNextAllowed: Long = Duration.between(
                Instant.now(),
                reportUpdateEligibility.availableSince
            ).toSeconds()

            val problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS,
                "Retry after ${secondsUntilNextAllowed.toString()} seconds."
            ).apply {
                title = "Too Frequent Requests"
            }

            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", secondsUntilNextAllowed.toString())
                .body(problem)
        }

        progressService.initializeProgress(appUserId)

        CoroutineScope(dispatcher + observationRegistry.asContextElement()).launch {
            analysisService.analyze(appUserId)
        }
        return ResponseEntity.accepted().build()
    }

    @PostMapping("/updates/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun analyzeStream(@AuthenticatedUser appUserId: UUID): SseEmitter {

        val newReportGeneratedAt = appUserService.claimReportQuota(appUserId)

        if (newReportGeneratedAt != null) {

            progressService.initializeProgress(appUserId)

            CoroutineScope(dispatcher + observationRegistry.asContextElement()).launch {
                try {
                    analysisService.analyze(appUserId)
                } catch (e: Exception) {
                    progressService.error(appUserId)
                }
            }
        }

        return progressService.createEmitter(appUserId)
    }

    @GetMapping("/updates")
    fun subscribeProgress(
        @AuthenticatedUser appUserId: UUID,
        @RequestHeader("Accept") acceptHeader: String
    ): ResponseEntity<*> {
        if (!acceptHeader.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON).build<Void>()
        }
        return ResponseEntity.ok(progressService.createEmitter(appUserId))
    }
}
