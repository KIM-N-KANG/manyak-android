package app.manyak.story.detail.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.manyak.common.entity.persona.Persona
import app.manyak.designsystem.component.ManyakBottomSheet
import app.manyak.designsystem.text.PassageSegment
import app.manyak.designsystem.text.parsePassageSegments
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.story.entity.StoryStartSetting
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.story.R as StoryR

/** 시작 시트에서 펼칠 수 있는 행. 한 번에 하나만 펼친다. */
private enum class StartSheetRow { PERSONA, START_SETTING }

/**
 * 채팅을 만들기 전에 고른 페르소나와 시작 상황을 보여 주고 그 자리에서 바꾸게 하는 시트.
 * 고른 값은 상세 본문의 셀렉트와 같은 상태라 여기서 바꾸면 본문에도 보인다.
 *
 * @param selectedPersona null 이면 기본 주인공이다.
 * @param selectedStartSetting 시작 설정이 없는 스토리면 null 이고 시작 상황 행을 두지 않는다.
 * @param isStarting 채팅 생성 중. 시트를 닫지 않고 시작 버튼을 진행 표시로 바꾼다.
 */
@Composable
@Suppress("LongParameterList")
internal fun ChatStartSheet(
    personas: List<Persona>,
    selectedPersona: Persona?,
    startSettings: List<StoryStartSetting>,
    selectedStartSetting: StoryStartSetting?,
    isStarting: Boolean,
    onSelectPersona: (String?) -> Unit,
    onCreatePersona: () -> Unit,
    onSelectStartSetting: (String) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf<StartSheetRow?>(null) }
    val toggle = { row: StartSheetRow -> expanded = if (expanded == row) null else row }
    val defaultPersonaName = stringResource(StoryR.string.story_detail_persona_default)

    ManyakBottomSheet(
        onDismissRequest = onDismiss,
        dismissEnabled = !isStarting,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.section),
    ) {
        StartSheetHeader()
        // 회색 카드 하나를 시트 바탕색 선으로 나눠 두 칸으로 보이게 한다.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(ManyakTheme.shapes.overlay)
                    .background(ManyakTheme.colors.backgroundNeutralRaised),
        ) {
            ChoiceRow(
                label = stringResource(StoryR.string.story_detail_persona),
                value = selectedPersona?.name ?: defaultPersonaName,
                expanded = expanded == StartSheetRow.PERSONA,
                onToggle = { toggle(StartSheetRow.PERSONA) },
                options =
                    listOf(ChoiceOption<String?>(null, defaultPersonaName)) +
                        personas.map { persona -> ChoiceOption(persona.id, persona.name) },
                selected = selectedPersona?.id,
                onSelect = { id ->
                    expanded = null
                    onSelectPersona(id)
                },
                footer = { CreatePersonaOption(onClick = onCreatePersona) },
            )
            if (selectedStartSetting != null) {
                HorizontalDivider(thickness = 1.dp, color = ManyakTheme.colors.surfaceRaised)
                ChoiceRow(
                    label = stringResource(StoryR.string.story_detail_start_sheet_start_setting),
                    value = selectedStartSetting.name,
                    detail = remember(selectedStartSetting) { situationPreview(selectedStartSetting.startSituation) },
                    expanded = expanded == StartSheetRow.START_SETTING,
                    expandable = startSettings.size > 1,
                    onToggle = { toggle(StartSheetRow.START_SETTING) },
                    options = startSettings.map { setting -> ChoiceOption(setting.id, setting.name) },
                    selected = selectedStartSetting.id,
                    onSelect = { id ->
                        expanded = null
                        if (id != selectedStartSetting.id) onSelectStartSetting(id)
                    },
                )
            }
        }
        StartChatButton(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(StoryR.string.story_detail_start_sheet_start),
            isStarting = isStarting,
            onClick = onStart,
        )
    }
}

@Composable
private fun StartSheetHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
        Text(
            text = stringResource(StoryR.string.story_detail_start_sheet_title),
            style = ManyakTheme.typography.titleLarge,
            color = ManyakTheme.colors.text,
        )
        Text(
            text = stringResource(StoryR.string.story_detail_start_sheet_description),
            style = ManyakTheme.typography.bodyLarge,
            color = ManyakTheme.colors.textSubtle,
        )
    }
}

