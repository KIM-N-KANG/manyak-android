package app.manyak.home.search.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.manyak.common.data.di.DeviceDataStore
import app.manyak.common.data.di.IoDispatcher
import app.manyak.home.search.domain.RecentSearchRepository
import app.manyak.home.search.domain.prependRecentSearch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 최근 검색어를 기기 파일에 최신순 JSON 배열 하나로 둔다. 계정과 무관한 기기 기록이라 로그아웃 정리에 참여하지 않는다.
 *
 * 읽거나 쓰지 못하면 남기지 않고 빈 목록으로 본다. 기록이 없다고 검색이 막혀서는 안 된다.
 */
@Singleton
class RecentSearchStore
    @Inject
    constructor(
        @param:DeviceDataStore private val dataStore: DataStore<Preferences>,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : RecentSearchRepository {
        override val terms: Flow<List<String>> =
            dataStore.data
                .catch { emit(emptyPreferences()) }
                .map { preferences -> preferences.terms() }

        override suspend fun add(term: String) = edit { it.writeTerms(it.terms().prependRecentSearch(term)) }

        override suspend fun remove(term: String) =
            edit { it.writeTerms(it.terms().filterNot { saved -> saved == term }) }

        override suspend fun clear() = edit { it.remove(TERMS_KEY) }

        private suspend fun edit(transform: (MutablePreferences) -> Unit) {
            withContext(ioDispatcher) {
                try {
                    dataStore.edit(transform)
                } catch (_: IOException) {
                    // 기록을 남기지 못해도 검색은 이어 간다.
                }
            }
        }

        private fun Preferences.terms(): List<String> =
            this[TERMS_KEY]
                ?.let { raw ->
                    try {
                        Json.decodeFromString<List<String>>(raw)
                    } catch (_: SerializationException) {
                        null
                    } catch (_: IllegalArgumentException) {
                        null
                    }
                }.orEmpty()

        private fun MutablePreferences.writeTerms(terms: List<String>) {
            this[TERMS_KEY] = Json.encodeToString(terms)
        }

        private companion object {
            val TERMS_KEY = stringPreferencesKey("recent_story_searches")
        }
    }
