package app.manyak.legal.consent.data.repository

import app.manyak.auth.domain.SessionGate
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.session.MemberConsent
import app.manyak.common.domain.session.UserScopedStore
import app.manyak.legal.consent.data.api.ConsentApi
import app.manyak.legal.consent.data.api.dto.ConsentStatusDto
import app.manyak.legal.consent.data.api.dto.UserConsentRequestDto
import app.manyak.legal.consent.data.api.dto.UserConsentResponseDto
import app.manyak.legal.consent.domain.ConsentRepository
import app.manyak.legal.consent.entity.ConsentItem
import app.manyak.legal.consent.entity.ConsentStatus
import app.manyak.legal.consent.entity.RequiredConsent
import app.manyak.network.data.api.apiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConsentRepositoryImpl
    @Inject
    constructor(
        private val api: ConsentApi,
        private val gate: SessionGate,
    ) : ConsentRepository,
        MemberConsent,
        UserScopedStore {
        override val storeName = "member_consent"

        private val satisfied = MutableStateFlow(false)
        override val isSatisfied = satisfied.asStateFlow()

        override suspend fun clearUserData(): Boolean {
            satisfied.value = false
            return true
        }

        override suspend fun get(): DomainResult<ConsentStatus> = request { api.get() }

        override suspend fun record(versions: Map<ConsentItem, String>): DomainResult<ConsentStatus> =
            request {
                api.record(
                    UserConsentRequestDto(
                        terms = versions[ConsentItem.TERMS],
                        privacy = versions[ConsentItem.PRIVACY],
                        age14 = versions[ConsentItem.AGE14],
                    ),
                )
            }

        private suspend fun request(call: suspend () -> Response<UserConsentResponseDto>): DomainResult<ConsentStatus> =
            gate.withAuthWork(onBlocked = { DomainResult.Failure(DomainError.Unauthorized) }) { work ->
                gate.commit(work) { satisfied.value = false }
                val result =
                    when (val response = apiCall(request = call)) {
                        is DomainResult.Failure -> response
                        is DomainResult.Success -> response.value.toResult()
                    }
                gate.commit(work) {
                    satisfied.value = result is DomainResult.Success && result.value.isSatisfied
                    result
                } ?: DomainResult.Failure(DomainError.Unauthorized)
            }
    }

private fun UserConsentResponseDto.toResult(): DomainResult<ConsentStatus> {
    // 누락되거나 해석할 수 없는 항목을 동의 완료로 취급하지 않는다.
    if (listOf(age14, terms, privacy).any { it?.needsConsent == null || it.requiredVersion.isNullOrBlank() }) {
        return DomainResult.Failure(DomainError.Serialization)
    }
    return DomainResult.Success(toEntity())
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
