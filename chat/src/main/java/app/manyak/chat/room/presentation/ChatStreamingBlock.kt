package app.manyak.chat.room.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.manyak.chat.room.presentation.message.ChatAiOutput
import app.manyak.chat.room.presentation.message.ChatMessageSegment
import app.manyak.chat.room.presentation.message.ChatUserBand
import app.manyak.chat.room.presentation.message.rememberTypewriterSegments
import app.manyak.designsystem.component.CHARACTER_IMAGE_ASPECT_RATIO
import app.manyak.designsystem.component.CyclingPhrases
import app.manyak.designsystem.component.ImageGenerationLoading
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.chat.R as ChatR

/**
 * 진행 중인 턴. 이어쓰기면 목록 끝에, 재생성이면 대상 턴 자리에 놓인다.
 *
 * 본문은 도착한 그대로가 아니라 타자기 공개를 거친다 — 배칭된 덩이가 아니라 글자가 이어서 나타난다.
 */
@Composable
internal fun StreamingBlock(
    streaming: StreamingTurn,
    onCharacterImageClick: (String) -> Unit,
) {
    val revealed = rememberTypewriterSegments(streaming.segments)
    Column {
        ChatUserBand(text = streaming.userInput)
        StreamingOutput(
            realtimeImage = streaming.realtimeImage,
            segments = revealed,
            onCharacterImageClick = onCharacterImageClick,
        )
    }
}

/**
 * 로딩과 본문이 같은 자리를 쓴다. 첫 조각(글자든 이미지든)이 오면 로딩은 그 자리에서 페이드로 빠지고
 * 본문이 그 아래에서 드러난다.
 *
 * **로딩이 차지하던 높이는 본문이 그만큼 자랄 때까지 최소 높이로 남긴다** — 로딩이 빠지며 항목이 줄면
 * 앵커 아래 콘텐츠가 한 화면에 모자라져 상단에 붙인 사용자 밴드가 내려앉는다. 높이는 로딩이 그려지는
 * 동안 잰다(첫 조각이 온 뒤에는 이미 빠진 뒤다). 구성 변경에도 남겨야 복원된 패드와 어긋나지 않는다.
 * 목록이 같은 키의 저장 상태를 다음 전송에도 돌려주지만, 새 스트림은 늘 로딩부터 그려 값을 다시 잰다.
 */
@Composable
private fun StreamingOutput(
    realtimeImage: Boolean,
    segments: List<ChatMessageSegment>,
    onCharacterImageClick: (String) -> Unit,
) {
    val hasOutput = segments.isNotEmpty()
    var loadingHeightPx by rememberSaveable { mutableIntStateOf(0) }
    val minHeight = with(LocalDensity.current) { if (hasOutput) loadingHeightPx.toDp() else 0.dp }
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = minHeight)) {
        if (hasOutput) ChatAiOutput(segments = segments, onCharacterImageClick = onCharacterImageClick)
        AnimatedVisibility(
            visible = !hasOutput,
            enter = EnterTransition.None,
            exit = fadeOut(tween(ManyakTheme.motion.elementExitMillis)),
        ) {
            val measured = Modifier.onSizeChanged { size -> loadingHeightPx = size.height }
            if (realtimeImage) ScenePlaceholder(modifier = measured) else WritingPlaceholder(modifier = measured)
        }
    }
}

/** 첫 표시 가능 사건이 오기 전의 자리. 빈 화면으로 두면 보냈는지 알 수 없어, 옅은 띠를 흘려 진행 중임을 말한다. */
@Composable
private fun WritingPlaceholder(modifier: Modifier = Modifier) {
    val statusLabel = stringResource(ChatR.string.chat_room_writing_status)
    Text(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = ManyakTheme.spacing.gutter, vertical = ManyakTheme.spacing.passage)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = statusLabel
                },
        text = stringResource(ChatR.string.chat_room_writing),
        style = ManyakTheme.typography.bodyReading.merge(TextStyle(brush = rememberWritingShimmerBrush())),
    )
}

/**
 * 실시간 이미지가 켜진 채 보낸 턴의 자리. 순환 문구 아래에 인물 이미지가 들어올 4:3 자리를 미리 잡아,
 * 이미지가 오면 같은 자리에서 교체된다. 보조기술에는 덩이 하나로 읽힌다 — 문구가 4초마다 바뀌는 것을
 * 매번 알리면 소음이다.
 */
@Composable
private fun ScenePlaceholder(modifier: Modifier = Modifier) {
    val statusLabel = stringResource(ChatR.string.chat_room_scene_status)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = ManyakTheme.spacing.gutter, vertical = ManyakTheme.spacing.passage)
                .clearAndSetSemantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = statusLabel
                },
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.passage),
    ) {
        CyclingPhrases(
            phrases = stringArrayResource(ChatR.array.chat_room_scene_phrases).toList(),
            style = ManyakTheme.typography.bodyReading,
        )
        ImageGenerationLoading(
            modifier = Modifier.fillMaxWidth(),
            label = statusLabel,
            aspectRatio = CHARACTER_IMAGE_ASPECT_RATIO,
            shape = ManyakTheme.shapes.overlay,
        )
    }
}
