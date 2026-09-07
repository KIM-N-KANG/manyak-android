package app.manyak.chat.room.presentation.message

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

/**
 * 렌더에 쓸 공개 수. 저장된 공개 수가 총량을 넘으면 **이전 스트림의 값**이다.
 *
 * 목록은 같은 키의 항목 상태를 항목이 사라져도 보존한다 — 첫 턴이 끝나며 저장된 공개 수가 다음
 * 전송의 새 블록에 그대로 복원되고, 새 스트림 총량보다 크니 "다 공개됨"으로 판정돼 타자기가 통째로
 * 건너뛴다. 스트림 안에서 총량은 줄지 않으므로, 넘어선 값은 새 스트림의 시작으로 보고 0에서 다시
 * 시작한다.
 */
internal fun effectiveRevealedUnits(
    revealedUnits: Int,
    totalUnits: Int,
): Int = if (revealedUnits > totalUnits) 0 else revealedUnits

/**
 * 이번 프레임에 드러낼 글자 수. 시정수 [tauNanos] 로 밀린 분량을 지수적으로 비운다.
 *
 * 서버 토큰은 문장 단위 청크(수십 자)로 수백 ms 간격으로 온다. 시정수가 청크 간격보다 짧으면 청크를
 * 몇 프레임에 쏟아내고 다음 청크까지 멈춰 "촤르륵 → 멈춤"이 반복된다 — 뭉텅뭉텅의 원인. 간격보다
 * 길게 잡으면 다음 청크가 올 때까지 글자가 이어져 끊기지 않는다. 하한 1이 타자기 느낌을 지킨다.
 */
internal fun typewriterStep(
    backlog: Int,
    frameNanos: Long,
    tauNanos: Long,
): Int = maxOf(1L, backlog * frameNanos / tauNanos).toInt()

/** 조각 목록의 공개 단위 수. 글자 하나가 1, 이미지도 등장 순서를 지키도록 1을 차지한다. */
internal fun List<ChatMessageSegment>.revealUnitCount(): Int =
    sumOf { segment ->
        when (segment) {
            is ChatMessageSegment.Text -> segment.content.length
            is ChatMessageSegment.CharacterImage -> 1
        }
    }

/**
 * 앞에서부터 [count] 단위만 남긴 조각 목록.
 *
 * 텍스트 조각은 글자 단위로 잘리고, 이미지는 앞 텍스트가 모두 공개된 뒤에야 통째로 나타난다 —
 * 이미지를 반만 보여 줄 방법은 없고, 순서를 앞당기면 아직 안 읽은 문단 뒤의 이미지가 먼저 보인다.
 */
internal fun List<ChatMessageSegment>.takeRevealUnits(count: Int): List<ChatMessageSegment> {
    if (count <= 0) return emptyList()
    var remaining = count
    val revealed = mutableListOf<ChatMessageSegment>()
    for (segment in this) {
        when (segment) {
            is ChatMessageSegment.Text -> {
                if (segment.content.length <= remaining) {
                    revealed += segment
                    remaining -= segment.content.length
                } else {
                    revealed += ChatMessageSegment.Text(segment.content.take(remaining))
                    remaining = 0
                }
            }

            is ChatMessageSegment.CharacterImage -> {
                revealed += segment
                remaining -= 1
            }
        }
        if (remaining <= 0) break
    }
    return revealed
}

/**
 * 스트리밍 조각을 글자 단위로 드러낸다.
 *
 * 토큰은 전송량을 줄이려 배칭돼 덩이로 도착하지만, 화면에는 웹처럼 글자가 이어서 나타나야 한다.
 * 도착한 전체를 목표로 두고 프레임마다 [typewriterStep] 만큼 공개 수를 올린다 — 상태는 배칭
 * 주기로만 바뀌고, 프레임 단위 재구성은 밀린 분량이 있는 동안만 돈다.
 *
 * 공개 수는 [rememberSaveable] 이라 회전해도 읽던 위치부터 이어진다.
 */
@Composable
internal fun rememberTypewriterSegments(segments: List<ChatMessageSegment>): List<ChatMessageSegment> {
    var revealedUnits by rememberSaveable { mutableIntStateOf(0) }
    val totalUnits = segments.revealUnitCount()

    // totalUnits 가 바뀌면(새 배치 도착) 효과가 다시 시작된다 — elapsed 가 배치 이후 경과가 된다.
    LaunchedEffect(totalUnits) {
        // 이전 스트림에서 복원된 공개 수는 여기서 실제로 0으로 되돌린다. 렌더 쪽은 아래
        // effectiveRevealedUnits 가 같은 판정으로 첫 프레임부터 가려 준다.
        if (revealedUnits > totalUnits) revealedUnits = 0
        var previousFrame = 0L
        var elapsed = 0L
        while (revealedUnits < totalUnits) {
            val frame = withFrameNanos { nanos -> nanos }
            val delta = if (previousFrame == 0L) DEFAULT_FRAME_NANOS else frame - previousFrame
            previousFrame = frame
            elapsed += delta
            // 새 배치가 한동안 없으면 스트림이 끝났다고 보고 빠르게 비운다 — 확정 교체 때 남은
            // 글이 한꺼번에 나타나지 않게 한다.
            val tau = if (elapsed > STALE_AFTER_NANOS) DRAIN_TAU_NANOS else STEADY_TAU_NANOS
            val step = typewriterStep(totalUnits - revealedUnits, delta, tau)
            revealedUnits = (revealedUnits + step).coerceAtMost(totalUnits)
        }
    }

    val shown = effectiveRevealedUnits(revealedUnits = revealedUnits, totalUnits = totalUnits)
    return remember(segments, shown) {
        if (shown >= totalUnits) segments else segments.takeRevealUnits(shown)
    }
}

/** 스트림 진행 중 시정수. 서버 청크 간격(수백 ms)보다 길어야 청크 사이가 끊기지 않는다. */
private const val STEADY_TAU_NANOS = 800_000_000L

/** 배치가 끊긴 뒤 비우기 시정수. 확정 교체가 오기 전에 남은 글을 소화한다. */
private const val DRAIN_TAU_NANOS = 130_000_000L

/** 이 시간 동안 새 배치가 없으면 스트림이 멈춘 것으로 본다. */
private const val STALE_AFTER_NANOS = 250_000_000L

/** 첫 프레임의 경과 시간 대체값(60Hz 한 프레임). 이전 프레임이 없어 잴 수 없다. */
private const val DEFAULT_FRAME_NANOS = 16_666_667L
