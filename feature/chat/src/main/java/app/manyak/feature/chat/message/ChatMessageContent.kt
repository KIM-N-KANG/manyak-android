package app.manyak.feature.chat.message

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.manyak.core.ui.text.storyAnnotatedString
import app.manyak.core.ui.theme.ManyakTheme
import coil3.compose.AsyncImage

/**
 * 사용자 입력. **말풍선이 아니라 화면 폭을 다 쓰는 밴드다** — 정렬도 최대 폭도 둥근 모서리도 없고
 * AI 출력과 가르는 것은 배경 하나다.
 *
 * 폭을 좁힌 말풍선을 쓰면 같은 문단이 웹과 다른 줄 수로 읽힌다. 채팅방 본문은 대화 UI가 아니라
 * 읽는 이야기다.
 */
@Composable
internal fun ChatUserBand(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().background(ManyakTheme.colors.backgroundNeutral)) {
        ChatPassage(text = text)
    }
}

/** 프롤로그·AI 출력. 배경 없이 본문만 그린다. */
@Composable
internal fun ChatAiOutput(
    content: String,
    modifier: Modifier = Modifier,
) {
    ChatAiOutput(segments = remember(content) { parseChatMessageSegments(content) }, modifier = modifier)
}

/**
 * 조각 목록으로 그리는 AI 출력. 스트리밍 중에는 사건이 만든 목록이, 확정 뒤에는 저장 본문을 파싱한
 * 목록이 들어온다.
 *
 * **이미지가 첫 조각이 아니면 위쪽 간격을 한 번 더 준다** — 이야기 사이에 끼는 이미지가 앞 문단에
 * 붙어 보이지 않게 한다.
 */
@Composable
internal fun ChatAiOutput(
    segments: List<ChatMessageSegment>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = ManyakTheme.spacing.passage),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.passage),
    ) {
        segments.forEachIndexed { index, segment ->
            when (segment) {
                is ChatMessageSegment.Text -> ChatPassage(text = segment.content, vertical = false)

                is ChatMessageSegment.CharacterImage ->
                    ChatCharacterImage(
                        modifier =
                            Modifier
                                .padding(top = if (index > 0) ManyakTheme.spacing.passage else 0.dp)
                                .padding(horizontal = ManyakTheme.spacing.gutter),
                        name = segment.name,
                        imageUrl = segment.imageUrl,
                    )
            }
        }
    }
}

/**
 * 본문 한 덩이. 강조 마크업은 `:core:ui` 의 파서를 그대로 쓴다.
 *
 * 조립 결과를 [remember] 로 붙잡는 이유는 스트리밍 중 본문이 계속 자라기 때문이다 — 매
 * recomposition 마다 전문을 다시 파싱하면 비용이 길이의 제곱이 된다.
 */
@Composable
private fun ChatPassage(
    text: String,
    modifier: Modifier = Modifier,
    vertical: Boolean = true,
) {
    val emphasisColor = ManyakTheme.colors.textSubtle
    val annotated = remember(text, emphasisColor) { storyAnnotatedString(text, emphasisColor) }
    Text(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    horizontal = ManyakTheme.spacing.gutter,
                    vertical = if (vertical) ManyakTheme.spacing.passage else 0.dp,
                ),
        text = annotated,
        style = ManyakTheme.typography.bodyReading,
        color = ManyakTheme.colors.text,
    )
}

/**
 * 인물 이미지. 4:3 으로 두고 잘라내지 않는다.
 *
 * **불러오지 못하면 자리째로 사라진다** — placeholder 를 남기면 빈 상자가 이야기를 끊는다.
 * 허용 주소 검사를 그리기 직전에 한 번 더 하는 이유는 마커가 AI 가 만든 본문에서 오기 때문이다.
 */
@Composable
private fun ChatCharacterImage(
    name: String,
    imageUrl: String,
    modifier: Modifier = Modifier,
) {
    var failed by rememberSaveable(imageUrl) { mutableStateOf(false) }
    if (failed || !isAllowedChatCharacterImageUrl(imageUrl)) return

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(CHARACTER_IMAGE_ASPECT_RATIO)
                .border(ImageBorderWidth, ManyakTheme.colors.border, ManyakTheme.shapes.overlay)
                .clip(ManyakTheme.shapes.overlay)
                .background(ManyakTheme.colors.backgroundNeutral),
    ) {
        // 이미지는 선 두께만큼 안으로 들인다. 이미지와 선의 경계가 같은 픽셀에 겹치면 두
        // 안티에일리어싱이 합성돼 곡률에서만 진한 띠가 생기고, 그 띠가 직선부의 선보다 도드라진다.
        AsyncImage(
            modifier =
                Modifier
                    .matchParentSize()
                    .padding(ImageBorderWidth)
                    .clip(ManyakTheme.shapes.overlay),
            model = imageUrl,
            contentDescription = name,
            contentScale = ContentScale.Fit,
            onError = { failed = true },
        )
    }
}

/** 인물 이미지의 가로세로 비율. */
private const val CHARACTER_IMAGE_ASPECT_RATIO = 4f / 3f

private val ImageBorderWidth = 1.dp
