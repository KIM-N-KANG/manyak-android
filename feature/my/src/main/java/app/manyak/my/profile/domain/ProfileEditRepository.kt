package app.manyak.my.profile.domain

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.user.UserProfile
import app.manyak.my.profile.entity.ProfilePreset

/**
 * 프로필 수정. 저장에 성공하면 응답을 프로필 캐시에 바로 써서 프로필을 관찰하는 화면이 다시 읽지 않고도
 * 바뀐 값을 그린다.
 */
interface ProfileEditRepository {
    suspend fun presets(): DomainResult<List<ProfilePreset>>

    /** 바뀐 필드만 싣는다. null 인 필드는 보내지 않는다. */
    suspend fun update(
        nickname: String?,
        presetKey: String?,
    ): DomainResult<UserProfile>
}
