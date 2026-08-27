package app.manyak.feature.story

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.manyak.core.ui.R
import app.manyak.core.ui.component.ManyakProgressIndicator
import app.manyak.core.ui.component.ScrollEdgeFade
import app.manyak.core.ui.theme.ManyakTheme

/** 본문 위에 떠 있는 하단 CTA. 버튼 위쪽 페이드가 본문이 버튼 뒤로 흘러 들어가는 경계를 만든다. */
@Composable
internal fun StartChatCta(
    isStarting: Boolean,
    failed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val surface = ManyakTheme.colors.surface

    Column(modifier = modifier.fillMaxWidth()) {
        ScrollEdgeFade()
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(surface)
                    .padding(horizontal = ManyakTheme.spacing.gutter)
                    .padding(bottom = ManyakTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        ) {
            if (failed) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.story_detail_start_chat_failed),
                    style = ManyakTheme.typography.bodySmall,
                    color = ManyakTheme.colors.textDanger,
                    textAlign = TextAlign.Center,
                )
            }
            Button(
                modifier = Modifier.fillMaxWidth().heightIn(min = ManyakTheme.sizes.control),
                onClick = onClick,
                enabled = !isStarting,
                shape = ManyakTheme.shapes.control,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = ManyakTheme.colors.brand,
                        contentColor = ManyakTheme.colors.textInverse,
                        disabledContainerColor = ManyakTheme.colors.brand,
                        disabledContentColor = ManyakTheme.colors.textInverse,
                    ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // 진행 중에도 라벨 자리를 유지해 버튼 크기와 접근성 이름이 그대로 남는다.
                    Text(
                        modifier = Modifier.alpha(if (isStarting) 0f else 1f),
                        text = stringResource(R.string.story_detail_start_chat),
                        style = ManyakTheme.typography.labelLarge,
                    )
                    if (isStarting) {
                        ManyakProgressIndicator(
                            modifier = Modifier.size(ManyakTheme.sizes.icon),
                            color = ManyakTheme.colors.textInverse,
                        )
                    }
                }
            }
        }
    }
}
