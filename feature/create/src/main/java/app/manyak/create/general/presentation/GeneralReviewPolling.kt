package app.manyak.create.general.presentation

import app.manyak.common.domain.error.DomainResult
import app.manyak.create.general.entity.GeneralStoryEditor
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

internal suspend fun pollGeneralReview(
    startedAt: Long,
    now: () -> Long,
    fetch: suspend () -> DomainResult<GeneralStoryEditor>,
    onResult: suspend (GeneralStoryEditor) -> Unit,
    onTimeout: suspend () -> Unit,
) {
    val remaining = (REVIEW_TIMEOUT_MS - (now() - startedAt)).coerceAtLeast(0)
    val terminal =
        withTimeoutOrNull(remaining) {
            var editor: GeneralStoryEditor? = null
            while (editor == null) {
                delay(REVIEW_INTERVAL_MS)
                val result = fetch()
                val status = (result as? DomainResult.Success)?.value?.submission?.status
                if (status in setOf("APPROVED", "REJECTED", "FAILED")) {
                    editor = (result as DomainResult.Success).value
                }
            }
            editor
        }
    if (terminal == null) onTimeout() else onResult(terminal)
}

internal const val REVIEW_TIMEOUT_MS = 60_000L
private const val REVIEW_INTERVAL_MS = 1_000L
