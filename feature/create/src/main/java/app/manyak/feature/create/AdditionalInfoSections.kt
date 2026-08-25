package app.manyak.feature.create

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.manyak.core.ui.R
import app.manyak.core.ui.text.storyAnnotatedString
import app.manyak.core.ui.theme.ManyakTheme

/** 선택한 스토리라인 본문. 기본은 한 줄로 접혀 있고 더보기·접기로 펼친다. */
@Composable
internal fun SelectedStorylineBox(
    text: String,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var collapsible by remember { mutableStateOf(false) }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize()
                .background(ManyakTheme.colors.backgroundNeutral)
                .padding(ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
    ) {
        Text(
            text = storyAnnotatedString(text),
            style = ManyakTheme.typography.bodyReading,
            color = ManyakTheme.colors.text,
            maxLines = if (expanded) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layoutResult ->
                if (!expanded) collapsible = layoutResult.hasVisualOverflow
            },
        )
        if (collapsible) {
            SelectedStorylineToggle(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                expanded = expanded,
                onClick = { expanded = !expanded },
            )
        }
    }
}

@Composable
private fun SelectedStorylineToggle(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labelRes = if (expanded) R.string.create_additional_collapse else R.string.create_additional_expand
    val iconRes = if (expanded) R.drawable.ic_angle_up else R.drawable.ic_angle_down
    Row(
        modifier =
            modifier
                .clip(ManyakTheme.shapes.control)
                .clickable(onClick = onClick)
                .padding(horizontal = ManyakTheme.spacing.compact, vertical = ManyakTheme.spacing.inline),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        Text(
            text = stringResource(labelRes),
            style = ManyakTheme.typography.labelSmall,
            color = ManyakTheme.colors.textSubtlest,
        )
        Icon(
            modifier = Modifier.size(16.dp),
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = ManyakTheme.colors.textSubtlest,
        )
    }
}

@Composable
internal fun RecommendedInfoSection(
    recommendations: List<String>,
    selectedRecommendations: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .padding(horizontal = ManyakTheme.spacing.gutter)
                .padding(top = ManyakTheme.spacing.block, bottom = ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        KeywordSectionLabel(
            text = stringResource(R.string.create_additional_recommended_label),
            required = false,
        )
        recommendations.forEach { recommendation ->
            RecommendationChip(
                text = recommendation,
                selected = recommendation in selectedRecommendations,
                onClick = { onToggle(recommendation) },
            )
        }
    }
}

/** 키워드 칩과 같은 선택 문법을 쓰되, 문장 전체가 들어가므로 폭을 채우고 왼쪽 정렬한다. */
@Composable
private fun RecommendationChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (selected) ManyakTheme.colors.backgroundBrandSubtle else ManyakTheme.colors.surfaceRaised
    val borderColor = if (selected) ManyakTheme.colors.borderBrand else ManyakTheme.colors.border
    val textColor = if (selected) ManyakTheme.colors.textBrand else ManyakTheme.colors.text
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ManyakTheme.sizes.input)
                .clip(ManyakTheme.shapes.control)
                .background(background)
                .border(1.dp, borderColor, ManyakTheme.shapes.control)
                .toggleable(
                    value = selected,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onValueChange = { onClick() },
                ).padding(
                    horizontal = ManyakTheme.spacing.controlHorizontal,
                    vertical = ManyakTheme.spacing.controlVertical,
                ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = text, style = ManyakTheme.typography.bodyMedium, color = textColor)
    }
}

@Composable
internal fun AdditionalInfoHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        KeywordSectionLabel(
            text = stringResource(R.string.create_additional_label),
            required = false,
        )
        Text(
            text =
                stringResource(
                    R.string.create_additional_max_count,
                    CreateAdditionalInfoUiState.INPUT_MAX_COUNT,
                ),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.text,
        )
    }
}

@Composable
internal fun AdditionalInfoRow(
    index: Int,
    input: AdditionalInfoInput,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        AdditionalInfoField(
            modifier = Modifier.weight(1f),
            value = input.value,
            label = stringResource(R.string.create_additional_input_description, index + 1),
            placeholder = placeholder,
            onValueChange = onValueChange,
        )
        IconButton(
            modifier = Modifier.size(32.dp),
            onClick = onRemove,
        ) {
            Icon(
                modifier = Modifier.size(16.dp),
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.create_additional_delete_description, index + 1),
                tint = ManyakTheme.colors.textSubtlest,
            )
        }
    }
}

/**
 * 여러 줄 입력과 글자 수 카운터를 담은 자유 텍스트 필드. 시각 문법은 [KeywordTextField]와 같다.
 *
 * 테두리·카운터를 decorationBox 안에 두는 것이 중요하다. 포커스가 들어올 때 스크롤 컨테이너가
 * 끌어올리는 범위는 decorationBox 전체라, 밖에 두면 커서 줄만 올라오고 카운터는 키보드에 가린다.
 */
@Composable
private fun AdditionalInfoField(
    value: String,
    label: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (focused) ManyakTheme.colors.borderInput else ManyakTheme.colors.border
    BasicTextField(
        modifier = modifier.semantics { contentDescription = label },
        value = value,
        onValueChange = onValueChange,
        textStyle = ManyakTheme.typography.bodyMedium.copy(color = ManyakTheme.colors.text),
        cursorBrush = SolidColor(ManyakTheme.colors.text),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            Column(
                modifier =
                    Modifier
                        .clip(ManyakTheme.shapes.control)
                        .background(ManyakTheme.colors.surfaceRaised)
                        .border(1.dp, borderColor, ManyakTheme.shapes.control)
                        .padding(
                            horizontal = ManyakTheme.spacing.controlHorizontal,
                            vertical = ManyakTheme.spacing.controlVertical,
                        ),
                verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = ManyakTheme.typography.bodyMedium,
                            color = ManyakTheme.colors.textDisabled,
                        )
                    }
                    innerTextField()
                }
                Box(modifier = Modifier.align(Alignment.End)) {
                    InputCounter(
                        length = value.length,
                        maxLength = CreateAdditionalInfoUiState.INPUT_MAX_LENGTH,
                    )
                }
            }
        },
    )
}