private data class ChoiceOption<T>(
    val value: T,
    val label: String,
)

/** 상황 설명의 글만 이어 붙인다. 장면 이미지 마커는 미리보기에 남기지 않는다. */
private fun situationPreview(startSituation: String): String =
    parsePassageSegments(startSituation)
        .filterIsInstance<PassageSegment.Text>()
        .joinToString(" ") { segment -> segment.content }
        .replace(WhitespaceRun, " ")
        .trim()

private val WhitespaceRun = Regex("""\s+""")

/**
 * 라벨과 값을 보이다가 누르면 값 대신 선택지 목록을 펼치는 행. 펼친 목록이 길면 그 안에서 스크롤하고,
 * [footer] 는 목록 아래 구분선 밑에 스크롤과 상관없이 남는다.
 *
 * @param detail 접힌 상태에서 값 아래 두 줄까지 보이는 설명.
 * @param expandable 거짓이면 고를 값이 하나뿐이라 펼치지 않는다.
 */
@Composable
@Suppress("LongParameterList")
private fun <T> ChoiceRow(
    label: String,
    value: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    options: List<ChoiceOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    detail: String? = null,
    expandable: Boolean = true,
    footer: (@Composable () -> Unit)? = null,
) {
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (expandable) Modifier.expandToggle(expanded, onToggle) else Modifier)
                    .padding(ManyakTheme.spacing.gutter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component),
        ) {
            ChoiceSummary(
                modifier = Modifier.weight(1f),
                label = label,
                value = value,
                detail = detail,
                expanded = expanded,
            )
            if (expandable) ExpandChevron(expanded = expanded)
        }
        AnimatedVisibility(visible = expanded, enter = ExpandIn, exit = CollapseOut) {
            ChoiceList(options = options, selected = selected, onSelect = onSelect, footer = footer)
        }
    }
}

/** 행 전체가 펼침 단추다. 보조기술에는 펼치기와 접기 동작으로 알린다. */
private fun Modifier.expandToggle(
    expanded: Boolean,
    onToggle: () -> Unit,
): Modifier =
    clickable(role = Role.Button, onClick = onToggle).semantics {
        val action = {
            onToggle()
            true
        }
        if (expanded) collapse(action = action) else expand(action = action)
    }

/** 펼치면 값과 설명이 접혀 들어가고 그 자리를 선택지 목록이 대신한다. */
@Composable
private fun ChoiceSummary(
    label: String,
    value: String,
    detail: String?,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(text = label, style = ManyakTheme.typography.bodyMedium, color = ManyakTheme.colors.textSubtle)
        AnimatedVisibility(visible = !expanded, enter = ExpandIn, exit = CollapseOut) {
            Column(
                modifier = Modifier.padding(top = ManyakTheme.spacing.inline),
                verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
            ) {
                Text(text = value, style = ManyakTheme.typography.bodyLargeStrong, color = ManyakTheme.colors.text)
                if (!detail.isNullOrEmpty()) {
                    Text(
                        text = detail,
                        style = ManyakTheme.typography.bodyMedium,
                        color = ManyakTheme.colors.textSubtle,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandChevron(expanded: Boolean) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) HALF_TURN else 0f,
        animationSpec = ExpandSpring,
        label = "choiceChevron",
    )
    Icon(
        modifier = Modifier.size(ManyakTheme.sizes.icon).rotate(rotation),
        painter = painterResource(DesignsystemR.drawable.ic_angle_down),
        // 펼침 상태는 행의 시맨틱이 알린다.
        contentDescription = null,
        tint = ManyakTheme.colors.textSubtle,
    )
}

@Composable
private fun <T> ChoiceList(
    options: List<ChoiceOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    footer: (@Composable () -> Unit)?,
) {
    // 고른 항목이 잘린 목록 아래에 숨지 않도록 그 위 두 칸부터 보이며 연다. 5줄 반의 가운데쯤이다.
    val checkedIndex = options.indexOfFirst { option -> option.value == selected }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (checkedIndex - 2).coerceAtLeast(0))
    // 목록 끝에서 남은 끌기와 플링을 여기서 삼킨다. 시트로 넘어가면 목록을 맨 위로 끌어내리던 손길에 시트가
    // 닫힌다. 스크롤할 것이 없는 짧은 목록은 다른 시트처럼 끌어서 닫을 수 있게 넘긴다.
    val containOverscroll =
        remember(listState) {
            object : NestedScrollConnection {
                private val scrollable get() = listState.canScrollForward || listState.canScrollBackward

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset = if (scrollable) available else Offset.Zero

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity,
                ): Velocity = if (scrollable) available else Velocity.Zero
            }
        }
    val bottomPadding = if (footer != null) ManyakTheme.spacing.inline else ManyakTheme.spacing.compact
    Column(modifier = Modifier.padding(horizontal = ManyakTheme.spacing.compact)) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = ManyakTheme.sizes.choiceListMaxHeight)
                    .nestedScroll(containOverscroll),
            state = listState,
            contentPadding = PaddingValues(bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
        ) {
            items(options) { option ->
                ChoiceItem(
                    label = option.label,
                    checked = option.value == selected,
                    onClick = { onSelect(option.value) },
                )
            }
        }
        if (footer != null) {
            Column(
                modifier = Modifier.padding(bottom = ManyakTheme.spacing.compact),
                verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
            ) {
                // 선택지 글자 시작선에 맞춰 짧게 긋는다.
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = ManyakTheme.spacing.compact),
                    thickness = 1.dp,
                    color = ManyakTheme.colors.surfaceRaised,
                )
                footer()
            }
        }
    }
}

