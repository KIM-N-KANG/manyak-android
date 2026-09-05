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
import app.manyak.story.R as StoryR

/**
 * 제작자·생성일. 본문 마지막에 딸린 메타 정보라 다른 섹션과 달리 화면 폭을 그대로 채우는 옅은
 * 바탕을 깔아 읽을 글과 구분한다. 좌우 여백은 바탕 밖이 아니라 안에 둔다.
 *
 * 둘은 바탕 하나를 나눠 쓴다 — 같은 성격의 값이라 띠를 둘로 나누면 무엇이 한 묶음인지 흐려진다.
 * 값이 없는 줄은 그리지 않고, 둘 다 없으면 띠 자체가 없다.
 */
@Composable
internal fun MetaBlock(
    authorNickname: String?,
    date: String?,
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
