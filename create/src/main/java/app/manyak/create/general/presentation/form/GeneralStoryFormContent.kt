package app.manyak.create.general.presentation.form

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.manyak.create.general.entity.GeneralFieldError
import app.manyak.create.general.entity.GeneralFieldTarget
import app.manyak.create.general.entity.GeneralStoryForm
import app.manyak.create.general.entity.GeneralTab
import app.manyak.create.general.presentation.GeneralGenreSearchState
import app.manyak.create.presentation.component.FunnelPrimaryButton
import app.manyak.create.presentation.component.scrollToCenter
import app.manyak.designsystem.component.ManyakDestructiveDialog
import app.manyak.designsystem.component.ManyakNeutralButton
import app.manyak.designsystem.component.ScrollEdgeFade
import app.manyak.designsystem.theme.ManyakTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import app.manyak.create.R as CreateR

@Suppress("LongMethod")
@Composable
internal fun GeneralStoryFormContent(
    form: GeneralStoryForm,
    errors: List<GeneralFieldError>,
    submitLabel: String,
    onFormChange: (GeneralStoryForm) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    serverErrors: Map<GeneralFieldTarget, String> = emptyMap(),
    genres: List<String> = emptyList(),
    enabled: Boolean = true,
    tabsEnabled: Boolean = true,
    submitting: Boolean = false,
    onFieldBlur: (GeneralFieldTarget) -> Unit = {},
    validationRequest: Int = 0,
    onUploadImage: (GeneralFieldTarget, String) -> Unit = { _, _ -> },
    uploadingTargets: Set<GeneralFieldTarget> = emptySet(),
    onPickerActiveChanged: (Boolean) -> Unit = {},
    genreSearch: GeneralGenreSearchState = GeneralGenreSearchState(),
    featuredGenres: List<String> = emptyList(),
    genreCatalogFailed: Boolean = false,
    onGenreQuery: (String) -> Unit = {},
    onGenreExpanded: (Boolean) -> Unit = {},
    onRetryGenres: () -> Unit = {},
) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedStart by rememberSaveable { mutableStateOf<String?>(null) }
    var collapsed by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingTarget by remember { mutableStateOf<GeneralFieldTarget?>(null) }
    var addedTarget by remember { mutableStateOf<GeneralFieldTarget?>(null) }
    var seenValidation by rememberSaveable { mutableIntStateOf(-1) }
    var viewportCenter by remember { mutableStateOf(0f) }
    val positions = remember { mutableMapOf<GeneralFieldTarget, Float>() }
    val scroll = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val contentState = rememberSaveableStateHolder()
    val fields =
        GeneralFormScope(
            form,
            onFormChange,
            errors,
            serverErrors,
            enabled,
            onFieldBlur,
            positions,
            onUploadImage,
            uploadingTargets,
            onPickerActiveChanged,
        )
    val changeTab: (Int) -> Unit = {
        focusManager.clearFocus()
        tabIndex = it
    }
    val toggle: (String) -> Unit = { id ->
        collapsed =
            ArrayList(if (id in collapsed) collapsed - id else collapsed + id)
    }
    val delete: (String) -> Unit = { id ->
        val index = form.startSettings.indexOfFirst { it.id == id }
        if (index > 0 && selectedStart == id) selectedStart = form.startSettings[index - 1].id
        onFormChange(removeGeneralItem(form, id))
        deleteId = null
    }
    val requestDelete: (String, Boolean) -> Unit = { id, hasInput -> if (hasInput) deleteId = id else delete(id) }
    LaunchedEffect(validationRequest) {
        if (seenValidation != validationRequest) {
            seenValidation = validationRequest
            val targets = errors.map { it.target } + serverErrors.keys
            val first = targets.minByOrNull { it.tab.ordinal }
            if (first != null) {
                focusManager.clearFocus()
                collapsed = ArrayList(collapsed - targets.mapNotNull { it.itemId }.toSet())
                first.startId?.let { selectedStart = it }
                tabIndex = first.tab.ordinal
                pendingTarget = first
            }
        }
    }
    ResetScrollOnTabChange(tabIndex, scroll)
    ScrollToTarget(pendingTarget, positions, scroll, { viewportCenter }, waitForLayout = false) { pendingTarget = null }
    ScrollToTarget(addedTarget, positions, scroll, { viewportCenter }, waitForLayout = true) { addedTarget = null }
    Column(modifier.fillMaxSize()) {
        GeneralFormTabs(
            tabIndex,
            errors
                .map {
                    it.target.tab
                }.toSet() + serverErrors.keys.map { it.tab },
            tabsEnabled,
            changeTab,
        )
        // 스크롤 본문이 푸터 경계에서 딱 잘리므로 바닥에 페이드를 겹친다.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned {
                        viewportCenter =
                            it.positionInRoot().y + it.size.height / 2f
                    }.verticalScroll(scroll)
                    .padding(GeneralTab.entries[tabIndex].contentPadding()),
            ) {
                contentState.SaveableStateProvider(tabIndex) {
                    GeneralFormBody(
                        fields,
                        GeneralTab.entries[tabIndex],
                        selectedStart,
                        { selectedStart = it },
                        collapsed,
                        toggle,
                        requestDelete,
                        { addedTarget = it },
                        GeneralGenreUi(
                            genres,
                            featuredGenres,
                            genreCatalogFailed,
                            genreSearch,
                            onGenreQuery,
                            onGenreExpanded,
                            onRetryGenres,
                        ),
                    )
                }
            }
            ScrollEdgeFade(Modifier.align(Alignment.BottomCenter))
        }
        if (WindowInsets.ime.getBottom(LocalDensity.current) == 0) {
            GeneralFormFooter(
                tabIndex,
                submitLabel,
                tabsEnabled,
                enabled && uploadingTargets.isEmpty(),
                submitting,
                changeTab,
            ) {
                focusManager.clearFocus()
                onSubmit()
            }
        }
    }
    deleteId?.let { id ->
        ManyakDestructiveDialog(
            title = stringResource(CreateR.string.create_remove_input_title),
            description = stringResource(CreateR.string.create_remove_input_description),
            confirmLabel = stringResource(CreateR.string.create_remove_input_confirm),
            cancelLabel = stringResource(CreateR.string.create_remove_input_cancel),
            onConfirm = { delete(id) },
            onDismiss = { deleteId = null },
        )
    }
}

