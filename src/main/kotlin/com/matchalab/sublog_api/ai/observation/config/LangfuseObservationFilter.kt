package com.matchalab.sublog_api.ai.observation.config

import io.micrometer.common.KeyValue
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationFilter
import org.springframework.ai.chat.observation.ChatModelObservationContext
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Profile("benchmark")
@Order(2)
@Component
class LangfuseObservationFilter : ObservationFilter {

    override fun map(context: Observation.Context): Observation.Context {

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
