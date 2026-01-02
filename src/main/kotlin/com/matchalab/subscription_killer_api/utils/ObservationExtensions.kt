package com.matchalab.subscription_killer_api.utils

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope

suspend inline fun <T> ObservationRegistry.withParent(
    crossinline block: suspend CoroutineScope.(Observation?) -> T
): T {
    val parent = this.currentObservation
    return coroutineScope {
        block(parent)
    }
}

suspend inline fun <T> ObservationRegistry.observeSuspend(
    name: String,
    parent: Observation? = null,
    vararg tags: Pair<String, String>,
    crossinline block: suspend () -> T
): T {
    val observation = Observation.createNotStarted(name, this)
    parent?.let { observation.parentObservation(it) }
    tags.forEach { observation.lowCardinalityKeyValue(it.first, it.second) }

    observation.start()
    return try {
        block()
    } catch (e: Throwable) {
        observation.error(e)
        throw e
    } finally {
        observation.stop()
    }
}

inline fun <T> ObservationRegistry.observe(
    name: String,
    parent: Observation? = null,
    vararg tags: Pair<String, String>,
    block: () -> T
): T {
    val obs = Observation.createNotStarted(name, this)
    parent?.let { obs.parentObservation(it) }
    tags.forEach { obs.lowCardinalityKeyValue(it.first, it.second) }

    obs.start()
    return try {
        obs.openScope().use { block() }
    } finally {
        obs.stop()
    }
}