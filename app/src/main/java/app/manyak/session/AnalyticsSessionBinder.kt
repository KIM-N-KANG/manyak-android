package app.manyak.session

import app.manyak.analytics.domain.AnalyticsIdentity
import app.manyak.auth.domain.SessionRepository
import app.manyak.auth.entity.SessionState
import app.manyak.common.data.datastore.DeviceIdStore
import app.manyak.common.data.di.ApplicationScope
import app.manyak.common.domain.session.MemberConsent
import app.manyak.common.domain.user.UserProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 분석 SDK 의 식별자를 앱 소유 값에 묶는다.
 *
 * `device_id` 는 API 헤더의 정본인 [DeviceIdStore] 값을 그대로 넣는다. 사용자 식별자는 프로필
 * 캐시와 서버에서 확인한 필수 동의를 함께 따른다. 회원 세션과 동의가 유효할 때만 식별한다.
 * 로그아웃 클릭 이벤트는 그보다 앞서 발행되므로 옛 사용자에게 귀속된다.
 */
@Singleton
class AnalyticsSessionBinder
    @Inject
    constructor(
        private val identity: AnalyticsIdentity,
        private val deviceIdStore: DeviceIdStore,
        private val userProfileRepository: UserProfileRepository,
        private val sessionRepository: SessionRepository,
        private val memberConsent: MemberConsent,
        @param:ApplicationScope private val applicationScope: CoroutineScope,
    ) {
        fun start() {
            // SDK에 남은 이전 식별자를 이벤트 대기열을 열기 전에 해제한다.
            identity.clearUser()
            applicationScope.launch {
                // 읽지 못하면 이벤트가 열리지 않는다. 로그인도 같은 값이 없으면 막히므로 따로 복구하지 않는다.
                deviceIdStore.requireDeviceId()?.let(identity::setDeviceId)
            }
            applicationScope.launch {
                combine(userProfileRepository.profile, sessionRepository.sessionState, memberConsent.isSatisfied) {
                    profile,
                    session,
                    satisfied,
                    ->
                    profile?.id?.takeIf { session == SessionState.Member && satisfied }
                }.distinctUntilChanged()
                    .collect { userId -> if (userId == null) identity.clearUser() else identity.setUser(userId) }
            }
        }
    }
