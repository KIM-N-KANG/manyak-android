package app.manyak.my.profile.data.repository

import app.manyak.auth.domain.SessionGate
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.error.map
import app.manyak.common.entity.user.UserProfile
import app.manyak.my.profile.data.api.ProfileApi
import app.manyak.my.profile.data.datastore.ProfileCacheStore
import app.manyak.my.profile.data.dto.MeResponseDto
import app.manyak.my.profile.data.dto.UpdateProfileRequestDto
import app.manyak.my.profile.domain.ProfileEditRepository
import app.manyak.my.profile.entity.ProfilePreset
import app.manyak.network.data.api.apiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileEditRepositoryImpl
    @Inject
    constructor(
        private val profileApi: ProfileApi,
        private val cache: ProfileCacheStore,
        private val gate: SessionGate,
    ) : ProfileEditRepository {
        override suspend fun presets(): DomainResult<List<ProfilePreset>> =
            apiCall { profileApi.presets() }.map { list ->
                list.map { ProfilePreset(it.key, it.imageUrl, it.thumbnailBase64) }
            }

        override suspend fun update(
            nickname: String?,
            presetKey: String?,
        ): DomainResult<UserProfile> {
            val generation = gate.currentGeneration
            val result =
                apiCall { profileApi.updateProfile(UpdateProfileRequestDto(nickname, presetKey)) }
                    .map(MeResponseDto::toDomain)
            // 응답이 로그아웃 정리 뒤에 도착하면 지운 캐시를 되살리지 않는다.
            if (result is DomainResult.Success && gate.isCurrentGeneration(generation)) cache.save(result.value)
            return result
        }
    }
