package app.manyak.create.general.presentation.form

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import app.manyak.create.general.entity.GeneralField
import app.manyak.create.general.entity.GeneralFieldTarget
import app.manyak.create.general.entity.GeneralTab
import app.manyak.create.general.presentation.GeneralGenreSearchState
import app.manyak.create.keyword.presentation.KeywordChip
import app.manyak.create.keyword.presentation.KeywordChipSkeleton
import app.manyak.create.keyword.presentation.TagsLoadFailure
import app.manyak.create.presentation.component.KeywordSectionLabel
import app.manyak.designsystem.component.ManyakTextButton
import app.manyak.designsystem.component.ManyakTextField
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.create.R as CreateR
import app.manyak.designsystem.R as DesignR

@Suppress("LongParameterList")
internal data class GeneralGenreUi(
    val all: List<String>,
    val featured: List<String>,
    val catalogFailed: Boolean,
    val search: GeneralGenreSearchState,
    val query: (String) -> Unit,
    val expand: (Boolean) -> Unit,
    val retry: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GeneralFormScope.GenreField(genres: GeneralGenreUi) {
    var extraChips by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val target = GeneralFieldTarget(GeneralTab.PUBLISH, GeneralField.GENRES)
    Column(anchor(target), verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
        KeywordSectionLabel(stringResource(CreateR.string.general_genres), required = true)
        GeneralGenreSearchInput(genres) { genre ->
            if (genre !in genres.featured && genre !in extraChips) extraChips = ArrayList(extraChips + genre)
            toggleGenre(genre)
        }
        when {
            genres.catalogFailed -> TagsLoadFailure(genres.retry)
            genres.all.isEmpty() -> KeywordChipSkeleton()
            else ->
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
                    verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
                ) {
                    (genres.featured + extraChips + form.genres).distinct().forEach { genre ->
                        val selected = genre in form.genres
                        KeywordChip(
                            genre,
                            selected,
                            enabled && (selected || form.genres.size < 8),
                            onClick = { toggleGenre(genre) },
                        )
                    }
                }
        }
        GeneralFieldHint(message(target), stringResource(CreateR.string.general_genres_hint))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneralFormScope.GeneralGenreSearchInput(
    genres: GeneralGenreUi,
    select: (String) -> Unit,
) {
    val search = genres.search
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val close = {
        genres.expand(false)
        focus.clearFocus()
        keyboard?.hide()
        Unit
    }
    BackHandler(search.expanded, close)
    ExposedDropdownMenuBox(expanded = search.expanded, onExpandedChange = { if (enabled) genres.expand(it) }) {
        ManyakTextField(
            value = search.query,
            onValueChange = genres.query,
            enabled = enabled,
            placeholder = stringResource(CreateR.string.create_genre_search_placeholder),
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { close() }),
            leading = {
                Icon(
                    painterResource(CreateR.drawable.ic_search),
                    null,
                    Modifier.size(ManyakTheme.sizes.iconSmall),
                    tint = ManyakTheme.colors.textSubtle,
                )
            },
            trailing = {
                Icon(
                    painterResource(CreateR.drawable.ic_chevron_expand_y),
                    stringResource(CreateR.string.create_genre_open_list),
                    Modifier
                        .size(
                            ManyakTheme.sizes.iconSmall,
                        ).menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable, enabled),
                    tint = ManyakTheme.colors.textSubtle,
                )
            },
        )
        ExposedDropdownMenu(
            expanded = search.expanded,
            onDismissRequest = close,
            containerColor = ManyakTheme.colors.surfaceRaised,
        ) {
            GeneralGenreResults(genres) { genre ->
                select(genre)
                genres.query("")
                close()
            }
        }
    }
}

@Composable
private fun GeneralFormScope.GeneralGenreResults(
    genres: GeneralGenreUi,
    select: (String) -> Unit,
) {
    val search = genres.search
    when {
        search.failed ->
            ManyakTextButton(onClick = genres.retry) {
                Text(stringResource(CreateR.string.create_genre_search_retry))
            }
        search.showLoading ->
            Text(
                stringResource(CreateR.string.create_genre_search_loading),
                style = ManyakTheme.typography.bodyMedium,
            )
        search.searching && search.results.isEmpty() -> Spacer(Modifier.height(ManyakTheme.sizes.input))
        search.results.isEmpty() ->
            Text(
                stringResource(CreateR.string.create_genre_search_empty),
                style = ManyakTheme.typography.bodyMedium,
            )
        else ->
            search.results.forEach { genre ->
                val selected = genre in form.genres
                DropdownMenuItem(
                    text = { Text(genre, style = ManyakTheme.typography.bodyMedium, color = ManyakTheme.colors.text) },
                    enabled = enabled && (selected || form.genres.size < 8),
                    onClick = { select(genre) },
                    trailingIcon = {
                        if (selected) {
                            Icon(
                                painterResource(DesignR.drawable.ic_check),
                                null,
                                Modifier.size(ManyakTheme.sizes.iconSmall),
                                tint = ManyakTheme.colors.text,
                            )
                        }
                    },
                )
            }
    }
}

private fun GeneralFormScope.toggleGenre(genre: String) {
    onChange(form.copy(genres = if (genre in form.genres) form.genres - genre else form.genres + genre))
}
