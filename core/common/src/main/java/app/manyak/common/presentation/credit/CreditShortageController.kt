package app.manyak.common.presentation.credit

import app.manyak.common.domain.credit.AttendanceClaimer
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.user.UserProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 이프 부족 시트가 그리는 상태. 화면 상태에는 닫혀 있으면 null, 열려 있으면 이 값으로 들어간다. */
data class CreditShortageUiState(
    /** 오늘 출석했는지. 프로필을 모르면 null 이고 출석 버튼을 숨긴다. */
    val attendedToday: Boolean? = null,
    val isClaimingAttendance: Boolean = false,
    /** 충전으로 떠나기 전에 입력을 보관하는 중. */
    val isCharging: Boolean = false,
) {
    val canClaimAttendance: Boolean get() = attendedToday == false

    /** 요청 중에는 닫지 않는다. 결과를 못 본 채 시트만 사라진다. */
    val isBusy: Boolean get() = isClaimingAttendance || isCharging
}

/** 시트의 출석 결과. 화면이 토스트로 알린다. */
sealed interface AttendanceOutcome {
    data class Rewarded(
        val amount: Long,
    ) : AttendanceOutcome

    data object AlreadyDone : AttendanceOutcome

    data object Failed : AttendanceOutcome
}

/**
 * 이프 부족 시트의 절차. 채팅방과 간편 제작이 같은 시트를 쓰므로 화면마다 되풀이하지 않고 여기 둔다.
 *
 * 상태를 직접 들지 않고 [emit] 으로 올려보낸다. 화면 상태의 정본은 각 ViewModel 이라 여기서 따로 들면 구성
 * 변경에서 두 벌이 갈라진다. 다만 겹친 요청을 거르려고 마지막으로 올린 값은 기억한다. 분석 이벤트는 유료 동작을
 * 아는 ViewModel 이 보낸다.
 */
class CreditShortageController(
    private val scope: CoroutineScope,
    private val attendance: AttendanceClaimer,
    private val profiles: UserProfileRepository,
    private val emit: suspend (CreditShortageUiState?) -> Unit,
    private val notify: suspend (AttendanceOutcome) -> Unit,
) {
    private var state: CreditShortageUiState? = null
    private var job: Job? = null

    /** 시트를 연다. 잔액과 출석 여부는 화면이 서버와 어긋났을 수 있어 다시 읽는다. */
    suspend fun open() {
        update(CreditShortageUiState(attendedToday = profiles.profile.value?.attendedToday))
        scope.launch {
            if (profiles.refresh() !is DomainResult.Success) return@launch
            state?.let { current -> update(current.copy(attendedToday = profiles.profile.value?.attendedToday)) }
        }
    }

    suspend fun dismiss() {
        if (state?.isBusy == false) update(null)
    }

    /** 화면을 떠나지 않고 출석한다. 보상을 받았거나 이미 출석했으면 오늘 출석이 끝난 것이라 시트를 닫는다. */
    fun claimAttendance() {
        val current = state?.takeIf { it.canClaimAttendance && !it.isBusy } ?: return
        if (job?.isActive == true) return
        job =
            scope.launch {
                update((state ?: current).copy(isClaimingAttendance = true))
                when (val result = attendance.claimAttendance()) {
                    is DomainResult.Success -> {
                        update(null)
                        notify(
                            if (result.value.rewarded) {
                                AttendanceOutcome.Rewarded(result.value.amount ?: 0)
                            } else {
                                AttendanceOutcome.AlreadyDone
                            },
                        )
                        profiles.refresh()
                    }

                    is DomainResult.Failure -> {
                        state?.let { latest -> update(latest.copy(isClaimingAttendance = false)) }
                        notify(AttendanceOutcome.Failed)
                    }
                }
            }
    }

    /** 충전으로 떠난다. [leave] 가 입력을 보관하고 떠났으면 참이고, 떠나지 못하면 시트에 머문다. */
    fun charge(leave: suspend () -> Boolean) {
        val current = state?.takeIf { !it.isBusy } ?: return
        if (job?.isActive == true) return
        job =
            scope.launch {
                update((state ?: current).copy(isCharging = true))
                if (leave()) {
                    update(null)
                } else {
                    state?.let { latest -> update(latest.copy(isCharging = false)) }
                }
            }
    }

    private suspend fun update(next: CreditShortageUiState?) {
        state = next
        emit(next)
    }
}
