package app.manyak.my.credit.data.repository

import app.manyak.common.domain.credit.CreditPolicyRepository
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.credit.CreditPolicy
import app.manyak.my.credit.data.api.CreditPolicyApi
import app.manyak.my.credit.data.dto.toDomain
import app.manyak.network.data.api.apiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreditPolicyRepositoryImpl
    @Inject
    constructor(
        private val creditPolicyApi: CreditPolicyApi,
    ) : CreditPolicyRepository {
        private val state = MutableStateFlow<CreditPolicy?>(null)

        override val policy: StateFlow<CreditPolicy?> = state.asStateFlow()

        override suspend fun refresh() {
            // 실패는 화면이 알 필요가 없다. 수치를 못 받은 자리는 자리표시 숫자로 그려지므로
            // 오류 안내를 따로 띄우지 않고 들고 있던 값도 지우지 않는다.
            val result = apiCall { creditPolicyApi.creditPolicies() }
            if (result is DomainResult.Success) state.value = result.value.toDomain()
        }
    }
