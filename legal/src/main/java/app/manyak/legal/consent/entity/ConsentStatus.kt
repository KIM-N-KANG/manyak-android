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
 * 버전이 없는 항목은 제출할 수 없으므로 필요 목록에 넣지 않는다(계약상 필요 항목에는 항상 버전이 온다).
 */
data class ConsentStatus(
    val required: List<RequiredConsent>,
) {
    val isSatisfied: Boolean get() = required.isEmpty()
}
