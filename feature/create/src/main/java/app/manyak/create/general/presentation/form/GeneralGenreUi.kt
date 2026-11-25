package app.manyak.create.general.presentation.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.manyak.create.general.entity.GeneralField
import app.manyak.create.general.entity.GeneralFieldTarget
import app.manyak.create.general.entity.GeneralTab
import app.manyak.create.general.presentation.GeneralGenreSearchState
import app.manyak.create.keyword.presentation.GenreMenuOption
import app.manyak.create.keyword.presentation.GenreSearchCombobox
import app.manyak.create.keyword.presentation.KeywordChip
import app.manyak.create.keyword.presentation.KeywordChipSkeleton
import app.manyak.create.keyword.presentation.TagsLoadFailure
import app.manyak.create.presentation.component.KeywordSectionLabel
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.create.R as CreateR

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
    val atCap = form.genres.size >= GENRE_MAX
    Column(anchor(target), verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
        KeywordSectionLabel(
            stringResource(CreateR.string.general_genres) + " " +
                stringResource(CreateR.string.general_genres_max, GENRE_MAX),
            required = true,
        )
        val search = genres.search
        GenreSearchCombobox(
            query = search.query,
            expanded = search.expanded,
            showLoading = search.showLoading,
            searching = search.searching,
            failed = search.failed,
            options =
                search.results.map { genre ->
                    val selected = genre in form.genres
                    GenreMenuOption(genre, selected, enabled && (selected || !atCap))
                },
            onQueryChange = genres.query,
            onExpandedChange = genres.expand,
            onRetry = genres.retry,
            onSelect = { index ->
                val genre = search.results[index]
                if (genre !in genres.featured && genre !in extraChips) extraChips = ArrayList(extraChips + genre)
                toggleGenre(genre)
                genres.query("")
                genres.expand(false)
            },
            enabled = enabled,
        )
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
                        KeywordChip(genre, selected, enabled && (selected || !atCap), onClick = { toggleGenre(genre) })
                    }
                }
        }
        GeneralFieldHint(message(target), stringResource(CreateR.string.general_genres_hint))
    }
}

private fun GeneralFormScope.toggleGenre(genre: String) {
    onChange(form.copy(genres = if (genre in form.genres) form.genres - genre else form.genres + genre))
}

private const val GENRE_MAX = 8
