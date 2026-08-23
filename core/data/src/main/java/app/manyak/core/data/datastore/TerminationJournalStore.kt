package app.manyak.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.manyak.core.data.di.IoDispatcher
import app.manyak.core.data.di.SessionJournalDataStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** 종료 정리가 어디까지 끝났는지. 프로세스가 죽어도 이 단계부터 멱등하게 재개한다. */
enum class TerminationStep {
    STARTED,
    SERVER_LOGOUT_ATTEMPTED,
    TOKENS_CLEARED,
    USER_DATA_CLEARED,
    PROVIDERS_CLEARED,
    IDENTIFIERS_ROTATED,
}

/**
 * 종료 정리 저널.
 *
 * **refresh 토큰·사용자 ID 같은 식별·비밀값을 복제하지 않는다**. 남기는 것은 재개에
 * 필요한 최소 정보뿐이다 — 진행 단계, 이번 종료가 쓸 새 `device_id` 후보, 아직 정리하지 못한 제공자,
 * 사용자에게 보여 줄 종료 사유.
 *
 * 새 `device_id` 를 **시작할 때** 고정하는 이유는 멱등성이다. 재시작이 단계 6 을 반복해도 계속 새 UUID 를
 * 만들지 않는다.
 */
@Serializable
data class TerminationJournal(
    val step: TerminationStep,
    val newDeviceId: String,
    val pendingProviders: List<String>,
    val notice: String,
)

@Singleton
class TerminationJournalStore
    @Inject
    constructor(
        @param:SessionJournalDataStore private val dataStore: DataStore<Preferences>,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        suspend fun read(): TerminationJournal? =
            withContext(ioDispatcher) {
                val raw = runCatching { dataStore.data.first()[JOURNAL_KEY] }.getOrNull() ?: return@withContext null
                runCatching { json.decodeFromString<TerminationJournal>(raw) }.getOrNull()
            }

        /** 각 단계가 끝날 때 다음 단계를 원자 기록한다. 실패하면 삭제를 시작하지 않고 재시도한다. */
        suspend fun write(journal: TerminationJournal): Boolean =
            withContext(ioDispatcher) {
                runCatching {
                    dataStore.edit { it[JOURNAL_KEY] = json.encodeToString(journal) }
                }.isSuccess
            }

        /**
         * 정리 장벽을 닫는다. 이 뒤에야 인증 그래프를 공개한다.
         *
         * 삭제에 실패했는데 로그인을 허용하면 다음 실행의 재개가 **새 사용자의 데이터**를 지운다.
         * 그래서 실패를 돌려주고, 성공할 때까지 장벽을 유지한다.
         */
        suspend fun clear(): Boolean =
            withContext(ioDispatcher) {
                runCatching { dataStore.edit { it.remove(JOURNAL_KEY) } }.isSuccess
            }

        private companion object {
            val JOURNAL_KEY = stringPreferencesKey("termination")
            val json = Json { ignoreUnknownKeys = true }
        }
    }
