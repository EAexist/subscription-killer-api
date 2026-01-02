package com.matchalab.subscription_killer_api.config

import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.aop.ObservedAspect
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

private val logger = KotlinLogging.logger {}

@Configuration
class ObservationConfig {

    private val maxArgumentStringLength = 30

    @Bean
    fun observedAspect(observationRegistry: ObservationRegistry): ObservedAspect {
        return ObservedAspect(observationRegistry)
    }

//    @Bean
//    fun loggingObservationHandler(): ObservationHandler<Observation.Context> {
//        return object : ObservationHandler<Observation.Context> {
//            override fun onStart(context: Observation.Context) {
//                val detail = getContextDetail(context)
//                context.put("startTime", System.nanoTime())
//                logger.debug { "🚀 Starting [${detail}]" }
//            }
//
//            override fun onStop(context: Observation.Context) {
//                val startTime = context.get<Long>("startTime") ?: 0L
//                val durationMs = (System.nanoTime() - startTime) / 1_000_000
//
//                val detail = getContextDetail(context)
//                logger.info { "⏱️ [${detail}] took %.3fs".format(durationMs / 1000.0) }
//            }
//
//            override fun supportsContext(context: Observation.Context): Boolean = true
//        }
//    }
//
//    @Bean
//    fun skipSecurityObservations(): ObservationPredicate {
//        return ObservationPredicate { name, _ ->
//            // Return false to ignore/disable the observation
//            !name.startsWith("spring.security")
//        }
//    }

    private fun getContextDetail(context: Observation.Context): String {
        val detail = if (context is ObservedAspect.ObservedAspectContext) {
            val joinPoint = context.proceedingJoinPoint
            val methodName = joinPoint.signature.name

            val formattedArgs = joinPoint.args.joinToString(", ") { arg ->
                val str = arg?.toString() ?: "null"
                if (str.length > maxArgumentStringLength) "${str.take(maxArgumentStringLength)}..." else str
            }

            "$methodName($formattedArgs)"
        } else {
            context.name // Fallback for manual observations
        }
        return detail
    }
}