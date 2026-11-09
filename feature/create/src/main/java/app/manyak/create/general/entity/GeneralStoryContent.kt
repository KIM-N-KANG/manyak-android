package app.manyak.create.general.entity

data class GeneralStoryContent(
    val title: String,
    val oneLineIntro: String,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    /** 기본 주인공 이름. 주인공 설정 글에 넣지 않고 따로 주고받는다. 없으면 null 이다. */
    val protagonistName: String? = null,
    val storySettings: GeneralStorySettings = GeneralStorySettings(),
    val startSettings: List<GeneralStartInput> = emptyList(),
    val mainEvents: List<GeneralEventInput> = emptyList(),
    val visibility: String = "PRIVATE",
    val thumbnailImage: GeneralStoryImage? = null,
    val characters: List<GeneralCharacterInput> = emptyList(),
)

data class GeneralStorySettings(
    val worldSetting: String = "",
    val ruleSetting: String = "",
    val userRoleSetting: String = "",
    val characterSetting: String = "",
)

data class GeneralStartInput(
    val id: String? = null,
    val name: String,
    val prologue: String,
    val startSituation: String,
    val suggestedInputs: List<String>,
    val endings: List<GeneralEndingInput> = emptyList(),
)

data class GeneralEndingInput(
    val name: String,
    val minTurns: Int? = null,
    val achievementCondition: String = "",
    val epilogue: String = "",
)

data class GeneralEventInput(
    val name: String,
    val description: String,
    val keySentence: String,
)

data class GeneralCharacterInput(
    val id: String? = null,
    val name: String,
    val description: String? = null,
    val images: List<GeneralImageInput> = emptyList(),
)

data class GeneralImageInput(
    val id: String? = null,
    val objectKey: String? = null,
    val imageName: String = "",
    val imageUrl: String = "",
)

/**
 * null 필드는 생략하고 빈 소개는 저장된 소개를 지웁니다. 주인공 이름은 비우면 싣지 않아 서버가 기존 이름을
 * 그대로 둡니다.
 */
data class GeneralStoryPatch(
    val title: String? = null,
    val oneLineIntro: String? = null,
    val description: String? = null,
    val genres: List<String>? = null,
    val protagonistName: String? = null,
    val storySettings: GeneralStorySettings? = null,
    val startSettings: List<GeneralStartInput>? = null,
    val mainEvents: List<GeneralEventInput>? = null,
    val visibility: String? = null,
    val thumbnailObjectKey: String? = null,
    val characters: List<GeneralCharacterInput>? = null,
) {
    val isEmpty: Boolean
        get() =
            listOf(
                title,
                oneLineIntro,
                description,
                genres,
                protagonistName,
                storySettings,
                startSettings,
                mainEvents,
                visibility,
                thumbnailObjectKey,
                characters,
            ).all { it == null }
}

data class GeneralEditCharacter(
    val formId: String?,
    val source: GeneralCharacterInput,
)

data class GeneralStoryEditBase(
    val characters: List<GeneralEditCharacter>,
    val baseline: GeneralStoryPatch,
    val originalForm: GeneralStoryForm,
    val sendAll: Boolean = false,
)

data class GeneralStoryEditForm(
    val form: GeneralStoryForm,
    val base: GeneralStoryEditBase,
)
