package com.matchalab.subscription_killer_api.config

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
        return context.addLowCardinalityKeyValue(KeyValue.of("app.version", gitProperties.get("build.version")))
            .addHighCardinalityKeyValue(KeyValue.of("git.commit", gitProperties.shortCommitId))
    }
}