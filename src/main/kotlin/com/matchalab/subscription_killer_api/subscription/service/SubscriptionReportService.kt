package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.service.AppUserService
import com.matchalab.subscription_killer_api.subscription.controller.AppProperties
import com.matchalab.subscription_killer_api.subscription.dto.AccountReportDto
import com.matchalab.subscription_killer_api.subscription.dto.ReportUpdateEligibilityDto
import com.matchalab.subscription_killer_api.subscription.dto.SubscriptionReportResponseDto
import com.matchalab.subscription_killer_api.utils.toReportDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*


private val logger = KotlinLogging.logger {}

@Service
class SubscriptionReportService(
    private val appUserService: AppUserService,
    private val appProperties: AppProperties,
) {

    fun getUpdateEligibility(appUserId: UUID): ReportUpdateEligibilityDto {
        val googleAccounts =
            appUserService.findByIdOrNotFound(appUserId).googleAccounts

        val analyzedAt: Instant? = googleAccounts.map { it.analyzedAt }.minWithOrNull(compareBy { it })

        if (analyzedAt == null) {
            return ReportUpdateEligibilityDto(true)
        }

        val availableSince: Instant = analyzedAt.plus(appProperties.minRequestIntervalHours, ChronoUnit.HOURS)
        val canUpdate: Boolean = availableSince.isBefore(Instant.now())

        return ReportUpdateEligibilityDto(canUpdate, analyzedAt, availableSince)

    }

    fun getReport(appUserId: UUID): SubscriptionReportResponseDto? {

        val googleAccounts =
            appUserService.findByIdOrNotFound(appUserId).googleAccounts
        val hasAnalyzedSubscription = googleAccounts.any {
            it.analyzedAt != null
        } ?: false

        if (!hasAnalyzedSubscription) {
            return null
        }

        val accountReports: List<AccountReportDto> = googleAccounts.map {
            it.toReportDto()
        }
        val analyzedAt: Instant = googleAccounts.mapNotNull { it.analyzedAt }.min()

        return SubscriptionReportResponseDto(accountReports, analyzedAt)
    }
}
