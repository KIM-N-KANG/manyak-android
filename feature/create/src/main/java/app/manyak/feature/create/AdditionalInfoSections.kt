package app.manyak.feature.create

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.manyak.core.ui.R
import app.manyak.designsystem.component.ManyakIconButton
import app.manyak.designsystem.component.ManyakInputCounter
import app.manyak.designsystem.component.ManyakMultilineTextField
import app.manyak.designsystem.component.RowRevealTransition
import app.manyak.designsystem.text.storyAnnotatedString
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.designsystem.R as DesignsystemR

/** 선택한 스토리라인 본문. 진입 뒤 높이가 바뀌지 않게 더보기·접기 토글을 처음부터 함께 그린다. */
@Composable
internal fun SelectedStorylineBox(
    text: String,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
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
        )
        SelectedStorylineToggle(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            expanded = expanded,
            onClick = { expanded = !expanded },
        )
    }
}

@Composable
private fun SelectedStorylineToggle(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labelRes = if (expanded) R.string.create_additional_collapse else R.string.create_additional_expand
    val iconRes = if (expanded) DesignsystemR.drawable.ic_angle_up else DesignsystemR.drawable.ic_angle_down
    Row(
        modifier =
            modifier
                // 높이가 낮은 글자 버튼이라 컨트롤 곡률이면 알약으로 보인다 — 아이콘 버튼과 같은 곡률을 쓴다.
                .clip(ManyakTheme.shapes.menuItem)
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
                // 아래는 "추가 정보" 섹션이 이어지므로 섹션 사이 토큰을 쓴다 — 키워드 화면의 섹션 간격과 같다.
                .padding(top = ManyakTheme.spacing.block, bottom = ManyakTheme.spacing.section),
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

/**
 * 추가 정보 입력 칸 목록. 새 칸은 아래에서 자라 오르고, 지운 칸은 접힘이 끝난 뒤에야 [onRemove] 로
 * 빠진다 — 먼저 빼면 접히는 모습이 나오지 않는다.
 *
 * 목록 전체가 스크롤 항목 하나다. 칸마다 항목을 나누면 화면 밖으로 밀린 칸이 버려져, 다시 들어올 때
 * 등장 애니메이션을 처음부터 되풀이한다.
 *
 * 칸 사이 간격은 [Arrangement] 가 아니라 칸 안쪽 아래 여백이다. 배치 간격으로 두면 접힌 칸이
 * 차지하는 자리가 0 이 된 뒤에도 간격만 남아 빈 틈이 보인다.
 */
@Composable
internal fun AdditionalInfoRows(
    inputs: List<AdditionalInfoInput>,
    onValueChange: (Long, String) -> Unit,
    onRemove: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val placeholders = stringArrayResource(R.array.create_additional_placeholders)
    // 지우는 중인 칸. 구성 변경으로 이 표시를 잃으면 칸은 그대로 남는다(지워지지 않는 쪽이 안전하다).
    var exitingIds by remember { mutableStateOf(emptySet<Long>()) }
    // 들어올 때 이미 있던 칸은 그대로 그린다 — 화면에 닿자마자 칸들이 자라 오르면 안 된다.
    val initialIds = remember { inputs.map(AdditionalInfoInput::id).toSet() }
    Column(modifier = modifier.fillMaxWidth()) {
        inputs.forEachIndexed { index, input ->
            key(input.id) {
                RowRevealTransition(
                    entering = input.id !in initialIds,
                    exiting = input.id in exitingIds,
                    onExited = {
                        exitingIds = exitingIds - input.id
                        onRemove(input.id)
                    },
                ) {
                    AdditionalInfoRow(
                        modifier = Modifier.padding(bottom = ManyakTheme.spacing.compact),
                        index = index,
                        input = input,
                        placeholder = placeholders[index % placeholders.size],
                        onValueChange = { value -> onValueChange(input.id, value) },
                        onRemove = { exitingIds = exitingIds + input.id },
                    )
                }
            }
        }
    }
}

@Composable
private fun AdditionalInfoRow(
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
        ManyakMultilineTextField(
            modifier = Modifier.weight(1f),
            value = input.value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            contentDescription = stringResource(R.string.create_additional_input_description, index + 1),
            footer = {
                ManyakInputCounter(
                    length = input.value.length,
                    maxLength = CreateAdditionalInfoUiState.INPUT_MAX_LENGTH,
                )
            },
        )
        ManyakIconButton(
            iconRes = DesignsystemR.drawable.ic_close,
            contentDescription = stringResource(R.string.create_additional_delete_description, index + 1),
            onClick = onRemove,
            size = ManyakTheme.sizes.controlSmall,
            iconSize = ManyakTheme.sizes.iconSmall,
            shape = ManyakTheme.shapes.menuItem,
            tint = ManyakTheme.colors.textSubtlest,
        )
    }
}
