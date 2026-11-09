package app.manyak.notification.settings.data.repository

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.error.map
import app.manyak.network.data.api.apiCall
import app.manyak.notification.settings.data.api.PushSettingsApi
import app.manyak.notification.settings.data.api.dto.PushSettingsDto
import app.manyak.notification.settings.domain.PushSettingsRepository
import app.manyak.notification.settings.entity.PushSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushSettingsRepositoryImpl
    @Inject
    constructor(
        private val api: PushSettingsApi,
    ) : PushSettingsRepository {
        override suspend fun get(): DomainResult<PushSettings> = apiCall { api.get() }.map { it.toEntity() }

        override suspend fun update(settings: PushSettings): DomainResult<PushSettings> =
            apiCall { api.update(settings.toDto()) }.map { it.toEntity() }
    }

private fun PushSettingsDto.toEntity() =
    PushSettings(
        servicePush = servicePush,
        marketingPush = marketingPush,
        marketingNightPush = marketingNightPush,
    )

private fun PushSettings.toDto() =
    PushSettingsDto(
        servicePush = servicePush,
        marketingPush = marketingPush,
        marketingNightPush = marketingNightPush,
    )
