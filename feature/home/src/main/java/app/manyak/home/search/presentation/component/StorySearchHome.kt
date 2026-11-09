package app.manyak.home.search.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.manyak.designsystem.component.ManyakIconButton
import app.manyak.designsystem.component.ManyakTextButton
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.home.R as HomeR

/** 검색 전 화면. 최근 검색어와 대표 장르를 칩으로 보이고, 비어 있는 구역은 숨긴다. */
@Composable
internal fun StorySearchHome(
    recentTerms: List<String>,
    featuredGenres: List<String>,
    onSearch: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = ManyakTheme.spacing.gutter,
                    end = ManyakTheme.spacing.gutter,
                    top = ManyakTheme.spacing.compact,
                    bottom = ManyakTheme.spacing.gutter,
                ),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.block),
    ) {
        if (recentTerms.isNotEmpty()) {
            SearchSection(
                title = stringResource(HomeR.string.search_recent_title),
                action = {
                    ManyakTextButton(
                        // 버튼 안쪽 여백만큼 밀어 글자 끝을 화면 오른쪽 여백에 맞춘다.
                        modifier =
                            Modifier
                                .height(
                                    ManyakTheme.sizes.controlSmall,
                                ).offset(x = ManyakTheme.spacing.compact),
                        onClick = onClearRecent,
                        contentPadding = PaddingValues(horizontal = ManyakTheme.spacing.compact),
                    ) {
                        Text(
                            text = stringResource(HomeR.string.search_recent_clear),
                            style = ManyakTheme.typography.labelSmall,
                            color = ManyakTheme.colors.textSubtle,
                        )
                    }
                },
            ) {
                recentTerms.forEach { term ->
                    RecentSearchChip(term = term, onSearch = { onSearch(term) }, onRemove = { onRemoveRecent(term) })
                }
            }
        }
        if (featuredGenres.isNotEmpty()) {
            SearchSection(title = stringResource(HomeR.string.search_genre_title)) {
                featuredGenres.forEach { genre ->
                    Text(
                        modifier =
                            Modifier
                                .chipSurface()
                                .clickable(role = Role.Button, onClick = { onSearch(genre) })
                                .padding(horizontal = ManyakTheme.spacing.controlHorizontal)
                                .wrapContentHeight(),
                        text = genre,
                        style = ManyakTheme.typography.bodyMedium,
                        color = ManyakTheme.colors.text,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchSection(
    title: String,
    action: @Composable () -> Unit = {},
    chips: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                modifier = Modifier.weight(1f).semantics { heading() },
                text = title,
                style = ManyakTheme.typography.titleMediumStrong,
                color = ManyakTheme.colors.text,
            )
            action()
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        ) {
            chips()
        }
    }
}

/** 최근 검색어 칩. 글자를 누르면 다시 찾고 오른쪽 끝 x 로 그 검색어만 지운다. */
@Composable
private fun RecentSearchChip(
    term: String,
    onSearch: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(modifier = Modifier.chipSurface(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .clickable(role = Role.Button, onClick = onSearch)
                    .padding(start = ManyakTheme.spacing.controlHorizontal)
                    .widthIn(max = RecentTermMaxWidth)
                    .wrapContentHeight(),
            text = term,
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ManyakIconButton(
            iconRes = DesignsystemR.drawable.ic_close,
            contentDescription = stringResource(HomeR.string.search_recent_remove, term),
            onClick = onRemove,
            size = ManyakTheme.sizes.input,
            iconSize = RemoveIconSize,
            tint = ManyakTheme.colors.textSubtle,
        )
    }
}

@Composable
private fun Modifier.chipSurface(): Modifier =
    height(ManyakTheme.sizes.input)
        .clip(ManyakTheme.shapes.pill)
        .background(ManyakTheme.colors.backgroundNeutral)

/** 웹 칩의 최대 글자 폭. 긴 검색어는 이 폭에서 말줄임한다. */
private val RecentTermMaxWidth = 192.dp

/** 웹 칩의 x 아이콘 크기. */
private val RemoveIconSize = 14.dp
