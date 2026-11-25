package app.manyak.notification.presentation

import androidx.lifecycle.ViewModel
import app.manyak.notification.domain.NotificationPermissionPromptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** 권한 요청 컴포저블이 저장소를 직접 알지 않게 하는 얇은 통로. 화면 상태가 없어 MVI 를 쓰지 않는다. */
@HiltViewModel
class NotificationPermissionViewModel
    @Inject
    constructor(
        private val repository: NotificationPermissionPromptRepository,
    ) : ViewModel() {
        /** 아직 묻지 않았으면 물었다고 기록하고 true 를 돌려준다. 기록이 먼저라 재생성에서 두 번 묻지 않는다. */
        suspend fun claimPrompt(): Boolean {
            if (repository.wasPrompted()) return false
            repository.markPrompted()
            return true
        }
    }
