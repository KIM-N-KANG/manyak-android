package app.manyak.common.entity.settings

/** 앱 테마. 기본은 [SYSTEM] 이고, 마이 화면의 테마 변경이 순서대로 순환한다. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    fun next(): ThemeMode = entries[(ordinal + 1) % entries.size]
}
