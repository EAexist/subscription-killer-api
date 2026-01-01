package com.matchalab.subscription_killer_api.subscription.analysisStep

data class AnalysisProgressStatusDto(
    val type: AnalysisStatusType,
    val payload: Any? = null
) {}