@Composable
private fun ChoiceItem(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    val colors = ManyakTheme.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ManyakTheme.sizes.control)
                .clip(ManyakTheme.shapes.card)
                .background(if (checked) colors.overlayPressed else colors.backgroundNeutralRaised)
                .selectable(selected = checked, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = ManyakTheme.spacing.compact, vertical = ManyakTheme.spacing.component),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component),
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            style = ManyakTheme.typography.bodyLarge,
            color = colors.text,
        )
        if (checked) {
            Icon(
                modifier = Modifier.size(ManyakTheme.sizes.icon),
                painter = painterResource(DesignsystemR.drawable.ic_check),
                // 선택 여부는 항목의 시맨틱이 이미 알린다.
                contentDescription = null,
                tint = colors.text,
            )
        }
    }
}

/** 고를 수 있는 값이 아니라 생성 화면으로 가는 동작이라 선택 채움과 체크를 두지 않고 흐리게 둔다. */
@Composable
private fun CreatePersonaOption(onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ManyakTheme.sizes.control)
                .clip(ManyakTheme.shapes.card)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = ManyakTheme.spacing.compact, vertical = ManyakTheme.spacing.component),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component),
    ) {
        Icon(
            modifier = Modifier.size(ManyakTheme.sizes.icon),
            painter = painterResource(DesignsystemR.drawable.ic_add),
            contentDescription = null,
            tint = ManyakTheme.colors.textSubtle,
        )
        Text(
            text = stringResource(StoryR.string.story_detail_persona_create),
            style = ManyakTheme.typography.bodyLarge,
            color = ManyakTheme.colors.textSubtle,
        )
    }
}

private const val HALF_TURN = 180f

/** 셀렉트의 화살표와 같은 스프링이라 펼침과 화살표가 한 움직임으로 읽힌다. */
private val ExpandSpring =
    spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

private val ExpandIn =
    expandVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) +
        fadeIn(ExpandSpring)

private val CollapseOut =
    shrinkVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) +
        fadeOut(ExpandSpring)

private val PreviewStartSetting =
    StoryStartSetting(id = "a", name = "첫 표행의 아침", startSituation = "문은 안에서 잠겨 있다.", endings = emptyList())

@Preview(name = "채팅 시작 시트")
@Composable
private fun ChatStartSheetPreview() {
    ManyakTheme(darkTheme = false) {
        ChatStartSheet(
            personas = listOf(Persona("p", "윤해솔", "")),
            selectedPersona = null,
            startSettings = listOf(PreviewStartSetting, PreviewStartSetting.copy(id = "b", name = "시계공의 작업실")),
            selectedStartSetting = PreviewStartSetting,
            isStarting = false,
            onSelectPersona = {},
            onCreatePersona = {},
            onSelectStartSetting = {},
            onStart = {},
            onDismiss = {},
        )
    }
}
