package app.manyak.story.detail.presentation.component

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.manyak.designsystem.theme.ManyakTheme
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
