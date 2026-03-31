package com.matchalab.subscription_killer_api.ai.observation.config

import io.micrometer.common.KeyValue
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationFilter
import org.springframework.boot.info.GitProperties
import org.springframework.stereotype.Component

@Component
class VersionObservationFilter(
    private val gitProperties: GitProperties
) : ObservationFilter {
    override fun map(context: Observation.Context): Observation.Context {
        val latestTag = gitProperties.get("tags")?.split(',')?.firstOrNull() ?: "unknown"
        return context.addLowCardinalityKeyValue(KeyValue.of("langfuse.version", latestTag))
    }
}