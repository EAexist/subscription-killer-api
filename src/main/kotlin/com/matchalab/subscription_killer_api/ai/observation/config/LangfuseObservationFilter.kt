package com.matchalab.subscription_killer_api.ai.observation.config

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationFilter
import io.micrometer.common.KeyValue
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.ai.chat.observation.ChatModelObservationContext
import org.springframework.http.server.observation.ServerRequestObservationContext

@Profile("benchmark")
@Order(2)
@Component
class LangfuseObservationFilter : ObservationFilter {

    override fun map(context: Observation.Context): Observation.Context {

        if (context is ServerRequestObservationContext) {
            val k6Index = context.carrier.getHeader("X-K6-Index")
            if (k6Index != null) {
                // Use 'tags' to make it a first-class filter in Langfuse UI
                context.addHighCardinalityKeyValue(KeyValue.of("langfuse.trace.tags", "request_$k6Index"))
                context.addHighCardinalityKeyValue(KeyValue.of("langfuse.trace.metadata.k6_index", k6Index))
            }
        }

        if (context is ChatModelObservationContext) {
            moveLowCardinalityTag(context, "app.ai.task_name", "langfuse.observation.metadata.task_name")
            moveHighCardinalityTag(context, "app.ai.data_count", "langfuse.observation.metadata.data_count")
            listOf(
                "app.ai.task_name",
                "app.ai.data_count",
            ).forEach { removeKey(context, it) }
        }

        return context
    }


    private fun moveHighCardinalityTag(context: Observation.Context, oldKey: String, newKey: String) {
        getValue(context, oldKey)?.let {
            context.addHighCardinalityKeyValue(KeyValue.of(newKey, it))
        }
    }

    private fun moveLowCardinalityTag(context: Observation.Context, oldKey: String, newKey: String) {
        getValue(context, oldKey)?.let {
            context.addLowCardinalityKeyValue(KeyValue.of(newKey, it))
        }
    }

    private fun getValue(context: Observation.Context, key: String): String? {
        return context.allKeyValues.find { it.key == key }?.value
    }

    private fun removeKey(context: Observation.Context, key: String) {
        // Remove key from both high and low cardinality key collections
        context.removeHighCardinalityKeyValue(key)
        context.removeLowCardinalityKeyValue(key)
    }
}
