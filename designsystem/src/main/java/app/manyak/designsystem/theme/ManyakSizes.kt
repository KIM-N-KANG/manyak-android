package app.manyak.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Semantic — 컨트롤 크기.
 *
 * 토큰 정본에는 높이가 없어 이 레포가 소유하는 값이다(DESIGN.md). 일반 컨트롤은 안드로이드 최소
 * 터치 타깃과 같은 48dp, 밀도 높은 입력·칩과 카드 안 라벨 버튼의 보이는 높이는 40dp 로 구분한다.
 */
@Immutable
data class ManyakSizes(
    /** 48dp — 버튼·탭처럼 탭 가능한 일반 컨트롤의 높이 */
    val control: Dp,
    /** 40dp — 입력창·칩·셀렉트 앵커·앱 바 액션 버튼처럼 밀도 높은 컨트롤의 최소 높이 */
    val input: Dp,
    /** 40dp — 카드 안에 놓이는 라벨 버튼의 보이는 높이. 터치 영역은 최소 48dp 를 그대로 유지한다 */
    val controlCompact: Dp,
    /** 32dp — 작은 보조 버튼. 인물 머리 줄의 접기와 삭제, 본문 옆 아이콘에 사용한다 */
    val controlSmall: Dp,
    /** 12dp — 작은 보조 버튼 안에서 12sp 라벨 옆에 붙는 아이콘. 글자 크기와 맞춘다 */
    val iconTiny: Dp,
    /** 16dp — 밀도 높은 컨트롤 안에서 라벨 옆에 붙는 작은 아이콘 */
    val iconSmall: Dp,
    /** 20dp — 라벨 옆에 붙는 아이콘·제공자 로고 */
    val icon: Dp,
    /** 24dp — 하단 탭 아이콘. 라벨 옆이 아니라 위에 놓여 탭의 주된 시각 요소이므로 [icon]보다 크다 */
    val tabIcon: Dp,
    /** 24dp — 마냑 로고 락업의 높이. 폭은 원본 비율(89:32)로 따라간다 */
    val logo: Dp,
    /** 64dp — 시작 화면에서 혼자 튀는 로고 심벌의 크기 */
    val startupSymbol: Dp,
    /** 2dp — 미선택 체크박스 경계. Material 3 라디오 버튼의 선 두께와 동일 */
    val selectionBorderWidth: Dp,
    /** 60dp — 텍스트 시머 띠의 반폭 */
    val shimmerBandHalfWidth: Dp,
    /** 10dp — 마스코트 무대 바탕 점의 간격 */
    val generationDotGap: Dp,
    /** 1dp — 마스코트 무대 바탕 점의 반지름 */
    val generationDotRadius: Dp,
    /** 288dp — 채팅 안내 투어 카드의 폭 */
    val tourCardWidth: Dp,
    /** 6dp — 채팅 안내 투어 카드의 스텝 점 지름 */
    val tourStepDot: Dp,
    /** 128dp 제작 탭의 스토리, 초안, 검수 제출본 표지 폭 */
    val studioCoverWidth: Dp,
    /** 240dp 장르 검색 결과 메뉴의 최대 높이 */
    val genreMenuMaxHeight: Dp,
    /** 1dp 입력과 검색 메뉴의 경계 두께 */
    val inputBorderWidth: Dp,
    /** 4dp 입력 아래에 겹쳐지는 선택 메뉴의 그림자 */
    val selectMenuElevation: Dp,
)

internal val ManyakDefaultSizes =
    ManyakSizes(
        control = 48.dp,
        input = 40.dp,
        controlCompact = 40.dp,
        controlSmall = 32.dp,
        iconTiny = 12.dp,
        iconSmall = 16.dp,
        icon = 20.dp,
        tabIcon = 24.dp,
        logo = 24.dp,
        startupSymbol = 64.dp,
        selectionBorderWidth = 2.dp,
        shimmerBandHalfWidth = 60.dp,
        generationDotGap = 10.dp,
        generationDotRadius = 1.dp,
        tourCardWidth = 288.dp,
        tourStepDot = 6.dp,
        studioCoverWidth = 128.dp,
        genreMenuMaxHeight = 240.dp,
        inputBorderWidth = 1.dp,
        selectMenuElevation = 4.dp,
    )
