package app.manyak.legal.consent.entity

import app.manyak.common.entity.consent.RequiredConsent

/**
 * 조회·기록 응답을 "아직 필요한 항목" 으로 접은 것. 비어 있으면 동의를 마친 회원이다.
 * 저장소가 세 항목의 버전과 동의 필요 여부를 검증한 응답만 이 모델로 만든다.
 */
data class ConsentStatus(
    val required: List<RequiredConsent>,
) {
    val isSatisfied: Boolean get() = required.isEmpty()
}
