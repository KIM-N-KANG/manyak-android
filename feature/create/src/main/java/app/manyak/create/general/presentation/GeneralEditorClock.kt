package app.manyak.create.general.presentation

import android.os.SystemClock
import javax.inject.Inject

class GeneralEditorClock(
    private val elapsedRealtime: () -> Long,
) {
    @Inject
    constructor() : this(SystemClock::elapsedRealtime)

    fun now(): Long = elapsedRealtime()
}
