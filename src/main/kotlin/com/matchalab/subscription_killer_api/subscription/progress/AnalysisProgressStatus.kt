package com.matchalab.subscription_killer_api.subscription.progress

enum class AnalysisProgressStatus(val sortOrder: Int) {
    STARTED(10),
    EMAIL_FETCHED(20),
    EMAIL_ACCOUNT_ANALYSIS_COMPLETED(30),
    COMPLETED(50);

    companion object {
        private val map = entries.associateBy(AnalysisProgressStatus::sortOrder)
        fun fromSortOrder(order: Int): AnalysisProgressStatus? = map[order]
    }
}

