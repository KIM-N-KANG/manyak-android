package app.manyak.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.manyak.analytics.domain.Analytics
import app.manyak.auth.domain.SessionRepository
import app.manyak.auth.entity.SessionState
import app.manyak.common.domain.credit.CreditPolicyRepository
import app.manyak.common.domain.settings.ThemePreferenceRepository
import app.manyak.common.entity.credit.CreditPolicy
import app.manyak.common.entity.settings.ThemeMode
import app.manyak.session.SessionTerminationCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

        /** 서버가 정본인 이프 수치. 세 기능 모듈이 같은 값을 보도록 루트가 한 번만 읽는다. */
        val creditPolicy: StateFlow<CreditPolicy?> = creditPolicyRepository.policy

        init {
            // 인증이 필요 없는 공개 조회라 세션이 정해지기 전에 시작해도 된다. 실패해도 되살리지 않는다 —
            // 수치를 못 받은 자리는 자리표시 숫자로 그려지고 다음 실행에서 다시 읽는다.
            viewModelScope.launch { creditPolicyRepository.refresh() }
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
