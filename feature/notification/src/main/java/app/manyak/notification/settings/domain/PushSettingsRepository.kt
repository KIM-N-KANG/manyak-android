package app.manyak.notification.settings.domain

import app.manyak.common.domain.error.DomainResult
import app.manyak.notification.settings.entity.PushSettings

interface PushSettingsRepository {
    suspend fun get(): DomainResult<PushSettings>

    /** 세 값을 전체 교체한다. 성공 응답은 갱신 후 상태다. */
    suspend fun update(settings: PushSettings): DomainResult<PushSettings>

    companion object {
        /** 광고 동의 없이 야간만 켠 요청에 서버가 돌려주는 400 코드. */
        const val ERROR_NIGHT_PUSH_REQUIRES_MARKETING = "NIGHT_PUSH_REQUIRES_MARKETING"
    }
}
