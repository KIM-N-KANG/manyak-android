package app.manyak.create.general.entity

enum class GeneralTab { PROFILE, SETTINGS, PROTAGONIST, SUPPORTING, START, EVENTS, PUBLISH }

enum class GeneralField {
    TITLE,
    ONE_LINE_INTRO,
    WORLD,
    PROGRESSION,
    NAME,
    GENDER,
    FEATURE,
    CHARACTER_DESCRIPTION,
    PROLOGUE,
    SITUATION,
    SUGGESTED_INPUT,
    MIN_TURNS,
    CONDITION,
    EPILOGUE,
    EVENT_DESCRIPTION,
    KEY_SENTENCE,
    GENRES,
    DESCRIPTION,
    IMAGE,
    COVER,
    ITEMS,
}

data class GeneralFieldTarget(
    val tab: GeneralTab,
    val field: GeneralField,
    val itemId: String? = null,
    val startId: String? = null,
    val inputIndex: Int? = null,
)

enum class GeneralErrorReason {
    REQUIRED,
    TOO_SHORT,
    TOO_LONG,
    DUPLICATE,
    GENDER_REQUIRED,
    GENRE_REQUIRED,
    INVALID_COUNT,
    INVALID_NUMBER,
    INVALID_GENRE,
}

data class GeneralFieldError(
    val target: GeneralFieldTarget,
    val reason: GeneralErrorReason,
    val limit: Int? = null,
)
