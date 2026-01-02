package com.matchalab.subscription_killer_api.subscription.progress.dto

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.matchalab.subscription_killer_api.subscription.dto.ServiceProviderResponseDto
import com.matchalab.subscription_killer_api.subscription.progress.AnalysisProgressStatus
import com.matchalab.subscription_killer_api.subscription.progress.ServiceProviderAnalysisProgressStatus

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type" // The JSON field used to distinguish types
)
@JsonSubTypes(
    JsonSubTypes.Type(value = AppUserAnalysisProgressUpdate::class, name = "appUser"),
    JsonSubTypes.Type(value = ServiceProviderAnalysisProgressUpdate::class, name = "serviceProvider"),
)
sealed interface AnalysisProgressUpdate {
}

data class AppUserAnalysisProgressUpdate(
    val status: AnalysisProgressStatus
) : AnalysisProgressUpdate

data class ServiceProviderAnalysisProgressUpdate(
    val serviceProvider: ServiceProviderResponseDto,
    val status: ServiceProviderAnalysisProgressStatus
) : AnalysisProgressUpdate