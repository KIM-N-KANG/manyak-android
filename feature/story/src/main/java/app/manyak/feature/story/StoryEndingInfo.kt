package app.manyak.feature.story

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.manyak.core.ui.R
import app.manyak.designsystem.theme.ManyakTheme
import kotlinx.coroutines.launch
import app.manyak.designsystem.R as DesignsystemR

/**
 * 엔딩 라벨 옆 안내 버튼. 엔딩이 스토리가 아니라 고른 시작 상황에 딸린다는 사실은 목록만 봐서는
 * 드러나지 않는데, 그렇다고 늘 떠 있는 문장으로 두면 한 번 읽고 나면 자리만 차지한다.
 *
 * 띄우고 내리는 일은 M3 [TooltipBox] 가 맡는다 — 화면 가장자리 회피, 바깥 탭·뒤로가기 닫기,
 * 접근성 계약이 이미 들어 있다. 다만 [TooltipDefaults] 의 기본 말풍선은 쓰지 않는다: 이 앱에서
 * 떠 있는 판의 모양은 시작 상황 셀렉트 메뉴가 이미 정해 놨다.
 *
 * 길게 누르기(기본 제스처)는 끄고 탭으로만 연다. 손가락을 떼면 사라지는 안내는 문장을 다 읽기 전에
 * 닫히고, 이 자리에는 길게 눌러 볼 다른 동작도 없다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EndingInfoButton(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    // 기본값(false)은 잠깐 떴다 스스로 사라진다 — 한 문장이라도 읽을 시간은 사용자가 정한다.
    val state = rememberTooltipState(isPersistent = true)
    val label = stringResource(R.string.story_detail_endings_info)

    TooltipBox(
        modifier = modifier,
        positionProvider =
            TooltipDefaults.rememberTooltipPositionProvider(
                positioning = TooltipAnchorPosition.Below,
                spacingBetweenTooltipAndAnchor = ManyakTheme.spacing.inline,
            ),
        tooltip = { EndingInfoTooltip() },
        state = state,
        enableUserInput = false,
    ) {
        Box(
            modifier =
                Modifier
                    .size(ButtonSize)
                    .clip(ManyakTheme.shapes.pill)
                    .clickable(role = Role.Button, onClickLabel = label) {
                        if (state.isVisible) state.dismiss() else scope.launch { state.show() }
                    },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier.size(ManyakTheme.sizes.iconSmall),
                painter = painterResource(DesignsystemR.drawable.ic_info),
                contentDescription = label,
                tint = ManyakTheme.colors.textSubtle,
            )
        }
    }
}

/**
 * 안내 말풍선의 판. 셀렉트 메뉴와 같은 배경·테두리·그림자다 — 같은 화면에서 뜨는 것끼리 모양이
 * 갈리면 무엇이 겹쳐 있는 판인지 매번 다시 읽어야 한다. 테두리는 흰 판과 흰 배경의 경계를 세우려는
 * 것이라 셀렉트 메뉴와 같은 무그림자 원칙의 예외다.
 */
@Composable
private fun EndingInfoTooltip(modifier: Modifier = Modifier) {
    Text(
        modifier =
            modifier
                .widthIn(max = TooltipMaxWidth)
                .shadow(TooltipShadowElevation, ManyakTheme.shapes.control)
                .background(ManyakTheme.colors.surfaceRaised, ManyakTheme.shapes.control)
                .border(TooltipBorderWidth, ManyakTheme.colors.border, ManyakTheme.shapes.control)
                .clip(ManyakTheme.shapes.control)
                .padding(
                    horizontal = ManyakTheme.spacing.component,
                    vertical = ManyakTheme.spacing.compact,
                ),
        text = stringResource(R.string.story_detail_endings_info_tooltip),
        style = ManyakTheme.typography.bodyMedium,
        color = ManyakTheme.colors.text,
    )
}

/** 라벨 글줄과 같은 높이. 더 키우면 안내 하나 때문에 섹션 라벨 줄만 두꺼워진다. */
private val ButtonSize = 24.dp

private val TooltipMaxWidth = 240.dp
private val TooltipShadowElevation = 4.dp
private val TooltipBorderWidth = 1.dp

@Preview(showBackground = true, name = "엔딩 안내 버튼")
@Composable
private fun EndingInfoButtonPreview() {
    ManyakTheme(darkTheme = false) {
        EndingInfoButton()
    }
}
