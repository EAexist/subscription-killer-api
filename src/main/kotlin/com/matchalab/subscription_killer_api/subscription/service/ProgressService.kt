package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.service.AppUserService
import com.matchalab.subscription_killer_api.subscription.analysisStep.AnalysisProgressStatusDto
import com.matchalab.subscription_killer_api.subscription.analysisStep.AnalysisStatusType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap


private val logger = KotlinLogging.logger {}

@Service
class ProgressService(
    private val appUserService: AppUserService,
) {
    private val emitters = ConcurrentHashMap<UUID, SseEmitter>()
    private val progresses = ConcurrentHashMap<UUID, ConcurrentHashMap<String, AnalysisProgressStatusDto>>()

    fun createEmitter(appUserId: UUID): SseEmitter {
        val emitter = SseEmitter(30 * 60 * 1000L).apply {
            emitters[appUserId] = this

            // Clean up
            onCompletion { emitters.remove(appUserId) }
            onTimeout { emitters.remove(appUserId) }
            onError { emitters.remove(appUserId) }
        }

        emitter.send(SseEmitter.event().name("subscribed"))

        progresses[appUserId]?.let { state ->
            try {
                emitter.send(SseEmitter.event().name("progress-update").data(state))
            } catch (e: Exception) {
                emitters.remove(appUserId)
            }
        }

        return emitter
    }

    fun setProgress(appUserId: UUID, googleAccountSubject: String, status: AnalysisProgressStatusDto) {

        progresses.computeIfAbsent(appUserId) {
            val appUser = appUserService.findByIdOrNotFound(appUserId)
            ConcurrentHashMap(appUser.googleAccounts.map { it.subject }
                .associateWith { AnalysisProgressStatusDto(AnalysisStatusType.STARTED) })
        }[googleAccountSubject] = status

        if (status.type === AnalysisStatusType.SERVICE_PROVIDER_ANALYSIS_COMPLETED) {
            sendProgress(appUserId, status)
        } else {
            progresses[appUserId]?.values?.asSequence()?.distinct()?.singleOrNull()?.let { sendProgress(appUserId, it) }
        }

    }

    fun initializeProgress(appUserId: UUID) {
        val appUser = appUserService.findByIdOrNotFound(appUserId)
        val appUserId = checkNotNull(appUser.id) { "User has null id" }

        progresses[appUserId] =
            ConcurrentHashMap(appUser.googleAccounts.map { it.subject }
                .associateWith { AnalysisProgressStatusDto(AnalysisStatusType.STARTED) })
    }

    private fun sendProgress(appUserId: UUID, payload: AnalysisProgressStatusDto) {

        emitters[appUserId]?.let { emitter ->
            try {
                emitter.send(SseEmitter.event().name("progress-update").data(payload))
            } catch (e: IOException) {
                emitters.remove(appUserId)
            }
        }
    }

    suspend fun initializeProgress(appUserId: UUID, googleAccountSubjects: List<String>) {
        val appUser = appUserService.findByIdOrNotFound(appUserId)
        val appUserId = checkNotNull(appUser.id) { "User has null id" }
        if (!progresses.keys.contains(appUserId)) {
            progresses[appUserId] =
                ConcurrentHashMap(googleAccountSubjects.associateWith { AnalysisProgressStatusDto(AnalysisStatusType.STARTED) })
        }
    }
}
