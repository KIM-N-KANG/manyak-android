package app.manyak.session

import app.manyak.analytics.domain.AnalyticsIdentity
import app.manyak.common.data.datastore.DeviceIdStore
import app.manyak.common.data.di.ApplicationScope
import app.manyak.common.domain.user.UserProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 분석 SDK 의 식별자를 앱 소유 값에 묶는다.
 *
 * `device_id` 는 API 헤더의 정본인 [DeviceIdStore] 값을 그대로 넣는다. 사용자 식별자는 프로필
 * 캐시를 따른다 — 로그인·복원으로 프로필이 생기면 붙고, 종료 정리가 캐시를 비우면 떨어진다.
 * 로그아웃 클릭 이벤트는 그보다 앞서 발행되므로 옛 사용자에게 귀속된다.
 */
@Singleton
class AnalyticsSessionBinder
    @Inject
    constructor(
        private val identity: AnalyticsIdentity,
        private val deviceIdStore: DeviceIdStore,
        private val userProfileRepository: UserProfileRepository,
        @param:ApplicationScope private val applicationScope: CoroutineScope,
    ) {
        fun start() {
            applicationScope.launch {
                // 읽지 못하면 이벤트가 열리지 않는다. 로그인도 같은 값이 없으면 막히므로 따로 복구하지 않는다.
                deviceIdStore.requireDeviceId()?.let(identity::setDeviceId)
            }
            applicationScope.launch {
                userProfileRepository.profile
                    .map { profile -> profile?.id }
                    .distinctUntilChanged()
                    .collect { userId -> if (userId == null) identity.clearUser() else identity.setUser(userId) }
            }
        }
    }
