package com.matchalab.subscription_killer_api.subscription.progress.service

import com.matchalab.subscription_killer_api.service.AppUserService
import com.matchalab.subscription_killer_api.subscription.dto.ServiceProviderResponseDto
import com.matchalab.subscription_killer_api.subscription.progress.AnalysisProgressStatus
import com.matchalab.subscription_killer_api.subscription.progress.ServiceProviderAnalysisProgressStatus
import com.matchalab.subscription_killer_api.subscription.progress.dto.AppUserAnalysisProgressUpdate
import com.matchalab.subscription_killer_api.subscription.progress.dto.ServiceProviderAnalysisProgressUpdate
import com.matchalab.subscription_killer_api.subscription.service.ServiceProviderService
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.*
import java.util.concurrent.ConcurrentHashMap


private val logger = KotlinLogging.logger {}

@Service
class ProgressService(
    private val appUserService: AppUserService,
    private val serviceProviderService: ServiceProviderService,
) {
    data class AnalysisProgress(
        var status: AnalysisProgressStatus = AnalysisProgressStatus.STARTED,
        val serviceProviders: ConcurrentHashMap<UUID, ServiceProviderAnalysisProgressStatus> = ConcurrentHashMap()
    ) {}

    private val emitters = ConcurrentHashMap<UUID, SseEmitter>()
    private val progresses = ConcurrentHashMap<UUID, ConcurrentHashMap<String, AnalysisProgress>>()

    fun isOnProgress(appUserId: UUID): Boolean {
        return progresses.containsKey(appUserId)
    }

    fun createEmitter(appUserId: UUID): SseEmitter {
        val emitter = SseEmitter(30 * 60 * 1000L).apply {
            emitters[appUserId] = this

            val cleanup = {
                emitters.remove(appUserId)
            }
            onCompletion { cleanup() }
            onTimeout { cleanup() }
            onError { cleanup() }
        }

        progresses[appUserId]?.values?.let { progresses ->
            try {
                val update: AppUserAnalysisProgressUpdate =
                    progresses.map { it.status }.minBy { it.sortOrder }.let { AppUserAnalysisProgressUpdate(it) }

                val serviceProviderIds = progresses.flatMap { it.serviceProviders.keys }

                val responseDtos =
                    serviceProviderService.findAllDtoById(serviceProviderIds).associateBy { it.id }

                val serviceProviderUpdates: List<ServiceProviderAnalysisProgressUpdate> =
                    progresses.flatMap { progress ->
                        progress.serviceProviders.map {
                            ServiceProviderAnalysisProgressUpdate(
                                responseDtos[it.key]!!,
                                it.value
                            )
                        }
                    }

                emitter.send(
                    SseEmitter.event().name("progress-update").data(update)
                )
                serviceProviderUpdates.forEach {
                    emitter.send(
                        SseEmitter.event().name("progress-update").data(it)
                    )
                }
            } catch (e: Exception) {
                emitter.completeWithError(e)
            }
        }

        return emitter
    }

    fun setProgress(appUserId: UUID, googleAccountSubject: String, status: AnalysisProgressStatus) {

        progresses[appUserId]?.get(googleAccountSubject)?.status = status

        val totalStatus: AnalysisProgressStatus? =
            if (status === AnalysisProgressStatus.COMPLETED) AnalysisProgressStatus.COMPLETED else progresses[appUserId]?.values?.minBy { it.status.sortOrder }?.status
        val update: AppUserAnalysisProgressUpdate? = totalStatus?.let { AppUserAnalysisProgressUpdate(it) }

        update?.let { update ->
            emitters[appUserId]?.let { emitter ->
                try {
                    emitter.send(
                        SseEmitter.event().name("progress-update")
                            .data(update)
                    )
                    if (totalStatus === AnalysisProgressStatus.COMPLETED) {
                        emitter.complete()
                    }
                } catch (e: Exception) {
                    emitter.completeWithError(e)
                }
            }
            if (totalStatus === AnalysisProgressStatus.COMPLETED) {
                progresses.remove(appUserId)
            }
        }
    }

    fun setServiceProviderProgress(
        appUserId: UUID,
        googleAccountSubject: String,
        serviceProviderId: UUID,
        status: ServiceProviderAnalysisProgressStatus
    ) {

        progresses[appUserId]?.get(googleAccountSubject)?.serviceProviders?.set(serviceProviderId, status)

        val serviceProviderResponseDto: ServiceProviderResponseDto =
            serviceProviderService.findDtoById(serviceProviderId) ?: throw EntityNotFoundException()

        val update = ServiceProviderAnalysisProgressUpdate(serviceProviderResponseDto, status)

        emitters[appUserId]?.let { emitter ->
            try {
                emitter.send(
                    SseEmitter.event().name("progress-update")
                        .data(update)
                )
            } catch (e: Exception) {
                emitter.completeWithError(e)
            }
        }
    }

    fun initializeProgress(appUserId: UUID) {
        val appUser = appUserService.findByIdOrNotFound(appUserId)

        progresses[appUserId] =
            ConcurrentHashMap(appUser.googleAccounts.map { it.subject }
                .associateWith { AnalysisProgress() })
    }
}
