package app.manyak.legal.consent.data.repository

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.error.map
import app.manyak.legal.consent.data.api.ConsentApi
import app.manyak.legal.consent.data.api.dto.ConsentStatusDto
import app.manyak.legal.consent.data.api.dto.UserConsentRequestDto
import app.manyak.legal.consent.data.api.dto.UserConsentResponseDto
import app.manyak.legal.consent.domain.ConsentRepository
import app.manyak.legal.consent.entity.ConsentItem
import app.manyak.legal.consent.entity.ConsentStatus
import app.manyak.legal.consent.entity.RequiredConsent
import app.manyak.network.data.api.apiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConsentRepositoryImpl
    @Inject
    constructor(
        private val api: ConsentApi,
    ) : ConsentRepository {
        override suspend fun get(): DomainResult<ConsentStatus> = apiCall { api.get() }.map { it.toEntity() }

        override suspend fun record(versions: Map<ConsentItem, String>): DomainResult<ConsentStatus> =
            apiCall {
                api.record(
                    UserConsentRequestDto(
                        terms = versions[ConsentItem.TERMS],
                        privacy = versions[ConsentItem.PRIVACY],
                        age14 = versions[ConsentItem.AGE14],
                    ),
                )
            }.map { it.toEntity() }
    }

private fun UserConsentResponseDto.toEntity(): ConsentStatus =
    ConsentStatus(
        required =
            listOfNotNull(
                age14.requiredOrNull(ConsentItem.AGE14),
                terms.requiredOrNull(ConsentItem.TERMS),
                privacy.requiredOrNull(ConsentItem.PRIVACY),
            ),
    )

private fun ConsentStatusDto?.requiredOrNull(item: ConsentItem): RequiredConsent? {
    val version = this?.requiredVersion ?: return null
    return if (needsConsent == true) RequiredConsent(item, version) else null
}
