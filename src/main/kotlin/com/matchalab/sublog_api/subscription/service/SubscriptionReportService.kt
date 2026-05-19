package com.matchalab.sublog_api.subscription.service

import com.matchalab.sublog_api.service.AppUserService
import com.matchalab.sublog_api.subscription.dto.AccountReportDto
import com.matchalab.sublog_api.subscription.dto.SubscriptionReportResponseDto
import com.matchalab.sublog_api.utils.toResponseDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*


private val logger = KotlinLogging.logger {}

@Service
class SubscriptionReportService(
    private val appUserService: AppUserService,
    private val subscriptionService: SubscriptionService,
) {

    @Transactional(readOnly = true)
    fun getReport(appUserId: UUID): SubscriptionReportResponseDto? {

        val reportUpdateEligibility = appUserService.getReportUpdateEligibility(appUserId)

        logger.debug { "reportUpdateEligibility: ${reportUpdateEligibility.toString()}" }

        if (reportUpdateEligibility.reportUpdatedAt == null) {
            return null
        }

        val reportUpdateAvailableSince = reportUpdateEligibility.availableSince ?: return null

        val googleAccounts =
            appUserService.findGoogleAccountsWithFullSubscriptions(appUserId)

        val accountReports: List<AccountReportDto> = googleAccounts.map {

            val subscriptionDtos =
                subscriptionService.getResponseDtos(it.subscriptions.map { s -> s.id!! })

            AccountReportDto(
                subscriptionDtos,
                it.toResponseDto()
            )
        }

        return SubscriptionReportResponseDto(accountReports, reportUpdateAvailableSince)
    }

}
