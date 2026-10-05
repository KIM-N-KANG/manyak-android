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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenreCombobox(
    state: CreateKeywordUiState,
    onIntent: (CreateKeywordIntent) -> Unit,
) {
    val picker = state.genrePicker
    var anchorBounds by remember { mutableStateOf(IntRect.Zero) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val close: () -> Unit = {
        onIntent(CreateKeywordIntent.ExpandGenres(false))
        focusManager.clearFocus()
        keyboard?.hide()
    }
    val dropdownLabel =
        stringResource(
            if (picker.expanded) R.string.create_genre_close_list else R.string.create_genre_open_list,
        )
    BackHandler(enabled = picker.expanded, onBack = close)
    ExposedDropdownMenuBox(
        expanded = picker.expanded,
        onExpandedChange = { onIntent(CreateKeywordIntent.ExpandGenres(it)) },
    ) {
        ManyakTextField(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInWindow()
                        anchorBounds =
                            IntRect(
                                bounds.left.roundToInt(),
                                bounds.top.roundToInt(),
                                bounds.right.roundToInt(),
                                bounds.bottom.roundToInt(),
                            )
                    }.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
            value = picker.query,
            onValueChange = { onIntent(CreateKeywordIntent.SearchGenres(it)) },
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
                        }.menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable),
                )
            },
        )
        if (picker.expanded) {
            GenreDropdownMenu(anchorBounds, close) {
                GenreMenuContents(state, onIntent) { id ->
                    onIntent(CreateKeywordIntent.SelectGenre(id))
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
    state: CreateKeywordUiState,
    onIntent: (CreateKeywordIntent) -> Unit,
    onSelect: (Long) -> Unit,
) {
    val picker = state.genrePicker
    when {
        picker.showLoading -> GenreMenuMessage(stringResource(R.string.create_genre_search_loading))
        !picker.searching && (picker.searchFailed || picker.catalogFailed) ->
            DropdownMenuItem(
                text = { GenreMenuMessage(stringResource(R.string.create_genre_search_retry)) },
                onClick = { onIntent(CreateKeywordIntent.RetryGenres) },
            )
        picker.results.isEmpty() ->
            if (picker.searching) {
                Spacer(Modifier.height(ManyakTheme.sizes.input))
            } else {
                GenreMenuMessage(stringResource(R.string.create_genre_search_empty))
            }
        else -> GenreMenuItems(state, onSelect)
    }
}

@Composable
private fun GenreMenuItems(
    state: CreateKeywordUiState,
    onSelect: (Long) -> Unit,
) {
    state.genrePicker.results.forEach { tag ->
        val selected = tag.id in state.selectedGenreTagIds
        val enabled =
            !state.isRestoring &&
                state.genrePicker.catalog != null &&
                (selected || !state.isAtSelectionCap(KeywordTarget.Genre))
        val background =
            if (selected) ManyakTheme.colors.backgroundNeutral else ManyakTheme.colors.surfaceRaised
        DropdownMenuItem(
            modifier =
                Modifier
                    .height(ManyakTheme.sizes.input)
                    .background(
                        background,
                        ManyakTheme.shapes.menuItem,
                    ).semantics { this.selected = selected },
            text = {
                Text(
                    text = tag.name,
                    style = ManyakTheme.typography.bodyMedium,
                    color = if (enabled) ManyakTheme.colors.text else ManyakTheme.colors.textDisabled,
                )
            },
            trailingIcon =
                if (selected) {
                    { GenreInputIcon(DesignsystemR.drawable.ic_check) }
                } else {
                    null
                },
            enabled = enabled,
            onClick = { onSelect(tag.id) },
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
private fun GenreMenuMessage(text: String) {
    Text(
        modifier = Modifier.padding(ManyakTheme.spacing.controlHorizontal),
        text = text,
        style = ManyakTheme.typography.bodySmall,
        color = ManyakTheme.colors.textSubtle,
    )
}
