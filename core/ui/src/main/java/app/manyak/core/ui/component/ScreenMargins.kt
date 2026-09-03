package app.manyak.core.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 셸이 넘긴 여백에 하단 여유만 더한다. 좌우에는 화면 여백을 더하지 않는다 — 그 여백은 행 카드 안쪽에
 * 있어야 눌림 표시가 화면 폭을 채운다.
 *
 * 하단 여유가 그리드(`gutter`)보다 작은 `compact` 인 이유는 행 목록의 리듬이 다르기 때문이다 — 카드가
 * 스스로 위아래 `compact` 를 갖고 붙어 있으므로, 마지막 카드 아래도 같은 값이어야 카드 사이와 끝이 같은
 * 간격으로 읽힌다.
 */
@Composable
fun PaddingValues.withRowListMargins(): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection),
        top = calculateTopPadding(),
        end = calculateEndPadding(layoutDirection),
        bottom = calculateBottomPadding() + ManyakTheme.spacing.compact,
    )
}

/**
 * 셸이 넘긴 여백에 화면 좌우 여백을 더한다. 목록 항목이 화면 가장자리에 닿지 않게 하면서도,
 * 스크롤되는 콘텐츠가 헤더·탭 아래로 흘러 들어가는 성질은 그대로 둔다.
 *
 * **하단 여유는 `gutter` 다.** 그리드 행 사이 간격과 같은 값이라 마지막 행 아래가 행 사이와 같은
 * 리듬으로 끝난다. 셸이 없는 전체 화면이 쓰는 `screenBottom` 은 여기에 쓰지 않는다 — 그 아래에
 * 늘 떠 있는 탭 바가 이미 끝을 알린다.
 */
@Composable
fun PaddingValues.withScreenMargins(): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection) + ManyakTheme.spacing.gutter,
        top = calculateTopPadding(),
        end = calculateEndPadding(layoutDirection) + ManyakTheme.spacing.gutter,
        bottom = calculateBottomPadding() + ManyakTheme.spacing.gutter,
    )
}
