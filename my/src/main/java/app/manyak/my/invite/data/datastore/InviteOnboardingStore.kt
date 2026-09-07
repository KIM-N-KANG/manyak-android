package app.manyak.my.invite.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import app.manyak.common.data.di.IoDispatcher
import app.manyak.common.domain.session.UserScopedStore
import app.manyak.my.data.di.ProfileDataStore
import app.manyak.my.invite.domain.InviteOnboardingRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 신규 가입 초대 코드 안내를 아직 보여 주지 않았다는 표시.
 *
 * **사용자 귀속 데이터라 프로필 캐시와 같은 DataStore 파일을 쓰고 세션 종료 정리 대상이다** — 남으면
 * 공용 기기의 다음 사용자에게 이전 회원의 안내가 뜬다.
 *
 * 읽기 실패는 "안내 없음"으로 흡수한다. 표시를 읽지 못했다고 앱 진입이 막혀서는 안 되고, 실패의
 * 결과는 안내 한 번을 놓치는 것뿐이다(자격은 서버가 들고 있다).
 */
@Singleton
class InviteOnboardingStore
    @Inject
    constructor(
        @param:ProfileDataStore private val dataStore: DataStore<Preferences>,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : InviteOnboardingRepository,
        UserScopedStore {
        override val storeName: String = "invite-onboarding"

        override val pending: Flow<Boolean> =
            dataStore.data
                .catch { emit(emptyPreferences()) }
                .map { preferences -> preferences[PENDING_KEY] == true }

        override suspend fun markPending() = write { it[PENDING_KEY] = true }

        override suspend fun acknowledge() = write { it.remove(PENDING_KEY) }

        /** 실패를 삼키지 않는다. 종료 흐름이 재시도하고, 성공 전에는 다음 단계로 넘어가지 않는다. */
        override suspend fun clearUserData(): Boolean =
            withContext(ioDispatcher) {
                runCatching { dataStore.edit { it.remove(PENDING_KEY) } }.isSuccess
            }

        private suspend fun write(transform: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
            withContext(ioDispatcher) {
                runCatching { dataStore.edit(transform) }
            }
        }

        private companion object {
            val PENDING_KEY = booleanPreferencesKey("invite_onboarding_pending")
        }
    }
