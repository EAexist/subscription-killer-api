package com.matchalab.subscription_killer_api.subscription.controller

import com.matchalab.subscription_killer_api.config.AuthenticatedUser
import com.matchalab.subscription_killer_api.service.AppUserService
import com.matchalab.subscription_killer_api.subscription.dto.ReportUpdateEligibilityDto
import com.matchalab.subscription_killer_api.subscription.dto.SubscriptionReportResponseDto
import com.matchalab.subscription_killer_api.subscription.progress.service.ProgressService
import com.matchalab.subscription_killer_api.subscription.service.SubscriptionAnalysisService
import com.matchalab.subscription_killer_api.subscription.service.SubscriptionReportService
import com.matchalab.subscription_killer_api.utils.observeSuspend
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
import java.time.Duration
import java.time.Instant
import java.util.*

private val logger = KotlinLogging.logger {}

@ConfigurationProperties(prefix = "app")
data class AppProperties(val minRequestIntervalHours: Long)

@RestController
@RequestMapping("/api/v1/reports")
class SubscriptionReportController(
    private val analysisService: SubscriptionAnalysisService,
    private val appUserService: AppUserService,
    private val progressService: ProgressService,
    private val reportService: SubscriptionReportService,
    private val observationRegistry: ObservationRegistry,
    private val appProperties: AppProperties,
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

    @GetMapping("/updates/eligibility")
    fun getUpdateEligibility(@AuthenticatedUser appUserId: UUID): ReportUpdateEligibilityDto {
        return reportService.getUpdateEligibility(appUserId)
    }

    @PostMapping("/updates")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun analyze(@AuthenticatedUser appUserId: UUID): ResponseEntity<Any> {

        val reportUpdateEligibility: ReportUpdateEligibilityDto = reportService.getUpdateEligibility(appUserId)

        if (!reportUpdateEligibility.canUpdate) {

            val secondsUntilNextAllowed: Long = Duration.between(
                Instant.now(),
                reportUpdateEligibility.availableSince
            ).toSeconds()

            val problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS,
                "Minimum interval between analyses is ${appProperties.minRequestIntervalHours} hours."
            ).apply {
                title = "Too Frequent Requests"
            }

            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", secondsUntilNextAllowed.toString())
                .body(problem)
        }

        progressService.initializeProgress(appUserId)

        val parent = observationRegistry.currentObservation

        CoroutineScope(dispatcher + observationRegistry.asContextElement()).launch {
            observationRegistry.observeSuspend(
                "analysis.task",
                parent,
                "user.id" to appUserId.toString()
            ) {
                analysisService.analyze(appUserId)
            }
        }
        return ResponseEntity.accepted().build()
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
