package app.manyak.create.keyword.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.manyak.create.R
import app.manyak.designsystem.component.ManyakTextField
import app.manyak.designsystem.theme.ManyakTheme
import kotlin.math.roundToInt
import app.manyak.designsystem.R as DesignsystemR

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GenreKeywordSection(
    state: CreateKeywordUiState,
    onIntent: (CreateKeywordIntent) -> Unit,
) {
    GenreCombobox(state, onIntent)
    if (state.genrePicker.needsReselection) {
        GenreMenuMessage(stringResource(R.string.create_genre_legacy_notice))
    }
    when {
        state.genrePicker.catalog != null ->
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
                verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
            ) {
                state.genreChips().forEach { tag ->
                    val selected = tag.id in state.selectedGenreTagIds
                    KeywordChip(
                        name = tag.name,
                        selected = selected,
                        enabled = !state.isRestoring && (selected || !state.isAtSelectionCap(KeywordTarget.Genre)),
                        onClick = { onIntent(CreateKeywordIntent.ToggleProvidedTag(KeywordTarget.Genre, tag.id)) },
                    )
                }
            }
        state.genrePicker.catalogFailed -> TagsLoadFailure(onRetry = { onIntent(CreateKeywordIntent.RetryGenres) })
        else -> KeywordChipSkeleton()
    }
}

@Composable
private fun GenreCombobox(
    state: CreateKeywordUiState,
    onIntent: (CreateKeywordIntent) -> Unit,
) {
    val picker = state.genrePicker
    val canPick = !state.isRestoring && picker.catalog != null
    GenreSearchCombobox(
        query = picker.query,
        expanded = picker.expanded,
        showLoading = picker.showLoading,
        searching = picker.searching,
        failed = picker.searchFailed || picker.catalogFailed,
        options =
            picker.results.map { tag ->
                val selected = tag.id in state.selectedGenreTagIds
                GenreMenuOption(
                    name = tag.name,
                    selected = selected,
                    enabled = canPick && (selected || !state.isAtSelectionCap(KeywordTarget.Genre)),
                )
            },
        onQueryChange = { onIntent(CreateKeywordIntent.SearchGenres(it)) },
        onExpandedChange = { onIntent(CreateKeywordIntent.ExpandGenres(it)) },
        onRetry = { onIntent(CreateKeywordIntent.RetryGenres) },
        onSelect = { index -> onIntent(CreateKeywordIntent.SelectGenre(picker.results[index].id)) },
    )
}

/** 장르 검색 결과 한 줄. 간편 제작과 일반 제작이 서로 다른 장르 모델을 이 모양으로 옮겨 같은 목록을 그린다. */
internal data class GenreMenuOption(
    val name: String,
    val selected: Boolean,
    val enabled: Boolean,
)

/** 제공 장르 검색 입력과 결과 팝업. 상태는 부르는 쪽이 소유한다. */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
@Composable
internal fun GenreSearchCombobox(
    query: String,
    expanded: Boolean,
    showLoading: Boolean,
    searching: Boolean,
    failed: Boolean,
    options: List<GenreMenuOption>,
    onQueryChange: (String) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var anchorBounds by remember { mutableStateOf(IntRect.Zero) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val close: () -> Unit = {
        onExpandedChange(false)
        focusManager.clearFocus()
        keyboard?.hide()
    }
    val dropdownLabel =
        stringResource(
            if (expanded) R.string.create_genre_close_list else R.string.create_genre_open_list,
        )
    BackHandler(enabled = expanded, onBack = close)
    ExposedDropdownMenuBox(
        modifier = modifier,
        expanded = expanded,
        onExpandedChange = { if (enabled) onExpandedChange(it) },
    ) {
        ManyakTextField(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { anchorBounds = it.windowIntBounds() }
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled),
            value = query,
            onValueChange = onQueryChange,
            enabled = enabled,
            placeholder = stringResource(R.string.create_genre_search_placeholder),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { close() }),
            leading = { GenreInputIcon(R.drawable.ic_search) },
            trailing = {
                GenreInputIcon(
                    R.drawable.ic_chevron_expand_y,
                    Modifier
                        .semantics {
                            contentDescription = dropdownLabel
                        }.menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable, enabled),
                )
            },
        )
        if (expanded) {
            GenreDropdownMenu(anchorBounds, close) {
                GenreMenuContents(showLoading, searching, failed, options, onRetry) { index ->
                    onSelect(index)
                    focusManager.clearFocus()
                    keyboard?.hide()
                }
            }
        }
    }
}