/**
 * [target] 칸을 화면 가운데로 옮긴다. 검증으로 접힌 항목을 펼치면 펼침이 끝나 칸 위치가 굳은 뒤에 옮긴다.
 * 새로 추가한 항목은 펼친 채 나타나므로([waitForLayout]) 자리를 잡는 대로 옮긴다 — 기다리면 나타난 뒤
 * 멈췄다가 튀어 보인다.
 */
@Composable
private fun ScrollToTarget(
    target: GeneralFieldTarget?,
    positions: Map<GeneralFieldTarget, Float>,
    scroll: ScrollState,
    viewportCenter: () -> Float,
    waitForLayout: Boolean,
    onDone: () -> Unit,
) {
    LaunchedEffect(target) {
        if (target == null) return@LaunchedEffect
        val y =
            if (waitForLayout) {
                withTimeoutOrNull(ADDED_LAYOUT_TIMEOUT_MILLIS) {
                    while (target !in positions) withFrameNanos { }
                    positions.getValue(target)
                }
            } else {
                withFrameNanos { }
                delay(EXPAND_SETTLE_MILLIS)
                positions[target]
            }
        y?.let { scroll.scrollToCenter(it, viewportCenter()) }
        onDone()
    }
}

private const val EXPAND_SETTLE_MILLIS = 350L
private const val ADDED_LAYOUT_TIMEOUT_MILLIS = 500L

