package app.manyak.root

import androidx.navigation3.runtime.NavKey
import app.manyak.auth.entity.SessionState
import app.manyak.common.entity.user.UserProfile
import app.manyak.core.navigation.PushEntry

/**
 * 보류 중인 외부 진입을 지금 소비할 수 있으면 도착지를, 아니면 null 을 돌려준다.
 *
 * 미로그인·미확정이면 보류를 유지해 로그인 뒤에 이어지고, 프로필이 아직 없으면 수신자를 비교할 수
 * 없으므로 도착할 때까지 기다린다. 홈은 [app.manyak.core.navigation.MainTabsRoute] 로 돌아온다.
 */
internal fun resolveEntryDestination(
    entry: PushEntry?,
    session: SessionState,
    profile: UserProfile?,
): NavKey? {
    if (entry == null || session != SessionState.Member || profile == null) return null
    return entry.routeFor(profile.id)
}
