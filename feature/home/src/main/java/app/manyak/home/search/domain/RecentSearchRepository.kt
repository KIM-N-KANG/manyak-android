package app.manyak.home.search.domain

import kotlinx.coroutines.flow.Flow

/** 스토리 검색 화면의 최근 검색어. 기기 단위라 계정과 무관하고 로그아웃에도 남는다. */
interface RecentSearchRepository {
    /** 최신순 검색어. 읽지 못하면 빈 목록이다. */
    val terms: Flow<List<String>>

    suspend fun add(term: String)

    suspend fun remove(term: String)

    suspend fun clear()
}

/** 새 검색어를 맨 앞에 넣는다. 이미 있으면 맨 앞으로 옮기고 [limit] 을 넘으면 뒤를 자른다. */
fun List<String>.prependRecentSearch(
    term: String,
    limit: Int = RECENT_SEARCH_LIMIT,
): List<String> = (listOf(term) + filterNot { saved -> saved == term }).take(limit)

const val RECENT_SEARCH_LIMIT = 10
