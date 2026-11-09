package app.manyak.my.persona.presentation

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.common.entity.persona.Persona
import app.manyak.designsystem.component.LoadFailedContent
import app.manyak.designsystem.component.ManyakAddButton
import app.manyak.designsystem.component.ManyakDestructiveDialog
import app.manyak.designsystem.component.ManyakMoreButton
import app.manyak.designsystem.component.ManyakOptionItem
import app.manyak.designsystem.component.ManyakOptionsSheet
import app.manyak.designsystem.component.ManyakOptionsSheetHeader
import app.manyak.designsystem.component.ManyakProgressIndicator
import app.manyak.designsystem.component.moreButtonTitleAlignment
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.my.persona.domain.parsePersonaDescription
import app.manyak.my.persona.domain.personaFeatureSummary
import app.manyak.my.presentation.component.MyDetailHeader
import app.manyak.common.R as CommonR
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.my.R as MyR

/** 마이의 페르소나 관리. 줄을 누르면 특징 전체를 펼치고, 더보기에서 수정하거나 삭제한다. */
@Composable
fun PersonaListScreen(
    onBack: () -> Unit,
    onOpenCreate: () -> Unit,
    onOpenEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PersonaListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val currentOnOpenCreate by rememberUpdatedState(onOpenCreate)
    val currentOnOpenEdit by rememberUpdatedState(onOpenEdit)

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                val message =
                    when (effect) {
                        PersonaListEffect.NavigateToCreate -> {
                            currentOnOpenCreate()
                            null
                        }
                        is PersonaListEffect.NavigateToEdit -> {
                            currentOnOpenEdit(effect.personaId)
                            null
                        }
                        PersonaListEffect.ShowLimitReached -> CommonR.string.persona_limit_reached
                        PersonaListEffect.ShowDeleted -> MyR.string.persona_deleted
                        PersonaListEffect.ShowDeleteFailed -> MyR.string.persona_delete_failed
                    }
                message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
            }
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        MyDetailHeader(titleRes = MyR.string.my_persona_manage, onBack = onBack)
        PersonaListContent(state = state, onIntent = viewModel::onIntent, modifier = Modifier.weight(1f))
    }
    PersonaListDialogs(state = state, onIntent = viewModel::onIntent)
}

@Composable
private fun PersonaListContent(
    state: PersonaListUiState,
    onIntent: (PersonaListIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val personas = state.personas
    when {
        personas == null && state.loadFailed ->
            LoadFailedContent(
                modifier = modifier.fillMaxWidth().padding(ManyakTheme.spacing.gutter),
                message = stringResource(MyR.string.persona_list_load_failed),
                onRetry = { onIntent(PersonaListIntent.Retry) },
            )

        personas == null -> {
            val loading = stringResource(MyR.string.persona_list_loading)
            Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ManyakProgressIndicator(modifier = Modifier.semantics { contentDescription = loading })
            }
        }

        personas.isEmpty() ->
            Column(
                modifier = modifier.fillMaxWidth().padding(horizontal = ManyakTheme.spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(MyR.string.persona_list_empty),
                    style = ManyakTheme.typography.bodyMedium,
                    color = ManyakTheme.colors.textSubtle,
                )
                PersonaAddButton(onClick = { onIntent(PersonaListIntent.Create) })
            }

        else -> PersonaRows(personas = personas, onIntent = onIntent, modifier = modifier)
    }
}

@Composable
private fun PersonaRows(
    personas: List<Persona>,
    onIntent: (PersonaListIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 펼침은 이 화면만 아는 표현 상태라 저장소에 두지 않는다. 회전해도 펼친 줄이 그대로 남게 저장한다.
    var expandedIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(personas, key = Persona::id) { persona ->
            val expanded = persona.id in expandedIds
            PersonaRow(
                persona = persona,
                expanded = expanded,
                onToggle = {
                    expandedIds = if (expanded) expandedIds - persona.id else expandedIds + persona.id
                },
                onOpenOptions = { onIntent(PersonaListIntent.OpenOptions(persona)) },
            )
        }
        item {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ManyakTheme.spacing.gutter)
                        .padding(top = ManyakTheme.spacing.compact, bottom = ManyakTheme.spacing.gutter),
                contentAlignment = Alignment.Center,
            ) {
                PersonaAddButton(onClick = { onIntent(PersonaListIntent.Create) })
            }
        }
    }
}

