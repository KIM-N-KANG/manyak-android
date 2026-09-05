package app.manyak.feature.chat.composer

import app.manyak.designsystem.text.parseTextSegments

/** 블럭 종류. 상황은 강조 마커로 감싸 보내고 대사는 그대로 보낸다. */
enum class InputBlockType {
    SITUATION,
    DIALOGUE,
}

/**
 * 블럭 입력 한 칸.
 *
 * [id] 는 목록 안에서만 뜻이 있는 값이다. 값이 같은 블럭이 둘 있어도 서로 다른 칸이므로 위치가 아니라
 * 이 값으로 가른다 — 가운데 칸을 지웠을 때 아래 칸의 입력이 위로 밀려 붙지 않게 한다.
 */
data class InputBlock(
    val id: Long,
    val type: InputBlockType,
    val value: String = "",
)

/**
 * 서버로 보낼 때 블럭을 잇는 구분자. 모드 전환에는 공백 한 칸을 쓴다.
 *
 * 추천 문장을 즉시 전송할 때도 같은 구분자로 정규화한다 — 그래야 추천을 눌렀을 때와 같은 문장을
 * 직접 입력했을 때의 저장 본문이 갈리지 않는다.
 */
const val BLOCK_SEND_SEPARATOR = "\n\n"

/** 시작 상태는 상황 하나와 대사 하나다. */
fun createDefaultInputBlocks(): List<InputBlock> =
    listOf(
        InputBlock(id = 1, type = InputBlockType.SITUATION),
        InputBlock(id = 2, type = InputBlockType.DIALOGUE),
    )

/**
 * 상황·대사를 합친 블럭 개수 상한.
 *
 * 파싱(모드 전환·추천 채우기)에는 걸지 않는다 — 문장을 잘라 버리는 대신 넘친 상태를 허용하고 추가만
 * 막는다.
 */
const val MAX_INPUT_BLOCKS = 50

fun List<InputBlock>.canAddBlock(): Boolean = size < MAX_INPUT_BLOCKS

fun List<InputBlock>.addBlock(type: InputBlockType): List<InputBlock> =
    if (canAddBlock()) this + InputBlock(id = nextId(), type = type) else this

/**
 * 블럭 id 마다 같은 종류끼리 1 부터 센 순번. 라벨에만 쓰고 전송 본문에는 싣지 않는다.
 *
 * 위치가 아니라 id 로 돌려준다 — 가운데 칸을 지우면 뒤 칸의 순번이 당겨져야 하고, 그때 목록 위치는
 * 이미 바뀌어 있다.
 */
fun List<InputBlock>.typeOrdinals(): Map<Long, Int> {
    val counts = mutableMapOf<InputBlockType, Int>()
    return associate { block ->
        val ordinal = (counts[block.type] ?: 0) + 1
        counts[block.type] = ordinal
        block.id to ordinal
    }
}

fun List<InputBlock>.removeBlock(id: Long): List<InputBlock> = filterNot { block -> block.id == id }

fun List<InputBlock>.updateBlock(
    id: Long,
    value: String,
): List<InputBlock> = map { block -> if (block.id == id) block.copy(value = value) else block }

fun List<InputBlock>.hasInput(): Boolean = any { block -> block.value.isNotBlank() }

/**
 * 블럭을 하나의 문장으로 잇는다.
 *
 * **구분자가 쓰임에 따라 갈린다** — 서버로 보낼 때는 빈 줄(`\n\n`)로 띄우고, 일반 모드로 옮길 때는
 * 공백 한 칸으로 잇는다. 전송 모양을 그대로 입력창에 넣으면 빈 줄이 남아 이어 쓰기 어렵다.
 *
 * 빈 블럭은 버리고 상황만 `*...*` 로 감싼다.
 */
fun serializeInputBlocks(
    blocks: List<InputBlock>,
    separator: String = " ",
): String =
    blocks
        .mapNotNull { block ->
            val value = block.value.trim()
            when {
                value.isEmpty() -> null
                block.type == InputBlockType.SITUATION -> "*$value*"
                else -> value
            }
        }.joinToString(separator)

/**
 * 문장을 블럭으로 쪼갠다. 강조 구간은 상황, 나머지는 대사이며 원문 순서를 지킨다.
 *
 * **볼드 마커는 지우지 않고 대사 안에 그대로 남긴다** — 블럭 모드에 볼드 칸이 없어서 마커를 걷으면
 * 모드를 오갈 때마다 굵기가 사라진다.
 */
fun parseInputBlocks(text: String): List<InputBlock> {
    val blocks = mutableListOf<InputBlock>()
    var nextId = 1L

    for (line in text.split('\n')) {
        val dialogue = StringBuilder()

        for (segment in parseTextSegments(line)) {
            if (!segment.emphasis) {
                dialogue.append(if (segment.bold) "**${segment.text}**" else segment.text)
                continue
            }
            nextId = blocks.appendTrimmed(InputBlockType.DIALOGUE, dialogue.toString(), nextId)
            dialogue.setLength(0)
            nextId = blocks.appendTrimmed(InputBlockType.SITUATION, segment.text, nextId)
        }

        nextId = blocks.appendTrimmed(InputBlockType.DIALOGUE, dialogue.toString(), nextId)
    }

    return blocks
}

/** 비어 있지 않을 때만 블럭을 붙이고 다음 id 를 돌려준다. */
private fun MutableList<InputBlock>.appendTrimmed(
    type: InputBlockType,
    value: String,
    nextId: Long,
): Long {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return nextId
    add(InputBlock(id = nextId, type = type, value = trimmed))
    return nextId + 1
}

private fun List<InputBlock>.nextId(): Long = (maxOfOrNull { block -> block.id } ?: 0L) + 1
