package app.manyak.my.credit.data.repository

import app.manyak.common.domain.credit.TrialsRepository
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.session.UserScopedStore
import app.manyak.common.entity.credit.Trials
import app.manyak.my.credit.data.api.TrialsApi
import app.manyak.my.credit.data.dto.toDomain
import app.manyak.network.data.api.apiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 메모리에만 둔다 — 소모 시점마다 서버가 정본을 다시 내려주므로 디스크에 남길 이유가 없다.
 * 그래도 회원 귀속 값이라 [UserScopedStore] 로 종료 정리에 참여한다. 남으면 다음 회원의 첫 화면이
 * 이전 회원의 잔여로 적용가를 그린다.
 */
@Singleton
class TrialsRepositoryImpl
    @Inject
    constructor(
        private val trialsApi: TrialsApi,
    ) : TrialsRepository,
        UserScopedStore {
        private val state = MutableStateFlow<Trials?>(null)

        override val trials: StateFlow<Trials?> = state.asStateFlow()

        override val storeName: String = "trials"

        override suspend fun refresh() {
            // 못 받은 자리는 정가만 보이고 적용가는 쉬머로 남으므로 오류 안내를 따로 띄우지 않는다.
            val result = apiCall { trialsApi.trials() }
            if (result is DomainResult.Success) state.value = result.value.toDomain()
        }

        override suspend fun clearUserData(): Boolean {
            state.value = null
            return true
        }
    }
