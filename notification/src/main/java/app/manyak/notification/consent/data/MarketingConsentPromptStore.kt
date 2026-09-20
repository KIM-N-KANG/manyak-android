package app.manyak.notification.consent.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
 * 회원별 광고 동의 질문 단계. 남으면 공용 기기의 다음 회원에게 묻지 않게 되므로 로그아웃 정리 대상이다.
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

        override suspend fun claimPrompt(): Boolean =
            withContext(ioDispatcher) {
                runCatching {
                    val (next, prompt) = read().onEntry()
                    dataStore.edit { it.write(next) }
                    prompt
                }.getOrDefault(true)
            }

        override suspend fun markDeclined() = update { it.onDeclined() }

        override suspend fun markSettled() = update { it.onSettled() }

        /** 실패를 삼키지 않는다. 종료 흐름이 재시도하고, 성공 전에는 다음 단계로 넘어가지 않는다. */
        override suspend fun clearUserData(): Boolean =
            withContext(ioDispatcher) {
                runCatching {
                    dataStore.edit {
                        it.remove(DECLINES_KEY)
                        it.remove(ENTRIES_KEY)
                        it.remove(LEGACY_PROMPTED_KEY)
                    }
                }.isSuccess
            }

        private suspend fun read(): MarketingPromptState = dataStore.data.first().toState()

        private suspend fun update(transform: (MarketingPromptState) -> MarketingPromptState) {
            withContext(ioDispatcher) {
                runCatching { dataStore.edit { it.write(transform(it.toState())) } }
            }
        }

        private fun Preferences.toState(): MarketingPromptState =
            MarketingPromptState(
                // 단계 도입 전의 "물었음" 표시는 한 번 거절한 것으로 읽는다 — 허용했다면 서버가 이미 동의 상태다.
                declines = this[DECLINES_KEY] ?: if (this[LEGACY_PROMPTED_KEY] == true) 1 else 0,
                entriesSinceDecline = this[ENTRIES_KEY] ?: 0,
            )

        private fun androidx.datastore.preferences.core.MutablePreferences.write(state: MarketingPromptState) {
            this[DECLINES_KEY] = state.declines
            this[ENTRIES_KEY] = state.entriesSinceDecline
        }

        private companion object {
            const val STORE_NAME = "marketing-consent"
            val DECLINES_KEY = intPreferencesKey("marketing_consent_declines")
            val ENTRIES_KEY = intPreferencesKey("marketing_consent_entries_since_decline")
            val LEGACY_PROMPTED_KEY = booleanPreferencesKey("marketing_consent_prompted")
        }
    }

/**
 * 질문 단계의 순수한 전이. DataStore 없이 검사한다.
 *
 * @property declines 거절 횟수. 0 은 아직 묻지 않음, 1 은 한 번 거절, 2 는 닫힘(두 번 거절 또는 허용).
 * @property entriesSinceDecline 첫 거절 뒤 재진입 횟수.
 */
internal data class MarketingPromptState(
    val declines: Int,
    val entriesSinceDecline: Int,
) {
    /** 재진입 하나를 세고, 이번 진입에 물을지 돌려준다. */
    fun onEntry(): Pair<MarketingPromptState, Boolean> =
        when (declines) {
            0 -> this to true
            1 -> {
                val next = copy(entriesSinceDecline = entriesSinceDecline + 1)
                next to (next.entriesSinceDecline >= REPROMPT_ENTRY)
            }
            else -> this to false
        }

    fun onDeclined(): MarketingPromptState =
        MarketingPromptState(declines = minOf(declines + 1, CLOSED), entriesSinceDecline = 0)

    fun onSettled(): MarketingPromptState = MarketingPromptState(declines = CLOSED, entriesSinceDecline = 0)

    companion object {
        /** 첫 거절 뒤 몇 번째 재진입에 다시 묻는가. */
        const val REPROMPT_ENTRY = 3
        private const val CLOSED = 2
    }
}
