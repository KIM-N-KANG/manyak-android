package app.manyak.create.general.domain

import app.manyak.create.general.entity.GeneralCharacter
import app.manyak.create.general.entity.GeneralCharacterInput
import app.manyak.create.general.entity.GeneralGender
import app.manyak.create.general.entity.GeneralImageInput
import app.manyak.create.general.entity.GeneralStartSetting
import app.manyak.create.general.entity.GeneralStoryContent
import app.manyak.create.general.entity.GeneralStoryForm
import app.manyak.create.general.entity.GeneralStoryImage
import app.manyak.create.general.entity.GeneralStorySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneralStoryConversionTest {
    @Test
    fun settings_parser_preserves_unknown_sections_and_unknown_gender() {
        val form =
            restoreGeneralStoryForm(
                GeneralStoryContent(
                    title = "제목",
                    oneLineIntro = "소개",
                    storySettings =
                        GeneralStorySettings(
                            worldSetting = "# 세계관\n배경\n# 전제\n추가 내용",
                            ruleSetting = "# 전개 규칙\n전개\n# 문체 톤\n차분함\n# 분량 배분\n묘사 7 : 대사 3",
                            userRoleSetting = "# 주인공\n## 호칭\n주인공\n## 성별\n미정\n## 역할\n여행자",
                            characterSetting = "# 등장인물\n\n## 동료\n### 성별\n남성\n### 성격\n친절함",
                        ),
                ),
            )

        assertEquals("배경\n# 전제\n추가 내용", form.world)
        assertEquals("전개\n# 문체 톤\n차분함", form.progression)
        assertEquals(7, form.descriptionRatio)
        assertNull(form.protagonist.gender)
        assertEquals("## 호칭\n주인공\n## 성별\n미정\n## 역할\n여행자", form.protagonist.feature)
        assertEquals("### 성격\n친절함", form.supporting.single().feature)
    }

    @Test
    fun protagonist_name_travels_as_its_own_field_and_legacy_honorific_stays_in_feature() {
        val content =
            buildGeneralStoryContent(
                GeneralStoryForm(
                    protagonist = GeneralCharacter(name = " 해솔 ", gender = GeneralGender.FEMALE, feature = "특징"),
                ),
            )

        assertEquals("해솔", content.protagonistName)
        assertEquals("# 주인공\n## 성별\n여성\n특징", content.storySettings.userRoleSetting)
        assertNull(buildGeneralStoryContent(GeneralStoryForm()).protagonistName)

        val restored = restoreGeneralStoryForm(legacyContent().copy(protagonistName = "해솔"))
        assertEquals("해솔", restored.protagonist.name)
        assertEquals(GeneralGender.FEMALE, restored.protagonist.gender)
        assertEquals("## 호칭\n주인공\n주인공 특징", restored.protagonist.feature)
    }

    @Test
    fun edit_patch_sends_protagonist_name_only_when_changed_and_not_blank() {
        val edit = restoreGeneralStoryEditForm(legacyContent().copy(protagonistName = "해솔"))
        val protagonist = edit.form.protagonist

        assertNull(buildGeneralStoryPatch(edit.form, edit.base).protagonistName)
        val renamed = edit.form.copy(protagonist = protagonist.copy(name = "새 이름"))
        assertEquals("새 이름", buildGeneralStoryPatch(renamed, edit.base).protagonistName)
        val cleared = edit.form.copy(protagonist = protagonist.copy(name = " "))
        assertTrue(buildGeneralStoryPatch(cleared, edit.base).isEmpty)
    }

    @Test
    fun unchanged_legacy_text_does_not_get_patched_by_parser_defaults() {
        val edit = restoreGeneralStoryEditForm(legacyContent())

        assertTrue(buildGeneralStoryPatch(edit.form, edit.base).isEmpty)
        val changed = buildGeneralStoryPatch(edit.form.copy(title = "다른 제목"), edit.base)
        assertEquals("다른 제목", changed.title)
        assertNull(changed.storySettings)
        assertNull(changed.characters)
    }

    @Test
    fun replacing_first_character_image_keeps_remaining_images_and_unmatched_character() {
        val edit = restoreGeneralStoryEditForm(legacyContent())
        val current =
            edit.form.copy(
                supporting =
                    edit.form.supporting.map {
                        it.copy(name = "새 이름", image = GeneralStoryImage(objectKey = "new/image"), description = "")
                    },
            )

        val characters = requireNotNull(buildGeneralStoryPatch(current, edit.base).characters)
        assertEquals("actor-1", characters[0].id)
        assertEquals("", characters[0].description)
        assertEquals("new/image", characters[0].images[0].objectKey)
        assertEquals("새 이름_기본", characters[0].images[0].imageName)
        assertEquals("image-2", characters[0].images[1].id)
        assertEquals("hidden", characters[1].id)
        assertNull(characters[1].description)
        assertEquals("hidden-image", characters[1].images.single().id)
    }

    @Test
    fun removing_representative_only_removes_first_image_and_cover_requires_separate_delete() {
        val edit = restoreGeneralStoryEditForm(legacyContent())
        val current = edit.form.copy(cover = null, supporting = edit.form.supporting.map { it.copy(image = null) })
        val patch = buildGeneralStoryPatch(current, edit.base)

        assertTrue(shouldDeleteGeneralThumbnail(current, edit.base))
        assertNull(patch.thumbnailObjectKey)
        assertEquals(
            listOf("image-2"),
            patch.characters
                ?.first()
                ?.images
                ?.map { it.id },
        )
    }

    @Test
    fun rejected_submission_resends_all_fields_and_preserves_pending_image_key() {
        val source = legacyContent().copy(thumbnailImage = GeneralStoryImage(objectKey = "pending-cover"))
        val edit = restoreGeneralStoryEditForm(source, sendAll = true)
        val patch = buildGeneralStoryPatch(edit.form, edit.base)

        assertFalse(patch.isEmpty)
        assertEquals("pending-cover", patch.thumbnailObjectKey)
        assertEquals("PRIVATE", patch.visibility)
        assertEquals("", patch.description)
        assertTrue(requireNotNull(patch.mainEvents).isEmpty())
    }

    @Test
    fun create_trims_text_and_omits_blank_character_description() {
        val form =
            GeneralStoryForm(
                title = "  제목  ",
                supporting =
                    listOf(
                        GeneralCharacter(name = " 인물 ", description = " \t\r\n "),
                        GeneralCharacter(name = "동료", description = " 첫째\n둘째\t끝 "),
                    ),
                startSettings = listOf(GeneralStartSetting(serverId = "existing", name = " 시작 ")),
            )
        val request = buildGeneralStoryContent(form)

        assertEquals("제목", request.title)
        assertNull(request.characters.first().description)
        assertEquals("첫째 둘째 끝", request.characters[1].description)
        assertNull(request.startSettings.single().id)
    }

    @Test
    fun edit_matches_characters_by_name_only_but_submission_can_fall_back_to_position() {
        val content =
            legacyContent().copy(
                characters = listOf(GeneralCharacterInput(id = "another", name = "다른 인물", description = "기존 소개")),
            )

        assertEquals("기존 소개", restoreGeneralStoryForm(content).supporting.single().description)
        assertEquals(
            "",
            restoreGeneralStoryEditForm(content)
                .form.supporting
                .single()
                .description,
        )
    }

    private fun legacyContent() =
        GeneralStoryContent(
            title = "기존 제목",
            oneLineIntro = "기존 소개",
            thumbnailImage = GeneralStoryImage(previewUrl = "https://example.com/cover"),
            storySettings =
                GeneralStorySettings(
                    worldSetting = "제목 없는 세계관",
                    ruleSetting = "오래된 전개 방식",
                    userRoleSetting = "# 주인공\n## 호칭\n주인공\n## 성별\n여성\n주인공 특징",
                    characterSetting = "# 등장인물\n\n## 동료\n### 성별\n남성\n동료 특징",
                ),
            characters =
                listOf(
                    GeneralCharacterInput(
                        id = "actor-1",
                        name = "동료",
                        description = "기존 인물 소개",
                        images =
                            listOf(
                                GeneralImageInput(id = "image-1", imageUrl = "https://example.com/one"),
                                GeneralImageInput(id = "image-2", imageUrl = "https://example.com/two"),
                            ),
                    ),
                    GeneralCharacterInput(
                        id = "hidden",
                        name = "숨은 인물",
                        description = "보존할 소개",
                        images = listOf(GeneralImageInput(id = "hidden-image")),
                    ),
                ),
        )
}
