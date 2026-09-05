package app.manyak.feature.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.manyak.common.entity.story.StoryCharacter
import app.manyak.designsystem.component.CharacterImage
import app.manyak.designsystem.theme.ManyakTheme

/**
 * 주변 인물. 인물 하나는 이름과 이미지 한 장이고, 이름은 시작 상황의 갈래 제목과 같은 자리다 —
 * 섹션 안에서 무엇이 한 인물인지 가르는 것이 그 제목이다.
 *
 * **이미지가 없는 인물도 이름은 남긴다.** 이미지 생성 실패는 스토리를 막지 않는 서버 계약이라
 * 이름만 있는 인물이 정상 응답이고, 그 인물을 통째로 빼면 등장인물이 몇인지가 화면에서 어긋난다.
 */
@Composable
internal fun CharacterSection(
    characters: List<StoryCharacter>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        // 인물 사이는 인물 안(이름↔이미지)보다 넓다 — 시작 상황의 갈래 사이와 같은 간격이다.
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.section),
    ) {
        characters.forEach { character ->
            SubLabeledBlock(label = character.name) {
                character.imageUrl?.let { imageUrl ->
                    CharacterImage(name = character.name, imageUrl = imageUrl)
                }
            }
        }
    }
}
