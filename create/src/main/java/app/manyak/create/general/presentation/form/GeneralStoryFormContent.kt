package app.manyak.create.general.presentation.form

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import app.manyak.create.general.entity.GeneralFieldError
import app.manyak.create.general.entity.GeneralFieldTarget
import app.manyak.create.general.entity.GeneralStoryForm
import app.manyak.create.general.entity.GeneralTab
import app.manyak.create.general.presentation.GeneralGenreSearchState
import app.manyak.create.presentation.component.FunnelPrimaryButton
import app.manyak.designsystem.component.ManyakNeutralButton
import app.manyak.designsystem.theme.ManyakTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
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
    LaunchedEffect(pendingTarget) {
        val target = pendingTarget ?: return@LaunchedEffect
        withFrameNanos { }
        delay(350)
        positions[target]?.let { y ->
            scroll.animateScrollTo((scroll.value + y - viewportCenter).roundToInt().coerceAtLeast(0))
        }
        pendingTarget = null
    }
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
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned {
                    viewportCenter =
                        it.positionInRoot().y + it.size.height / 2f
                }.verticalScroll(scroll)
                .padding(ManyakTheme.spacing.gutter),
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
                    { pendingTarget = it },
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
    deleteId?.let { id -> GeneralDeleteDialog(onDismiss = { deleteId = null }, onDelete = { delete(id) }) }
}

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
    Row(
        Modifier.fillMaxWidth().padding(ManyakTheme.spacing.gutter),
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