@Composable
private fun ResetScrollOnTabChange(
    tabIndex: Int,
    scroll: ScrollState,
) {
    var previous by remember { mutableIntStateOf(tabIndex) }
    LaunchedEffect(tabIndex) {
        if (previous != tabIndex) {
            previous = tabIndex
            scroll.scrollTo(0)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneralFormTabs(
    selected: Int,
    errorTabs: Set<GeneralTab>,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        SecondaryScrollableTabRow(
            selectedTabIndex = selected,
            edgePadding = 0.dp,
            // 탭 폭은 글자 폭에 맞춘다. 기본 최소 폭은 짧은 탭 이름 양옆에 빈 공간을 남긴다.
            minTabWidth = 0.dp,
            containerColor = ManyakTheme.colors.surface,
            contentColor = ManyakTheme.colors.text,
            indicator = {
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(selected, matchContentSize = false),
                    height = ManyakTheme.sizes.selectionBorderWidth,
                    color = ManyakTheme.colors.text,
                )
            },
        ) {
            GeneralTab.entries.forEach { tab ->
                Tab(
                    selected = tab.ordinal == selected,
                    onClick = { onSelect(tab.ordinal) },
                    enabled = enabled,
                    text = {
                        val color =
                            when {
                                tab in errorTabs -> ManyakTheme.colors.textDanger
                                tab.ordinal == selected -> ManyakTheme.colors.text
                                else -> ManyakTheme.colors.textSubtle
                            }
                        Text(
                            buildAnnotatedString {
                                append(stringResource(tab.labelRes()))
                                if (tab !=
                                    GeneralTab.EVENTS
                                ) {
                                    withStyle(SpanStyle(color = ManyakTheme.colors.textDanger)) { append(" *") }
                                }
                            },
                            style = ManyakTheme.typography.labelLarge,
                            color = color,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun GeneralFormFooter(
    tab: Int,
    submitLabel: String,
    tabsEnabled: Boolean,
    enabled: Boolean,
    submitting: Boolean,
    changeTab: (Int) -> Unit,
    submit: () -> Unit,
) {
    // 위 여백은 두지 않는다 — 본문과 버튼 사이는 페이드가 맡는다.
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                horizontal = ManyakTheme.spacing.gutter,
            ).padding(bottom = ManyakTheme.spacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        if (tab > 0) {
            ManyakNeutralButton(stringResource(CreateR.string.general_previous), {
                changeTab(tab - 1)
            }, Modifier.weight(1f), tabsEnabled)
        }
        val last = tab == GeneralTab.entries.lastIndex
        FunnelPrimaryButton(
            label = if (last) submitLabel else stringResource(CreateR.string.general_next),
            enabled = if (last) enabled else tabsEnabled,
            onClick = { if (last) submit() else changeTab(tab + 1) },
            modifier = Modifier.weight(1f),
            loading = last && submitting,
        )
    }
}

private fun removeGeneralItem(
    form: GeneralStoryForm,
    id: String,
): GeneralStoryForm =
    form.copy(
        supporting = form.supporting.filterNot { it.id == id && form.supporting.size > 1 },
        startSettings =
            form.startSettings
                .filterNot { it.id == id && it.id != form.startSettings.firstOrNull()?.id }
                .map { start -> start.copy(endings = start.endings.filterNot { it.id == id }) },
        mainEvents = form.mainEvents.filterNot { it.id == id },
    )

@Preview(showBackground = true)
@Composable
private fun GeneralStoryFormPreview() {
    ManyakTheme {
        GeneralStoryFormContent(
            GeneralStoryForm(),
            emptyList(),
            stringResource(CreateR.string.general_register),
            {},
            {},
        )
    }
}

@Composable
private fun GeneralFormBody(
    fields: GeneralFormScope,
    tab: GeneralTab,
    selectedStart: String?,
    selectStart: (String) -> Unit,
    collapsed: List<String>,
    toggle: (String) -> Unit,
    delete: (String, Boolean) -> Unit,
    added: (GeneralFieldTarget) -> Unit,
    genres: GeneralGenreUi,
) {
    with(fields) {
        when (tab) {
            GeneralTab.PROFILE -> ProfileSection()
            GeneralTab.SETTINGS -> SettingsSection()
            GeneralTab.PROTAGONIST -> ProtagonistSection()
            GeneralTab.SUPPORTING -> SupportingSection(collapsed, toggle, delete, added)
            GeneralTab.START -> StartSection(selectedStart, selectStart, collapsed, toggle, delete, added)
            GeneralTab.EVENTS -> EventsSection(collapsed, toggle, delete, added)
            GeneralTab.PUBLISH -> PublishSection(genres)
        }
    }
}

/** 목록 머리 줄이 화면 끝까지 닿는 탭은 좌우와 위 여백을 항목이 직접 갖는다. */
@Composable
private fun GeneralTab.contentPadding(): PaddingValues =
    when (this) {
        GeneralTab.SUPPORTING, GeneralTab.START, GeneralTab.EVENTS ->
            PaddingValues(bottom = ManyakTheme.spacing.gutter)
        else -> PaddingValues(ManyakTheme.spacing.gutter)
    }
