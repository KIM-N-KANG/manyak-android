package app.manyak.story.detail.presentation.component

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.story.entity.StoryDetail
import app.manyak.story.entity.StoryVisibility
import app.manyak.story.R as StoryR

/** 본문 마지막 메타 정보. 제작자, 생성일, 소유자의 공개 범위를 한 바탕에 표시한다. */
@Composable
internal fun MetaBlock(
    authorNickname: String?,
    date: String?,
    visibility: StoryVisibility?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(ManyakTheme.colors.backgroundNeutral)
                .padding(ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
    ) {
        authorNickname?.let { nickname ->
            MetaRow(labelRes = StoryR.string.story_detail_author, value = nickname)
        }
        date?.let { value -> MetaRow(labelRes = StoryR.string.story_detail_created_at, value = value) }
        visibility?.let { value ->
            MetaRow(
                labelRes = StoryR.string.story_detail_visibility,
                value =
                    stringResource(
                        when (value) {
                            StoryVisibility.PUBLIC -> StoryR.string.story_detail_visibility_public
                            StoryVisibility.PRIVATE -> StoryR.string.story_detail_visibility_private
                        },
                    ),
            )
        }
    }
}

/** 이름과 값을 양 끝으로 벌린 한 줄. 값이 하나뿐이라 표를 만들지 않고 줄 하나로 둔다. */
@Composable
private fun MetaRow(
    @StringRes labelRes: Int,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(labelRes),
            style = ManyakTheme.typography.labelLarge,
            color = ManyakTheme.colors.textSubtle,
        )
        Text(
            text = value,
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.textSubtle,
        )
    }
}

/**
 * 끝의 제작자·생성일 블록. 본문 구획이 아니라 끝맺음이라 구획 사이보다 좁게 붙인다. 간격을 블록 안
 * 여백으로 두면 하단 배경 전환이 회색 블록보다 먼저 시작하므로 따로 띄운다.
 */
internal fun LazyListScope.metaItems(story: StoryDetail) {
    val visibility = story.visibility.takeIf { story.isOwner }
    if (story.authorNickname == null && story.createdDate == null && visibility == null) return
    item(key = META_GAP_KEY) { Spacer(Modifier.height(ManyakTheme.spacing.gutter)) }
    item(key = META_KEY) {
        MetaBlock(authorNickname = story.authorNickname, date = story.createdDate, visibility = visibility)
    }
}

private const val META_GAP_KEY = "meta-gap"
