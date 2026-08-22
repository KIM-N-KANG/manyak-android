package app.manyak.core.data.session

/**
 * 사용자에게 귀속된 로컬 상태를 가진 저장소.
 *
 * 세션 종료 흐름이 이 목록을 통째로 지운다(하네스 §3-3-4 로그아웃 절차 4단계). 새 저장소를 만들 때
 * 이 인터페이스를 구현해 Hilt 멀티바인딩에 넣지 않으면 정리 대상에서 빠지고, 그 누락이 리뷰 diff 에
 * 드러나도록 바인딩 선언을 한곳에 모은다.
 *
 * 정리는 **여러 번 실행해도 안전해야 하고**, 한 저장소의 실패가 다른 저장소를 막지 않아야 한다.
 */
interface UserScopedStore {
    /** 진단용 이름. 실패한 항목을 저널에 남길 때 쓴다. 사용자에게 보이지 않는다. */
    val storeName: String

    suspend fun clearUserData()
}
