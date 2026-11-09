package app.manyak.auth.data.session

/**
 * `BOOT_COUNT` 를 읽을 수 없는 기기에서 재발급 무한 루프를 막는 프로세스 한정 플래그.
 *
 * 프로세스가 다시 시작되면 사라지므로, 그런 기기는 시작 후 첫 보호 요청에서 한 번 더 선제 재발급한다.
 */
class ProcessAnchorState {
    @Volatile
    var isAnchorVerifiedInThisProcess: Boolean = false
        private set

    fun markVerified() {
        isAnchorVerifiedInThisProcess = true
    }

    fun reset() {
        isAnchorVerifiedInThisProcess = false
    }
}
