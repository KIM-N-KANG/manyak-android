package app.manyak.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import app.manyak.designsystem.theme.ManyakTheme

/**
 * 스토리 뱃지의 크기 단계. 장르 뱃지와 표지 위 턴 수 뱃지가 함께 쓴다.
 *
 * 같은 뱃지가 목록 카드와 상세에서 크기만 달라지므로, 호출부마다 서체와 여백을 따로 지정하지 않고
 * 단계로 고른다 — 값으로 받으면 두 화면의 뱃지가 조금씩 어긋난다.
 */
enum class StoryBadgeScale {
    /** 목록 카드. 2열 그리드라 폭이 좁고 카드의 다른 글자도 작다. */
    Compact,

    /** 스토리 상세. 표지가 화면 폭을 채우고 본문 서체도 한 단계 크다. */
    Large,
    ;

    internal val textStyle: TextStyle
        @Composable
        get() =
            when (this) {
                Compact -> ManyakTheme.typography.labelSmall
                Large -> ManyakTheme.typography.labelLarge
            }

    internal val horizontalPadding: Dp
        @Composable
        get() =
            when (this) {
                Compact -> ManyakTheme.spacing.compact
                Large -> ManyakTheme.spacing.component
            }

    internal val verticalPadding: Dp
        @Composable
        get() =
            when (this) {
                Compact -> ManyakTheme.spacing.hairline
                Large -> ManyakTheme.spacing.inline
            }
}
