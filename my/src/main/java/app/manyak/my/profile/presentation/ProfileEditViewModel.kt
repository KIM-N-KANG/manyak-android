package app.manyak.my.profile.presentation

import androidx.lifecycle.viewModelScope
import app.manyak.analytics.domain.Analytics
import app.manyak.analytics.entity.AnalyticsEvent
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.user.UserProfileRepository
import app.manyak.common.entity.user.UserProfile
import app.manyak.common.presentation.mvi.MviViewModel
import app.manyak.my.profile.domain.NICKNAME_MAX_LENGTH
import app.manyak.my.profile.domain.NicknameViolation
import app.manyak.my.profile.domain.ProfileEditRepository
import app.manyak.my.profile.domain.validateNickname
import app.manyak.my.profile.entity.ProfilePreset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ProfileEditLoad { LOADING, FAILED, READY }

/** 닉네임 칸 아래에 안내 대신 보이는 사유. 앞의 둘은 저장 전 검사, 뒤의 둘은 서버 거부다. */
enum class NicknameError { TOO_SHORT, INVALID_CHARACTER, TAKEN, REJECTED }

data class ProfileEditUiState(
    val load: ProfileEditLoad = ProfileEditLoad.LOADING,
    /** 수정 전 값. 바뀐 내용 판정과 이미지 표시의 기준이다. */
    val original: UserProfile? = null,
    val nickname: String = "",
    /** 시트에서 고른 프리셋. 고르지 않았으면 원래 이미지를 그린다. */
    val selectedPreset: ProfilePreset? = null,
    val nicknameError: NicknameError? = null,
    val isSubmitting: Boolean = false,
    val isPresetSheetOpen: Boolean = false,
    val presetLoad: ProfileEditLoad = ProfileEditLoad.LOADING,
    val presets: List<ProfilePreset> = emptyList(),
    val isExitDialogOpen: Boolean = false,
) {
    val nicknameChanged: Boolean get() = original != null && nickname != original.nickname

    val imageChanged: Boolean
        get() = selectedPreset != null && selectedPreset.imageUrl != original?.profileImageUrl

    val isDirty: Boolean get() = nicknameChanged || imageChanged

    val imageUrl: String? get() = selectedPreset?.imageUrl ?: original?.profileImageUrl

    /** 원래 이미지의 썸네일은 원래 이미지를 그릴 때만 쓴다. 고른 프리셋은 그 프리셋의 썸네일을 쓴다. */
    val imageThumbnailBase64: String?
        get() = if (selectedPreset != null) selectedPreset.thumbnailBase64 else original?.profileThumbnailBase64
}

sealed interface ProfileEditIntent {
    data object Retry : ProfileEditIntent

    data class NicknameChanged(
        val nickname: String,
    ) : ProfileEditIntent

    data object OpenPresetSheet : ProfileEditIntent

    data object DismissPresetSheet : ProfileEditIntent

    data object RetryPresets : ProfileEditIntent

    data class SelectPreset(
        val preset: ProfilePreset,
    ) : ProfileEditIntent

    data object Submit : ProfileEditIntent

    /** 기기 뒤로가기와 헤더 뒤로가기 버튼. */
    data object Back : ProfileEditIntent

    data object DismissExitDialog : ProfileEditIntent

    data object ConfirmExit : ProfileEditIntent
}

sealed interface ProfileEditEvent {
    data class LoadChanged(
        val load: ProfileEditLoad,
    ) : ProfileEditEvent

    data class Filled(
        val profile: UserProfile,
    ) : ProfileEditEvent

    data class NicknameChanged(
        val nickname: String,
    ) : ProfileEditEvent

    data class NicknameRejected(
        val error: NicknameError,
    ) : ProfileEditEvent

    data class PresetSheetChanged(
        val open: Boolean,
    ) : ProfileEditEvent

    data class PresetLoadChanged(
        val load: ProfileEditLoad,
    ) : ProfileEditEvent

    data class PresetsLoaded(
        val presets: List<ProfilePreset>,
    ) : ProfileEditEvent

    data class PresetSelected(
        val preset: ProfilePreset,
    ) : ProfileEditEvent

    data class SubmittingChanged(
        val submitting: Boolean,
    ) : ProfileEditEvent

    data class ExitDialogChanged(
        val open: Boolean,
    ) : ProfileEditEvent
}

