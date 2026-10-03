package app.manyak.story.detail.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.manyak.designsystem.component.CharacterImage
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.story.entity.StoryCharacter

/** 이름 아래 소개와 이미지를 표시한다. 소개와 이미지가 없어도 인물 이름은 남긴다. */
@Composable
internal fun CharacterSection(
    characters: List<StoryCharacter>,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        // 인물 사이는 인물 안(이름↔이미지)보다 넓다 — 시작 상황의 갈래 사이와 같은 간격이다.
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.section),
    ) {
        characters.forEach { character ->
            SubLabeledBlock(label = character.name) {
                character.description?.let { description ->
                    Text(
                        text = description,
                        style = ManyakTheme.typography.bodyLarge,
                        color = ManyakTheme.colors.text,
                    )
                }
                character.imageUrl?.let { imageUrl ->
                    CharacterImage(name = character.name, imageUrl = imageUrl, onClick = { onImageClick(imageUrl) })
                }
            }
        }
    }
}
