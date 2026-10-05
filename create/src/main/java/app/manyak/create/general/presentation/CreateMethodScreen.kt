package app.manyak.create.general.presentation

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.manyak.analytics.entity.AnalyticsEvent
import app.manyak.analytics.presentation.LocalAnalytics
import app.manyak.create.R
import app.manyak.designsystem.component.ManyakIconButton
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.common.R as CommonR
import app.manyak.designsystem.R as DesignsystemR

/**
 * 선택지 카드 한 장의 높이 범위. 카드는 남는 높이를 반씩 채우되 웹과 같이 375×667 화면에서 헤더(56dp) 아래가
 * 스크롤 없이 딱 차는 높이(아래 여백과 카드 간격 16dp씩)부터, 412×924 화면에서 위 16dp와 아래 32dp 여백이
 * 남는 높이까지만 늘어난다. 남는 높이는 두 카드 묶음을 가운데에 두어 위아래로 나눈다.
 */
private val OptionMinHeight = (667.dp - 56.dp - 32.dp) / 2
private val OptionMaxHeight = (924.dp - 56.dp - 64.dp) / 2
private val CardBorderWidth = 1.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMethodScreen(
    onClose: () -> Unit,
    onSelectSimple: () -> Unit,
    onSelectGeneral: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val analytics = LocalAnalytics.current
    val resolver = LocalContext.current.contentResolver
    // 애니메이션 길이 배율을 0으로 둔 기기에서는 일러스트를 움직이지 않고 완성된 장면만 보여 준다.
    val reducedMotion =
        remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        TopAppBar(
            title = {
                Text(
                    stringResource(R.string.general_method_title),
                    style = ManyakTheme.typography.bodyLargeStrong,
                    color = ManyakTheme.colors.text,
                )
            },
            navigationIcon = {
                ManyakIconButton(
                    iconRes = DesignsystemR.drawable.ic_arrow_back,
                    contentDescription = stringResource(CommonR.string.common_back),
                    onClick = onClose,
                )
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = ManyakTheme.colors.surface),
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val gap = ManyakTheme.spacing.gutter
            val optionHeight = ((maxHeight - gap * 2) / 2).coerceIn(OptionMinHeight, OptionMaxHeight)
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(start = gap, end = gap, bottom = gap),
                verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically),
            ) {
                MethodCard(
                    title = stringResource(R.string.general_method_simple),
                    description = stringResource(R.string.general_method_simple_description),
                    onClick = {
                        analytics.track(AnalyticsEvent.StoryCreateMethodSelected("simple"))
                        onSelectSimple()
                    },
                    modifier = Modifier.height(optionHeight),
                ) { SimpleCreateIllustration(reducedMotion, it) }
                MethodCard(
                    title = stringResource(R.string.general_method_general),
                    description = stringResource(R.string.general_method_general_description),
                    onClick = {
                        analytics.track(AnalyticsEvent.StoryCreateMethodSelected("general"))
                        onSelectGeneral()
                    },
                    modifier = Modifier.height(optionHeight),
                ) { GeneralCreateIllustration(reducedMotion, it) }
            }
        }
    }
}

@Composable
private fun MethodCard(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    illustration: @Composable (Modifier) -> Unit,
) {
    val colors = ManyakTheme.colors
    val shape = ManyakTheme.shapes.overlay
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .border(CardBorderWidth, colors.border, shape)
            .background(colors.surfaceRaised)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(CardBorderWidth),
    ) {
        illustration(Modifier.weight(1f))
        HorizontalDivider(thickness = CardBorderWidth, color = colors.border)
        Column(
            Modifier.padding(
                horizontal = ManyakTheme.spacing.gutter,
                vertical = ManyakTheme.spacing.controlHorizontal,
            ),
        ) {
            Text(title, style = ManyakTheme.typography.bodyLargeStrong, color = colors.text)
            Text(
                description,
                Modifier.padding(top = ManyakTheme.spacing.inline),
                style =
                    ManyakTheme.typography.bodyMedium.copy(
                        // 한글을 글자 단위가 아니라 어절 경계에서 줄바꿈한다.
                        lineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase),
                        localeList = LocaleList("ko-KR"),
                    ),
                color = colors.textSubtle,
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 860)
@Composable
private fun CreateMethodPreview() {
    ManyakTheme { CreateMethodScreen(onClose = {}, onSelectSimple = {}, onSelectGeneral = {}) }
}
