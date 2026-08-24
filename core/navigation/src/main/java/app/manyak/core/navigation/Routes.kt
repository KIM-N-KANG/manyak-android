package app.manyak.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * 앱의 모든 목적지. **라우트 정의는 이 모듈 한 곳에만 등록한다**.
 * `:feature:*` 끼리 직접 참조하지 않으므로 화면 이동은 이 모듈을 거친다.
 *
 * 라우트에는 **복원 가능한 식별자만** 싣는다. 화면이 그릴 데이터는 목적지에서 다시 얻는다 —
 * 객체를 통째로 넘기면 프로세스 재시작 복원과 딥링크에서 깨진다.
 *
 * 백스택은 이 키들의 리스트 그 자체이고 프로세스 사망 시 클래스 이름과 함께 직렬화된다.
 * 그래서 모든 키는 `NavKey` 이면서 `@Serializable` 이어야 하고, 난독화에서 이름이 살아 있어야 한다.
 */
@Serializable
data object LoginRoute : NavKey

/**
 * 하단 탭 셋을 두르는 셸. 메인 백스택에서는 이 키 하나로 보이고, 탭 목적지들은 셸이 소유한
 * 탭별 백스택 안에만 있다.
 *
 * 셸을 두르지 않는 화면(스토리 상세·제작 퍼널·채팅 화면 등)은 이 키 위에 쌓아, 헤더와 하단 탭이
 * 두르는 범위를 백스택 구조가 그대로 드러내게 한다.
 */
@Serializable
data object MainTabsRoute : NavKey

@Serializable
data object HomeRoute : NavKey

@Serializable
data object ChatListRoute : NavKey

@Serializable
data object MyRoute : NavKey

/** 퍼널 단계는 각각 목적지이며, 이 화면은 셸 없이 [MainTabsRoute] 위에 쌓인다. */
@Serializable
data object CreateKeywordRoute : NavKey

/**
 * 스토리라인 선택 단계. [CreateKeywordRoute] 위에 쌓여 뒤로가기가 곧 키워드 단계 복귀이고,
 * 백스택이 살아 있어 키워드 입력은 그대로 유지된다.
 */
@Serializable
data object CreateStorylineRoute : NavKey

/**
 * 공용 법적 문서. **제품 백스택 밖에 두고 양쪽 백스택 모두에 등록해** 인증 상태와 무관하게 연다.
 * 뒤로가기는 진입한 화면으로 돌아가고, 여기서 임의의 메인 목적지로 이동하는 경로는 두지 않는다.
 */
@Serializable
data class LegalRoute(
    val document: LegalDocument,
) : NavKey

enum class LegalDocument {
    TERMS,
    PRIVACY,
}
