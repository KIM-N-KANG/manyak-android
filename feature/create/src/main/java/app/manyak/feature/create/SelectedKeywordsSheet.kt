package app.manyak.feature.create

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import app.manyak.common.entity.story.StoryTagCategory
import app.manyak.core.ui.R
import app.manyak.core.ui.component.ManyakBottomSheet
import app.manyak.core.ui.component.ManyakProgressIndicator
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 선택 키워드 시트를 여는 띠. 좌우 여백 없이 프레임을 꽉 채우고 모서리도 두지 않는다 — 푸터의 두
 * 버튼과 같은 무게로 보이면 안 되는, 본문 끝을 마감하는 보조 동작이다(웹과 같은 문법).
 */
@Composable
internal fun SelectedKeywordsTrigger(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ManyakTheme.sizes.input)
                .background(ManyakTheme.colors.backgroundNeutral)
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.create_storyline_view_keywords),
            style = ManyakTheme.typography.labelLarge,
            color = ManyakTheme.colors.textSubtle,
        )
    }
}

/**
 * 스토리라인 단계에서 되짚어 보는 선택 키워드.
 *
 * 이 단계는 키워드 목적지를 대체해 뒤로 돌아가 확인할 수 없다 — 무엇으로 만든 결과인지 보려면
 * 이 시트뿐이라 읽기 전용이고, 고치려면 처음부터 다시 만들어야 한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectedKeywordsSheet(
    keywords: SelectedKeywords,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (keywords is SelectedKeywords.Hidden) return
    ManyakBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismiss,
        // 안내와 키워드 두 덩어리를 32로 떼어 놓는다.
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.block),
    ) {
        SelectedKeywordsHeadline()
        when (keywords) {
            is SelectedKeywords.Loaded -> SelectedKeywordGroups(groups = keywords.groups)
            SelectedKeywords.Failed -> SelectedKeywordsFailure(onRetry = onRetry)
            else -> SelectedKeywordsLoading()
        }
    }
}

@Composable
private fun SelectedKeywordsHeadline(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        Text(
            text = stringResource(R.string.create_selected_keywords_title),
            style = ManyakTheme.typography.titleLarge,
            color = ManyakTheme.colors.text,
        )
        Text(
            text = stringResource(R.string.create_selected_keywords_description),
            style = ManyakTheme.typography.bodyLarge,
            color = ManyakTheme.colors.textSubtle,
        )
    }
}

@Composable
private fun SelectedKeywordGroups(
    groups: List<SelectedKeywordGroup>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.section),
    ) {
        groups.forEach { group -> SelectedKeywordGroupSection(group = group) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedKeywordGroupSection(
    group: SelectedKeywordGroup,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        KeywordSectionLabel(text = group.label(), required = false)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        ) {
            group.keywords.forEach { keyword -> SelectedKeywordChip(name = keyword) }
        }
    }
}

/** 주변 인물이 여럿이면 키워드 화면과 같은 순번 라벨을 쓴다. */
@Composable
private fun SelectedKeywordGroup.label(): String =
    if (ordinal == null) {
        stringResource(category.labelRes)
    } else {
        stringResource(R.string.create_supporting_character_label, ordinal)
    }

@Composable
private fun SelectedKeywordsLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        ManyakProgressIndicator(modifier = Modifier.size(ManyakTheme.sizes.icon))
    }
}

/** 이름표는 제공 태그 목록에서 오므로 조회가 실패하면 아무 키워드도 그릴 수 없다. */
@Composable
private fun SelectedKeywordsFailure(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.create_tags_load_failed),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.textDanger,
        )
        Box(
            modifier =
                Modifier
                    .heightIn(min = ManyakTheme.sizes.controlSmall)
                    .clip(ManyakTheme.shapes.control)
                    .clickable(role = Role.Button, onClick = onRetry)
                    .padding(horizontal = ManyakTheme.spacing.compact),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.common_retry),
                style = ManyakTheme.typography.labelSmall,
                color = ManyakTheme.colors.textSubtle,
            )
        }
    }
}

private fun previewKeywordGroups(): List<SelectedKeywordGroup> =
    listOf(
        SelectedKeywordGroup(
            category = StoryTagCategory.GENRE,
            keywords = listOf("로맨스", "재벌", "타임루프"),
        ),
        SelectedKeywordGroup(
            category = StoryTagCategory.PROTAGONIST,
            keywords = listOf("무심한", "사랑에 서툰"),
        ),
        SelectedKeywordGroup(
            category = StoryTagCategory.SUPPORTING_CHARACTER,
            ordinal = 1,
            keywords = listOf("다정한", "비밀이 많은"),
        ),
        SelectedKeywordGroup(
            category = StoryTagCategory.SUPPORTING_CHARACTER,
            ordinal = 2,
            keywords = listOf("어딘가 망가진"),
        ),
    )

@Preview(name = "선택한 키워드 · 라이트")
@Composable
private fun SelectedKeywordsSheetPreview() {
    ManyakTheme(darkTheme = false) {
        SelectedKeywordsSheet(
            keywords = SelectedKeywords.Loaded(previewKeywordGroups()),
            onDismiss = {},
            onRetry = {},
        )
    }
}
