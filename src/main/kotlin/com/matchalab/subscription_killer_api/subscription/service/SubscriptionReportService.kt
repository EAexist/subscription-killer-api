package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.service.AppUserService
import com.matchalab.subscription_killer_api.subscription.dto.AccountReportDto
import com.matchalab.subscription_killer_api.subscription.dto.SubscriptionReportResponseDto
import com.matchalab.subscription_killer_api.utils.toReportDto
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import java.util.*


private val logger = KotlinLogging.logger {}

@Service
class SubscriptionReportService(
    private val appUserService: AppUserService,
) {

    fun getReport(appUserId: UUID): SubscriptionReportResponseDto? {

        val googleAccounts =
            appUserService.findByIdWithAccounts(appUserId)?.googleAccounts
                ?: throw EntityNotFoundException("🚨 User not found with id $appUserId")
        val hasAnalyzedSubscription = googleAccounts.any {
            it.analyzedAt != null
        } ?: false

        if (!hasAnalyzedSubscription) {
            return null
        }

        val accountReports: List<AccountReportDto> = googleAccounts.map {
            it.toReportDto()
        }

        return SubscriptionReportResponseDto(accountReports)
    }
}
