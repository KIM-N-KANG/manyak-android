package app.manyak.core.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme
import kotlin.math.roundToInt

/**
 * 목록 카드에서 여는 옵션 다이얼로그. 헤더 더보기 메뉴([ManyakOptionsMenu])와 같은 항목을 담지만
 * 카드는 앵커가 손가락 아래라 팝업이 카드를 가리므로 화면 가운데 다이얼로그로 연다.
 *
 * 열림 여부는 호출부가 든다 — 어느 카드의 다이얼로그인지가 곧 화면 상태라 회전에서 살아남아야 한다.
 *
 * @param preview 어느 대상의 옵션인지 보여 주는 카드 미리보기. 회색 상자 안에 놓여 항목과 구분되며,
 *  카드 여러 장 중 무엇을 골랐는지 다이얼로그 안에서 확인하게 한다.
 */
@Composable
fun ManyakOptionsDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ManyakDialog(modifier = modifier, onDismissRequest = onDismissRequest) {
        ManyakOptionsDialogContent(preview = preview, content = content)
    }
}

/** 옵션 다이얼로그의 내용. 같은 창이 확인 다이얼로그로 바뀌는 흐름에서는 [ManyakDialog] 안에 직접 놓는다. */
@Composable
fun ManyakOptionsDialogContent(
    preview: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().padding(ManyakTheme.spacing.gutter)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    // 미리보기와 항목 사이. 항목 터치 영역이 위로 넓어 실제로는 이보다 떠 보인다.
                    .padding(bottom = ManyakTheme.spacing.compact)
                    .background(ManyakTheme.colors.backgroundNeutral, ManyakTheme.shapes.card)
                    .padding(ManyakTheme.spacing.compact),
        ) {
            preview()
        }
        content()
    }
}

/** 다이얼로그 항목 하나. 메뉴 항목과 같은 구성이되 폭을 채우고 터치 타깃을 확보한다. */
@Composable
fun ManyakOptionsDialogItem(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDanger: Boolean = false,
) {
    val color = if (isDanger) ManyakTheme.colors.textDanger else ManyakTheme.colors.text
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ManyakTheme.sizes.control)
                .clip(ManyakTheme.shapes.menuItem)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = ManyakTheme.spacing.controlHorizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        Icon(
            modifier = Modifier.size(ManyakTheme.sizes.icon),
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = color,
        )
        Text(text = label, style = ManyakTheme.typography.bodyLarge, color = color)
    }
}

/**
 * 카드 옵션 다이얼로그를 여는 트리거. 카드 바탕 위에 놓이므로 필드를 깔지 않고 아이콘만 둔다.
 * 상자를 크게 잡을수록 제목 줄이 두꺼워져 제목이 아래로 밀리므로 아이콘에 바짝 붙인다.
 */
@Composable
fun ManyakMoreButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ManyakIconButton(
        modifier = modifier,
        iconRes = R.drawable.ic_more,
        contentDescription = contentDescription,
        onClick = onClick,
        size = MoreButtonSize,
        iconSize = ManyakTheme.sizes.iconSmall,
        shape = ManyakTheme.shapes.menuItem,
        tint = ManyakTheme.colors.textSubtle,
    )
}

/**
 * 제목 줄 옆에 놓인 [ManyakMoreButton] 의 정렬선. 아이콘 가운데를 제목 **첫 줄 글자**의 가운데에 맞춘다 —
 * 글줄 상자 가운데에 맞추면 위아래 여백까지 세는 탓에 아이콘이 글자보다 낮게 보인다. 제목 쪽은
 * `alignBy(FirstBaseline)` 이어야 한다.
 */
@Composable
fun RowScope.moreButtonTitleAlignment(titleStyle: TextStyle): Modifier {
    // 한글은 베이스라인 위로만 글자를 채우므로, 그 절반만큼 위가 글자의 가운데다.
    val halfGlyphHeight =
        with(LocalDensity.current) { (titleStyle.fontSize.toPx() * HANGUL_GLYPH_HEIGHT_RATIO / 2f).roundToInt() }
    return Modifier.alignBy { measured -> measured.measuredHeight / 2 + halfGlyphHeight }
}

private val MoreButtonSize = 24.dp

/**
 * 한글 글자의 가운데가 베이스라인 위 어디쯤인지의 배율. 글자는 베이스라인 위로 약 0.9em 을 채우므로
 * 그 가운데는 0.9em 의 절반 지점이다. 기기 캡처에서 글자와 아이콘의 픽셀 범위를 재서 맞춘 값이고
 * (0.73 은 아이콘이 1~2dp 낮아 보였다), 글자 크기를 키워도 비율은 그대로라 dp 가 아니라 배율로 둔다.
 */
private const val HANGUL_GLYPH_HEIGHT_RATIO = 0.9f
