package app.manyak.notice.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.manyak.common.data.di.DeviceDataStore
import app.manyak.common.data.di.IoDispatcher
import app.manyak.notice.domain.NoticeReadRepository
import app.manyak.notice.domain.withRead
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 기기 DataStore 에 읽은 순서대로 JSON 배열 문자열로 둔다. Preferences 의 문자열 집합은 순서가 없어
 * "최근 100개" 를 지킬 수 없다. 읽기·쓰기 실패는 "읽은 적 없음" 으로 본다 — 결과가 점 하나 더 보이는 것이라면
 * 영영 안 읽음으로 남는 것보다 낫다.
 */
@Singleton
class NoticeReadStore
    @Inject
    constructor(
        @param:DeviceDataStore private val dataStore: DataStore<Preferences>,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val json: Json,
    ) : NoticeReadRepository {
        override val readIds: Flow<List<String>> =
            dataStore.data
                .map { it[READ_IDS_KEY].orEmpty().toIds() }
                .catch { emit(emptyList()) }
                .distinctUntilChanged()

        override suspend fun markRead(noticeId: String) {
            withContext(ioDispatcher) {
                runCatching {
                    dataStore.edit { preferences ->
                        preferences[READ_IDS_KEY] =
                            json.encodeToString(preferences[READ_IDS_KEY].orEmpty().toIds().withRead(noticeId))
                    }
                }
            }
        }

        private fun String.toIds(): List<String> =
            if (isBlank()) {
                emptyList()
            } else {
                try {
                    json.decodeFromString<List<String>>(this)
                } catch (_: SerializationException) {
                    emptyList()
                } catch (_: IllegalArgumentException) {
                    emptyList()
                }
            }

        private companion object {
            val READ_IDS_KEY = stringPreferencesKey("notice_read_ids")
        }
    }
