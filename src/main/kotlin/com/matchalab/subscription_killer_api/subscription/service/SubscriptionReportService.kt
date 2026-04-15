package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.service.AppUserService
import com.matchalab.subscription_killer_api.service.GoogleAccountService
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
    private val googleAccountService: GoogleAccountService,
    private val appProperties: AppProperties,
) {

    fun getUpdateEligibility(appUserId: UUID): ReportUpdateEligibilityDto {

        val lastEmailSyncedAt: Instant = appUserService.findLastEmailSyncedAtByUserId(appUserId)
            ?: return ReportUpdateEligibilityDto(true)

        val availableSince: Instant =
            lastEmailSyncedAt.plus(appProperties.minRequestIntervalSeconds, ChronoUnit.SECONDS)
        val canUpdate: Boolean = availableSince.isBefore(Instant.now())

        return ReportUpdateEligibilityDto(canUpdate, lastEmailSyncedAt, availableSince)

    }

    fun getReport(appUserId: UUID): SubscriptionReportResponseDto? {

        val hasAnalyzedSubscription =
            googleAccountService.existsAnalyzedSubscriptionByAppUserId(appUserId)

        if (!hasAnalyzedSubscription) {
            return null
        }

        val googleAccounts =
            appUserService.findGoogleAccountsWithFullSubscriptions(appUserId)
        val accountReports: List<AccountReportDto> = googleAccounts.map {
            it.toReportDto()
        }
        val analyzedAt: Instant = googleAccounts.mapNotNull { it.analyzedAt }.min()

        return SubscriptionReportResponseDto(accountReports, analyzedAt)
    }

}
