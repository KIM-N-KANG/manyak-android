package app.manyak.legal.consent.domain

import app.manyak.common.domain.error.DomainResult
import app.manyak.legal.consent.entity.ConsentItem
import app.manyak.legal.consent.entity.ConsentStatus

/** 이용약관·개인정보 처리방침·만 14세 이상 확인의 동의 상태. 정본은 서버이고 앱은 로컬 플래그를 두지 않는다. */
interface ConsentRepository {
    suspend fun get(): DomainResult<ConsentStatus>

    /** 수락한 항목의 버전만 기록한다. 성공 응답은 기록 후 상태다. */
    suspend fun record(versions: Map<ConsentItem, String>): DomainResult<ConsentStatus>

    companion object {
        /** 보낸 버전이 현행이 아닐 때의 400 코드. 문서를 다시 보여 주고 받아야 하며 버전만 바꿔 재전송하지 않는다. */
        const val ERROR_VERSION_MISMATCH = "CONSENT_VERSION_MISMATCH"
    }
}
