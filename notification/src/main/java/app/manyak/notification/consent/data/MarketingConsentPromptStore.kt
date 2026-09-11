package app.manyak.notification.consent.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import app.manyak.common.data.di.IoDispatcher
import app.manyak.common.domain.session.UserScopedStore
import app.manyak.notification.consent.domain.MarketingConsentPromptRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 회원별 "광고 동의를 물었다" 표시. 남으면 공용 기기의 다음 회원에게 묻지 않게 되므로 로그아웃 정리 대상이다.
 *
 * 읽기·쓰기 실패는 "아직 묻지 않음" 으로 본다 — 한 번 더 묻는 쪽이 영영 묻지 않는 쪽보다 낫다.
 */
@Singleton
class MarketingConsentPromptStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : MarketingConsentPromptRepository,
        UserScopedStore {
        // 기기 파일과 섞지 않는다 — 기기 파일은 로그아웃에도 남아야 하는 값만 담는다.
        private val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(STORE_NAME) }

        override val storeName: String = STORE_NAME

        override suspend fun wasPrompted(): Boolean =
            withContext(ioDispatcher) {
                runCatching { dataStore.data.first()[PROMPTED_KEY] == true }.getOrDefault(false)
            }

        override suspend fun markPrompted() {
            withContext(ioDispatcher) {
                runCatching { dataStore.edit { it[PROMPTED_KEY] = true } }
            }
        }

        /** 실패를 삼키지 않는다. 종료 흐름이 재시도하고, 성공 전에는 다음 단계로 넘어가지 않는다. */
        override suspend fun clearUserData(): Boolean =
            withContext(ioDispatcher) {
                runCatching { dataStore.edit { it.remove(PROMPTED_KEY) } }.isSuccess
            }

        private companion object {
            const val STORE_NAME = "marketing-consent"
            val PROMPTED_KEY = booleanPreferencesKey("marketing_consent_prompted")
        }
    }
