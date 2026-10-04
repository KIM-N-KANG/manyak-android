package app.manyak.legal.consent.entity

/** 명시 동의를 받는 항목. 서버 응답·요청 필드와 같은 이름이며, 시트에는 이 선언 순서(만 14세 → 약관 → 처리방침)로 그린다. */
enum class ConsentItem {
    AGE14,
    TERMS,
    PRIVACY,
}

/** 아직 동의가 필요한 항목과 서버가 요구하는 현행 버전. 기록 요청은 이 버전을 그대로 싣는다. */
data class RequiredConsent(
    val item: ConsentItem,
    val requiredVersion: String,
)

/**
 * 조회·기록 응답을 "아직 필요한 항목" 으로 접은 것. 비어 있으면 동의를 마친 회원이다.
 * 저장소가 세 항목의 버전과 동의 필요 여부를 검증한 응답만 이 모델로 만든다.
 */
data class ConsentStatus(
    val required: List<RequiredConsent>,
) {
    val isSatisfied: Boolean get() = required.isEmpty()
}
