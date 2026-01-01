package com.matchalab.subscription_killer_api.subscription.controller

import com.matchalab.subscription_killer_api.config.AuthenticatedUser
import com.matchalab.subscription_killer_api.subscription.dto.SubscriptionAnalysisResponseDto
import com.matchalab.subscription_killer_api.subscription.dto.SubscriptionReportResponseDto
import com.matchalab.subscription_killer_api.subscription.service.ProgressService
import com.matchalab.subscription_killer_api.subscription.service.SubscriptionAnalysisService
import com.matchalab.subscription_killer_api.subscription.service.SubscriptionReportService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.*


private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/reports")
class SubscriptionReportController(
    private val analysisService: SubscriptionAnalysisService,
    private val progressService: ProgressService,
    private val reportService: SubscriptionReportService
) {

    @GetMapping
    fun getReport(@AuthenticatedUser appUserId: UUID): ResponseEntity<SubscriptionReportResponseDto> {
        val report = reportService.getReport(appUserId)
        logger.debug { report }
        return report?.let {
            ResponseEntity.ok(it)
        } ?: ResponseEntity.noContent().build()
    }

    @PostMapping("/analysis")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun analyze(@AuthenticatedUser appUserId: UUID): SubscriptionAnalysisResponseDto {
        CoroutineScope(Dispatchers.IO).launch {
            analysisService.analyze(appUserId)
        }
        progressService.initializeProgress(appUserId)
        return SubscriptionAnalysisResponseDto()
    }

    @GetMapping("/analysis/progress", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun subscribeProgress(@AuthenticatedUser appUserId: UUID): SseEmitter {
        return progressService.createEmitter(appUserId)
    }
}
