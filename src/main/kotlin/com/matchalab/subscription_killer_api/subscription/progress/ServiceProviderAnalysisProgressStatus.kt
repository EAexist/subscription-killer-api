package com.matchalab.subscription_killer_api.subscription.progress

enum class ServiceProviderAnalysisProgressStatus(val sortOrder: Int) {
    STARTED(10),
    COMPLETED(20);

    companion object {
        private val map = entries.associateBy(ServiceProviderAnalysisProgressStatus::sortOrder)
        fun fromSortOrder(order: Int): ServiceProviderAnalysisProgressStatus? = map[order]
    }
}