sealed interface ProfileEditEffect {
    /** 저장 없이 들어온 화면으로 돌아간다. */
    data object Close : ProfileEditEffect

    /** 저장했다. 화면이 알리고 들어온 화면으로 돌아간다. */
    data object Saved : ProfileEditEffect

    data object ShowSaveFailed : ProfileEditEffect
}

/**
 * 프로필 수정. 수정 전 값은 프로필 캐시에서 한 번만 가져오고, 캐시가 비었으면 서버에서 읽는다. 편집 중에 캐시가
 * 바뀌어도 입력을 덮어쓰지 않는다.
 *
 * 저장이 성공하면 저장소가 응답을 캐시에 써서 마이 같은 관찰자가 바로 바뀐 값을 그린다. 실패하면 입력을 둔다.
 */
@HiltViewModel
class ProfileEditViewModel
    @Inject
    constructor(
        private val userProfileRepository: UserProfileRepository,
        private val repository: ProfileEditRepository,
        private val analytics: Analytics,
    ) : MviViewModel<ProfileEditIntent, ProfileEditUiState, ProfileEditEvent, ProfileEditEffect>(
            ProfileEditUiState(),
        ) {
        private var loadJob: Job? = null
        private var presetJob: Job? = null
        private var submitJob: Job? = null

        init {
            analytics.track(AnalyticsEvent.ProfileEditViewed)
            load()
        }

        override suspend fun handleIntent(intent: ProfileEditIntent) {
            val state = uiState.value
            when (intent) {
                ProfileEditIntent.Retry -> load()

                is ProfileEditIntent.NicknameChanged ->
                    dispatchEvent(ProfileEditEvent.NicknameChanged(intent.nickname.take(NICKNAME_MAX_LENGTH)))

                ProfileEditIntent.OpenPresetSheet -> openPresetSheet(state)

                ProfileEditIntent.DismissPresetSheet -> dispatchEvent(ProfileEditEvent.PresetSheetChanged(false))

                ProfileEditIntent.RetryPresets -> loadPresets()

                is ProfileEditIntent.SelectPreset -> dispatchEvent(ProfileEditEvent.PresetSelected(intent.preset))

                ProfileEditIntent.Submit -> submit(state)

                ProfileEditIntent.Back -> back(state)

                ProfileEditIntent.DismissExitDialog -> dispatchEvent(ProfileEditEvent.ExitDialogChanged(false))

                ProfileEditIntent.ConfirmExit -> {
                    dispatchEvent(ProfileEditEvent.ExitDialogChanged(false))
                    dispatchEffect(ProfileEditEffect.Close)
                }
            }
        }

        private suspend fun back(state: ProfileEditUiState) {
            when {
                state.isExitDialogOpen -> dispatchEvent(ProfileEditEvent.ExitDialogChanged(false))
                // 요청이 끝나기 전에 나가면 ViewModel 과 함께 요청이 취소돼 결과를 알 수 없다.
                state.isSubmitting -> Unit
                state.isDirty -> dispatchEvent(ProfileEditEvent.ExitDialogChanged(true))
                else -> dispatchEffect(ProfileEditEffect.Close)
            }
        }

        private fun load() {
            if (loadJob?.isActive == true) return
            loadJob =
                viewModelScope.launch {
                    val cached = userProfileRepository.profile.value
                    if (cached != null) {
                        dispatchEvent(ProfileEditEvent.Filled(cached))
                        return@launch
                    }
                    dispatchEvent(ProfileEditEvent.LoadChanged(ProfileEditLoad.LOADING))
                    when (val result = userProfileRepository.refresh()) {
                        is DomainResult.Success -> dispatchEvent(ProfileEditEvent.Filled(result.value))
                        is DomainResult.Failure -> dispatchEvent(ProfileEditEvent.LoadChanged(ProfileEditLoad.FAILED))
                    }
                }
        }

        private suspend fun openPresetSheet(state: ProfileEditUiState) {
            if (state.load != ProfileEditLoad.READY || state.isSubmitting) return
            dispatchEvent(ProfileEditEvent.PresetSheetChanged(true))
            // 한 번 받은 목록은 화면이 사는 동안 쓴다. 순서와 구성은 서버가 고정한다.
            if (state.presetLoad != ProfileEditLoad.READY) loadPresets()
        }

        private fun loadPresets() {
            if (presetJob?.isActive == true) return
            presetJob =
                viewModelScope.launch {
                    dispatchEvent(ProfileEditEvent.PresetLoadChanged(ProfileEditLoad.LOADING))
                    when (val result = repository.presets()) {
                        is DomainResult.Success -> dispatchEvent(ProfileEditEvent.PresetsLoaded(result.value))
                        is DomainResult.Failure ->
                            dispatchEvent(ProfileEditEvent.PresetLoadChanged(ProfileEditLoad.FAILED))
                    }
                }
        }

        private suspend fun submit(state: ProfileEditUiState) {
            if (!state.isDirty || submitJob?.isActive == true) return
            val nicknameChanged = state.nicknameChanged
            val imageChanged = state.imageChanged
            if (nicknameChanged) {
                validateNickname(state.nickname)?.let { violation ->
                    dispatchEvent(ProfileEditEvent.NicknameRejected(violation.toError()))
                    return
                }
            }
            dispatchEvent(ProfileEditEvent.SubmittingChanged(true))
            submitJob =
                viewModelScope.launch {
                    val result =
                        repository.update(
                            nickname = state.nickname.takeIf { nicknameChanged },
                            presetKey = state.selectedPreset?.key?.takeIf { imageChanged },
                        )
                    when (result) {
                        // 화면이 곧 사라지므로 잠금은 풀지 않는다. 풀면 나가는 동안 버튼이 되살아난다.
                        is DomainResult.Success -> {
                            analytics.track(AnalyticsEvent.ProfileEditCompleted(nicknameChanged, imageChanged))
                            dispatchEffect(ProfileEditEffect.Saved)
                        }

                        is DomainResult.Failure -> {
                            dispatchEvent(ProfileEditEvent.SubmittingChanged(false))
                            when (result.error.serverStatus()) {
                                HTTP_CONFLICT -> dispatchEvent(ProfileEditEvent.NicknameRejected(NicknameError.TAKEN))
                                HTTP_BAD_REQUEST ->
                                    dispatchEvent(ProfileEditEvent.NicknameRejected(NicknameError.REJECTED))
                                else -> dispatchEffect(ProfileEditEffect.ShowSaveFailed)
                            }
                        }
                    }
                }
        }

        override fun reduce(
            state: ProfileEditUiState,
            event: ProfileEditEvent,
        ): ProfileEditUiState =
            when (event) {
                is ProfileEditEvent.LoadChanged -> state.copy(load = event.load)

                // 재시도로 다시 채워도 이미 고친 입력은 두지 않는다. 채우는 것은 처음 한 번뿐이다.
                is ProfileEditEvent.Filled ->
                    if (state.original != null) {
                        state.copy(load = ProfileEditLoad.READY)
                    } else {
                        state.copy(
                            load = ProfileEditLoad.READY,
                            original = event.profile,
                            nickname = event.profile.nickname,
                        )
                    }

                // 칸을 고치면 오류를 지우고 안내로 돌아간다.
                is ProfileEditEvent.NicknameChanged -> state.copy(nickname = event.nickname, nicknameError = null)

                is ProfileEditEvent.NicknameRejected -> state.copy(nicknameError = event.error)

                is ProfileEditEvent.PresetSheetChanged -> state.copy(isPresetSheetOpen = event.open)

                is ProfileEditEvent.PresetLoadChanged -> state.copy(presetLoad = event.load)

                is ProfileEditEvent.PresetsLoaded ->
                    state.copy(presetLoad = ProfileEditLoad.READY, presets = event.presets)

                // 고르면 화면 이미지만 바꾸고 시트를 닫는다. 저장은 저장하기에서 한다.
                is ProfileEditEvent.PresetSelected ->
                    state.copy(selectedPreset = event.preset, isPresetSheetOpen = false)

                is ProfileEditEvent.SubmittingChanged -> state.copy(isSubmitting = event.submitting)

                is ProfileEditEvent.ExitDialogChanged -> state.copy(isExitDialogOpen = event.open)
            }
    }

private fun NicknameViolation.toError(): NicknameError =
    when (this) {
        NicknameViolation.TOO_SHORT -> NicknameError.TOO_SHORT
        NicknameViolation.INVALID_CHARACTER -> NicknameError.INVALID_CHARACTER
    }

private fun DomainError.serverStatus(): Int? = (this as? DomainError.Server)?.status

private const val HTTP_BAD_REQUEST = 400
private const val HTTP_CONFLICT = 409
