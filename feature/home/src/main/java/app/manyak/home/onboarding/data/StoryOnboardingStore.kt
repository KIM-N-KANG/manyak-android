package app.manyak.home.onboarding.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import app.manyak.common.data.di.IoDispatcher
import app.manyak.common.domain.session.UserScopedStore
import app.manyak.home.onboarding.domain.StoryOnboardingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 회원 귀속 표시라 로그아웃 정리 대상이다 — 남으면 공용 기기의 다음 회원이 온보딩에 갇힌다.
 *
 * 읽기 실패는 "온보딩 없음"으로 흡수한다. 표시를 읽지 못했다고 홈 진입이 막혀서는 안 된다.
 */
@Singleton
class StoryOnboardingStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : StoryOnboardingRepository,
        UserScopedStore {
        // 기기 파일과 섞지 않는다 — 기기 파일은 로그아웃에도 남아야 하는 값만 담는다.
        private val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(STORE_NAME) }

        override val storeName: String = STORE_NAME

        override val pending: Flow<Boolean> =
            dataStore.data
                .catch { emit(emptyPreferences()) }
                .map { preferences -> preferences[PENDING_KEY] == true }

        override suspend fun markPending() {
            write { it[PENDING_KEY] = true }
        }

        override suspend fun complete() {
            write { it.remove(PENDING_KEY) }
        }

        /** 실패를 삼키지 않는다. 종료 흐름이 재시도하고, 성공 전에는 다음 단계로 넘어가지 않는다. */
        override suspend fun clearUserData(): Boolean = write { it.remove(PENDING_KEY) }

        private suspend fun write(transform: (MutablePreferences) -> Unit): Boolean =
            withContext(ioDispatcher) {
                try {
                    dataStore.edit(transform)
                    true
                } catch (_: IOException) {
                    false
                }
            }

        private companion object {
            const val STORE_NAME = "story-onboarding"
            val PENDING_KEY = booleanPreferencesKey("story_onboarding_pending")
        }
    }
