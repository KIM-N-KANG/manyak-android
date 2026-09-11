package app.manyak.root

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import app.manyak.analytics.domain.Analytics
import app.manyak.auth.domain.SessionRepository
import app.manyak.auth.entity.SessionState
import app.manyak.common.domain.credit.CreditPolicyRepository
import app.manyak.common.domain.settings.ThemePreferenceRepository
import app.manyak.common.domain.user.UserProfileRepository
import app.manyak.common.entity.credit.CreditPolicy
import app.manyak.common.entity.settings.ThemeMode
import app.manyak.core.navigation.PushEntry
import app.manyak.my.invite.domain.InviteOnboardingRepository
import app.manyak.session.SessionTerminationCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 루트는 세션 상태만 본다. 그래프 전환은 이동 효과가 아니라 이 상태의 결과다. */
@HiltViewModel
class RootViewModel
    @Inject
    constructor(
        sessionRepository: SessionRepository,
        themePreferenceRepository: ThemePreferenceRepository,
        profileRepository: UserProfileRepository,
        inviteOnboardingRepository: InviteOnboardingRepository,
        private val savedStateHandle: SavedStateHandle,
        private val creditPolicyRepository: CreditPolicyRepository,
        private val coordinator: SessionTerminationCoordinator,
        /** 화면이 직접 보내는 이벤트의 통로. 루트가 CompositionLocal 로 내린다. */
        val analytics: Analytics,
    ) : ViewModel() {
        val sessionState: StateFlow<SessionState> = sessionRepository.sessionState

        /** 저장된 테마. 저장소를 읽기 전 첫 프레임은 시스템 설정으로 그린다. */
        val themeMode: StateFlow<ThemeMode> =
            themePreferenceRepository.themeMode
                .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

        /**
         * 신규 가입 초대 코드 안내가 뜰 차례인가. 회원 그래프 위에 얹는 시트들의 순서를 루트가 정하기 위해 본다.
         * 읽기 전에는 "뜰 수 있다" 로 둔다 — 그 반대면 안내 두 장이 한 프레임에 겹칠 수 있다.
         */
        val inviteOnboardingPending: StateFlow<Boolean> =
            inviteOnboardingRepository.pending.stateIn(viewModelScope, SharingStarted.Eagerly, true)

        /** 서버가 정본인 이프 수치. 세 기능 모듈이 같은 값을 보도록 루트가 한 번만 읽는다. */
        val creditPolicy: StateFlow<CreditPolicy?> = creditPolicyRepository.policy

        /**
         * 알림 탭으로 들어온 외부 진입의 도착지. 메인 그래프가 소비할 수 있을 때만 값이 있다.
         *
         * 진입 자체는 저장 상태에 보류한다 — 미로그인이면 로그인 뒤에 이어지고, 로그인 화면에서 외부
         * 제공자로 나갔다가 프로세스가 죽어도 남는다. 해석은 현재 회원의 프로필로 하므로 이전 계정
         * 알림으로는 그 화면에 들어가지 못한다.
         */
        val entryDestination: StateFlow<NavKey?> =
            combine(
                savedStateHandle.getStateFlow<PushEntry?>(KEY_PENDING_ENTRY, null),
                sessionState,
                profileRepository.profile,
                ::resolveEntryDestination,
            ).stateIn(viewModelScope, SharingStarted.Eagerly, null)

        init {
            // 인증이 필요 없는 공개 조회라 세션이 정해지기 전에 시작해도 된다. 실패해도 되살리지 않는다 —
            // 수치를 못 받은 자리는 자리표시 숫자로 그려지고 다음 실행에서 다시 읽는다.
            viewModelScope.launch { creditPolicyRepository.refresh() }
        }

        /** 첫 생성과 `onNewIntent` 가 같은 자리로 들어온다. 나중 진입이 앞의 미소비 진입을 대체한다. */
        fun onExternalEntry(entry: PushEntry) {
            savedStateHandle[KEY_PENDING_ENTRY] = entry
        }

        fun onEntryConsumed() {
            savedStateHandle[KEY_PENDING_ENTRY] = null
        }

        /**
         * 끝내지 못한 종료 정리를 다시 시도한다.
         *
         * 정리는 화면이 사라져도 끝나야 하므로 조정자가 앱 스코프에서 실행한다. 여기서는 시작만 시킨다.
         */
        fun onRetryCleanup() {
            viewModelScope.launch { coordinator.retryCleanup() }
        }
    }

private const val KEY_PENDING_ENTRY = "pendingEntry"