/**
 * 접힌 줄은 절 제목을 뺀 두 줄 요약을, 펼친 줄은 이름과 특징 원문 전체를 줄바꿈 그대로 보인다.
 *
 * 채팅 카드, 내 스토리 카드와 같은 문법이다. 줄 전체가 펼치기이고, 이름 줄 오른쪽 더보기와 길게 누르기가 같은
 * 옵션 시트를 연다. 요약은 더보기 아래까지 폭을 다 쓴다.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PersonaRow(
    persona: Persona,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenOptions: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val feature = remember(persona.description) { parsePersonaDescription(persona.description).feature }
    val summary = remember(feature) { personaFeatureSummary(feature) }
    val optionsLabel = stringResource(MyR.string.persona_options, persona.name)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    role = Role.Button,
                    onLongClickLabel = optionsLabel,
                    onClick = onToggle,
                    // 길게 누르기는 화면에 드러나지 않는 제스처라 시트가 열리는 순간 손으로도 알린다.
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onOpenOptions()
                    },
                ).padding(horizontal = ManyakTheme.spacing.gutter, vertical = ManyakTheme.spacing.compact),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        val nameStyle = ManyakTheme.typography.bodyLargeStrong
        Row(horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
            Text(
                modifier = Modifier.weight(1f).alignBy(FirstBaseline),
                text = persona.name,
                style = nameStyle,
                color = ManyakTheme.colors.text,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
            )
            ManyakMoreButton(
                contentDescription = optionsLabel,
                onClick = onOpenOptions,
                modifier = moreButtonTitleAlignment(nameStyle),
            )
        }
        Text(
            text = if (expanded) feature else summary,
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.textSubtle,
            maxLines = if (expanded) Int.MAX_VALUE else SUMMARY_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PersonaAddButton(onClick: () -> Unit) {
    ManyakAddButton(label = stringResource(MyR.string.persona_add), enabled = true, onClick = onClick)
}

/** 옵션 시트와 삭제 확인. 시트를 닫은 뒤 확인을 연다. */
@Composable
private fun PersonaListDialogs(
    state: PersonaListUiState,
    onIntent: (PersonaListIntent) -> Unit,
) {
    state.optionsTarget?.let { persona ->
        ManyakOptionsSheet(
            onDismissRequest = { onIntent(PersonaListIntent.CloseOptions) },
            header = {
                ManyakOptionsSheetHeader(kind = stringResource(MyR.string.persona_options_kind), title = persona.name)
            },
        ) {
            ManyakOptionItem(
                iconRes = DesignsystemR.drawable.ic_edit,
                label = stringResource(MyR.string.persona_edit_action),
                onClick = { onIntent(PersonaListIntent.Edit) },
            )
            ManyakOptionItem(
                iconRes = DesignsystemR.drawable.ic_delete,
                label = stringResource(MyR.string.persona_delete_action),
                onClick = { onIntent(PersonaListIntent.RequestDelete) },
                isDestructive = true,
            )
        }
    }
    if (state.deleteTarget != null) {
        ManyakDestructiveDialog(
            title = stringResource(MyR.string.persona_delete_title),
            description = stringResource(MyR.string.persona_delete_description),
            confirmLabel = stringResource(MyR.string.persona_delete_action),
            cancelLabel = stringResource(MyR.string.persona_delete_cancel),
            onConfirm = { onIntent(PersonaListIntent.ConfirmDelete) },
            onDismiss = { onIntent(PersonaListIntent.DismissDeleteDialog) },
            inProgress = state.isDeleting,
            inProgressLabel = stringResource(DesignsystemR.string.delete_in_progress),
        )
    }
}

private const val SUMMARY_MAX_LINES = 2

@Preview(showBackground = true, name = "페르소나 관리")
@Composable
private fun PersonaListPreview() {
    ManyakTheme(darkTheme = false) {
        PersonaListContent(
            state =
                PersonaListUiState(
                    personas =
                        listOf(
                            Persona("a", "윤해솔", "# 주인공\n## 성별\n여성\n## 성격\n겁이 많지만 끝까지 해내는 편이다."),
                            Persona("b", "강도윤", "# 주인공\n## 성별\n남성\n말수가 적다."),
                        ),
                ),
            onIntent = {},
        )
    }
}