@Composable
private fun GenreDropdownMenu(
    anchorBounds: IntRect,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val gap = with(density) { ManyakTheme.spacing.inline.roundToPx() }
    val bottomInset = maxOf(WindowInsets.ime.getBottom(density), WindowInsets.safeDrawing.getBottom(density))
    val available =
        (LocalWindowInfo.current.containerSize.height - bottomInset - anchorBounds.bottom - gap)
            .coerceAtLeast(
                0,
            )
    val height = minOf(ManyakTheme.sizes.genreMenuMaxHeight, with(density) { available.toDp() })
    val position =
        remember(gap) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ) = IntOffset(anchorBounds.left, anchorBounds.bottom + gap)
            }
        }
    Popup(
        popupPositionProvider = position,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false),
    ) {
        Surface(
            modifier = Modifier.width(with(density) { anchorBounds.width.toDp() }).heightIn(max = height),
            shape = ManyakTheme.shapes.control,
            color = ManyakTheme.colors.surfaceRaised,
            border = BorderStroke(ManyakTheme.sizes.inputBorderWidth, ManyakTheme.colors.border),
            shadowElevation = ManyakTheme.sizes.selectMenuElevation,
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(ManyakTheme.spacing.inline),
                verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
                content = content,
            )
        }
    }
}

@Composable
private fun GenreMenuContents(
    showLoading: Boolean,
    searching: Boolean,
    failed: Boolean,
    options: List<GenreMenuOption>,
    onRetry: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    when {
        showLoading -> GenreMenuMessage(stringResource(R.string.create_genre_search_loading))
        !searching && failed ->
            DropdownMenuItem(
                text = { GenreMenuMessage(stringResource(R.string.create_genre_search_retry)) },
                onClick = onRetry,
            )
        options.isEmpty() ->
            if (searching) {
                Spacer(Modifier.height(ManyakTheme.sizes.input))
            } else {
                GenreMenuMessage(stringResource(R.string.create_genre_search_empty))
            }
        else -> GenreMenuItems(options, onSelect)
    }
}

@Composable
private fun GenreMenuItems(
    options: List<GenreMenuOption>,
    onSelect: (Int) -> Unit,
) {
    options.forEachIndexed { index, option ->
        val background =
            if (option.selected) ManyakTheme.colors.backgroundNeutral else ManyakTheme.colors.surfaceRaised
        DropdownMenuItem(
            modifier =
                Modifier
                    .height(ManyakTheme.sizes.input)
                    .background(
                        background,
                        ManyakTheme.shapes.menuItem,
                    ).semantics { this.selected = option.selected },
            text = {
                Text(
                    text = option.name,
                    style = ManyakTheme.typography.bodyMedium,
                    color = if (option.enabled) ManyakTheme.colors.text else ManyakTheme.colors.textDisabled,
                )
            },
            trailingIcon =
                if (option.selected) {
                    { GenreInputIcon(DesignsystemR.drawable.ic_check) }
                } else {
                    null
                },
            enabled = option.enabled,
            onClick = { onSelect(index) },
        )
    }
}

@Composable
private fun GenreInputIcon(
    resource: Int,
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = painterResource(resource),
        contentDescription = null,
        modifier = modifier.size(ManyakTheme.sizes.iconSmall),
        tint = ManyakTheme.colors.textSubtle,
    )
}

@Composable
internal fun GenreMenuMessage(text: String) {
    Text(
        modifier = Modifier.padding(ManyakTheme.spacing.controlHorizontal),
        text = text,
        style = ManyakTheme.typography.bodySmall,
        color = ManyakTheme.colors.textSubtle,
    )
}

private fun LayoutCoordinates.windowIntBounds(): IntRect {
    val bounds = boundsInWindow()
    return IntRect(
        bounds.left.roundToInt(),
        bounds.top.roundToInt(),
        bounds.right.roundToInt(),
        bounds.bottom.roundToInt(),
    )
}
