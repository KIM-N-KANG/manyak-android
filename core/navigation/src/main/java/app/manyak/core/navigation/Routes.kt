package app.manyak.core.navigation

import kotlinx.serialization.Serializable

/**
 * 앱의 모든 목적지. **라우트 정의는 이 모듈 한 곳에만 등록한다**.
 * `:feature:*` 끼리 직접 참조하지 않으므로 화면 이동은 이 모듈을 거친다.
 *
 * 라우트에는 **복원 가능한 식별자만** 싣는다. 화면이 그릴 데이터는 목적지에서 다시 얻는다 —
 * 객체를 통째로 넘기면 프로세스 재시작 복원과 딥링크에서 깨진다.
 */
@Serializable
data object AuthGraphRoute

@Serializable
data object LoginRoute

@Serializable
data object MainGraphRoute

/**
 * 하단 탭 셋을 두르는 셸. 메인 그래프에서는 이 목적지 하나로 보이고, 탭 목적지들은 셸 안쪽에만 있다.
 *
 * 셸을 두르지 않는 화면(스토리 상세·제작 퍼널·채팅 화면 등)은 이 목적지의 형제로 등록해,
 * 헤더와 하단 탭이 두르는 범위를 목적지 구조가 그대로 드러내게 한다.
 */
@Serializable
data object MainTabsRoute

@Serializable
data object HomeRoute

@Serializable
data object ChatListRoute

@Serializable
data object MyRoute

/**
 * 공용 법적 문서. **제품 그래프 밖에 두고 루트에 한 번만 등록해** 인증 그래프와 메인 그래프
 * 양쪽에서 연다. 뒤로가기는 진입한 화면으로 돌아가고, 여기서 임의의 메인 목적지로
 * 이동하는 경로는 두지 않는다.
 */
@Serializable
data class LegalRoute(
    val document: LegalDocument,
)

enum class LegalDocument {
    TERMS,
    PRIVACY,
}
