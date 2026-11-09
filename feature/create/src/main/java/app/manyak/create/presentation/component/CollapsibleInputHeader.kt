package app.manyak.create.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.manyak.designsystem.component.ManyakTextButton
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.create.R as CreateR
import app.manyak.designsystem.R as DesignR

@Composable
internal fun CollapsibleInputHeader(
    headerLabel: String,
    countLabel: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // 입력 영역이 여닫히는 스프링과 같이 돈다.
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "section-chevron",
    )
    val toggleLabel =
        stringResource(if (expanded) CreateR.string.create_section_collapse else CreateR.string.create_section_expand)
    val deleteDescription = stringResource(CreateR.string.create_supporting_delete_description, headerLabel)
    // M3 TextButton 은 들어오는 최소 폭이 0일 때만 58dp 최소 폭을 강제한다. 0이 아닌 최소 폭을 넘겨 글자 폭에 맞춘다.
    val buttonModifier = Modifier.widthIn(min = 1.dp).height(ManyakTheme.sizes.controlSmall)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ManyakTheme.sizes.control)
                .background(ManyakTheme.colors.backgroundNeutral)
                .padding(start = ManyakTheme.spacing.gutter, end = ManyakTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InputHeaderLabel(headerLabel, countLabel, Modifier.weight(1f))
        // 버튼이 48dp 터치 타깃으로 레이아웃을 넓히면 글자 양옆에 빈 공간이 생겨 간격이 어긋난다.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            HeaderButtons(buttonModifier, toggleLabel, rotation, deleteDescription, onToggle, onDelete)
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun HeaderButtons(
    buttonModifier: Modifier,
    toggleLabel: String,
    rotation: Float,
    deleteDescription: String,
    onToggle: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ManyakTextButton(
            modifier = buttonModifier,
            onClick = onToggle,
            contentPadding = PaddingValues(horizontal = ManyakTheme.spacing.compact),
        ) {
            Text(text = toggleLabel, style = ManyakTheme.typography.labelSmall, color = ManyakTheme.colors.textSubtle)
            Spacer(Modifier.width(ManyakTheme.spacing.inline))
            Icon(
                painter = painterResource(DesignR.drawable.ic_chevron_down),
                contentDescription = null,
                tint = ManyakTheme.colors.textSubtle,
                modifier = Modifier.size(ManyakTheme.sizes.iconTiny).rotate(rotation),
            )
        }
        // 하나만 남아 지울 수 없는 항목은 삭제를 두지 않는다.
        if (onDelete == null) return@Row
        Spacer(Modifier.width(ManyakTheme.spacing.inline))
        ManyakTextButton(
            modifier = buttonModifier.semantics { contentDescription = deleteDescription },
            onClick = onDelete,
            contentPadding = PaddingValues(horizontal = ManyakTheme.spacing.compact),
        ) {
            Text(
                text = stringResource(CreateR.string.create_supporting_delete),
                style = ManyakTheme.typography.labelSmall,
                color = ManyakTheme.colors.textSubtle,
            )
        }
    }
}

@Composable
private fun InputHeaderLabel(
    headerLabel: String,
    countLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f, fill = false),
            text = headerLabel,
            style = ManyakTheme.typography.labelLarge,
            color = ManyakTheme.colors.textSubtle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            modifier =
                Modifier
                    .clip(ManyakTheme.shapes.pill)
                    .background(ManyakTheme.colors.backgroundNeutralPressed)
                    .padding(
                        horizontal = ManyakTheme.spacing.compact,
                        vertical = ManyakTheme.spacing.inline,
                    ),
            text = countLabel,
            style = ManyakTheme.typography.bodySmall,
            color = ManyakTheme.colors.textSubtle,
        )
    }
}

/**
 * 접히는 입력 항목 하나. 펼친 본문은 아래 여백을 깔고 있어 펼친 항목 사이만 벌어지고, 접힌 머리 줄끼리는 붙는다.
 */
@Composable
internal fun CollapsibleInputSection(
    headerLabel: String,
    countLabel: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
    contentSpacing: Dp = ManyakTheme.spacing.compact,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        CollapsibleInputHeader(
            headerLabel = headerLabel,
            countLabel = countLabel,
            expanded = expanded,
            onToggle = onToggle,
            onDelete = onDelete,
        )
        // 웹과 같은 튕김 없는 약 0.3초 스프링이다. 위 변을 붙잡아 입력 칸은 제자리에 두고 아래로 드러낸다.
        // 기본값처럼 아래를 붙잡으면 폼이 머리 줄 밑에서 미끄러져 나온다.
        val sizeSpec = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntSize.VisibilityThreshold)
        val fadeSpec = spring<Float>(stiffness = Spring.StiffnessMediumLow)
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(sizeSpec, expandFrom = Alignment.Top) + fadeIn(fadeSpec),
            exit = shrinkVertically(sizeSpec, shrinkTowards = Alignment.Top) + fadeOut(fadeSpec),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(ManyakTheme.spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(contentSpacing),
                content = content,
            )
        }
    }
}
