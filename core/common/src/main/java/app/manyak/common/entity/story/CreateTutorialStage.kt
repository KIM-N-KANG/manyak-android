package app.manyak.common.entity.story

/**
 * 간편 제작 첫 진입 안내의 단계. 보이는 순서대로 두며 다음 단계는 바로 앞 단계를 마쳤을 때만 연다.
 * [COMPLETING] 만 제작 탭에서 열고 나머지는 간편 제작 화면에서 연다.
 *
 * [wire] 는 기기에 남기는 값이자 분석 이벤트의 `stage` 값이다. 웹과 같은 문자열을 쓴다.
 */
enum class CreateTutorialStage(
    val wire: String,
) {
    INTRO("intro"),
    KEYWORD("keyword"),
    STORYLINE("storyline"),
    ADDITIONAL_INFO("additional-info"),
    COMPLETING("completing"),
    ;

    /** 이 단계 다음에 보일 단계. 마지막 단계면 null 이다. */
    val following: CreateTutorialStage? get() = entries.getOrNull(ordinal + 1)

    companion object {
        /** 건너뛰거나 마지막 단계를 마쳤다는 기록. */
        const val DONE: String = "done"

        /**
         * 기기에 남긴 마지막으로 마친 단계에서 다음에 보일 단계를 구한다. 기록이 없으면 첫 단계, 끝났거나 알 수
         * 없는 값이면 null 이다.
         */
        fun nextAfter(stored: String?): CreateTutorialStage? =
            if (stored == null) entries.first() else entries.firstOrNull { it.wire == stored }?.following
    }
}

/**
 * 지금 화면에서 열 단계. 다음에 보일 단계가 이 화면의 단계일 때만 연다. 키워드 화면은 인사와 키워드 안내를 함께
 * 받는다. 그래서 초안으로 뒤 단계에 바로 들어오면 앞 단계를 마칠 때까지 열지 않는다.
 *
 * @param screen 지금 화면이 안내를 받을 수 있으면 그 화면의 단계, 아니면 null
 */
fun CreateTutorialStage?.openableAt(screen: CreateTutorialStage?): CreateTutorialStage? =
    when {
        this == null || screen == null -> null
        this == screen -> this
        screen == CreateTutorialStage.KEYWORD && this == CreateTutorialStage.INTRO -> this
        else -> null
    }
