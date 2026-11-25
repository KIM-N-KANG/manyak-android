package app.manyak.create.general.presentation

import app.manyak.auth.domain.SessionGate
import app.manyak.common.domain.error.DomainResult
import app.manyak.create.general.domain.GeneralStoryRepository
import app.manyak.create.general.domain.generalFieldValue
import app.manyak.create.general.entity.GeneralField
import app.manyak.create.general.entity.GeneralFieldTarget
import app.manyak.create.general.entity.GeneralImageKind
import app.manyak.create.general.entity.GeneralStoryForm
import app.manyak.create.general.entity.GeneralStoryImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("LongParameterList")
internal class GeneralEditorUploads(
    private val repository: GeneralStoryRepository,
    private val scope: CoroutineScope,
    private val gate: SessionGate,
    private val generation: Long,
    private val storyId: String?,
    private val state: () -> GeneralEditorState,
    private val event: suspend (GeneralEditorEvent) -> Unit,
    private val apply: suspend (GeneralStoryForm) -> Unit,
    private val failed: suspend () -> Unit,
) {
    private val jobs = mutableMapOf<GeneralFieldTarget, Job>()

    fun invalidateChanged(
        previous: GeneralStoryForm,
        current: GeneralStoryForm,
    ) {
        jobs.entries.toList().forEach { (target, job) ->
            if (!current.hasImageTarget(target) ||
                generalFieldValue(previous, target) != generalFieldValue(current, target)
            ) {
                job.cancel()
            }
        }
    }

    suspend fun upload(intent: GeneralEditorIntent.Upload) {
        if (!state().inputsEnabled || !state().form.hasImageTarget(intent.target)) return
        jobs[intent.target]?.cancel()
        val previous = generalFieldValue(state().form, intent.target)
        event(GeneralEditorEvent.Uploading(intent.target, true))
        val job = scope.launch(start = CoroutineStart.LAZY) { runUpload(intent, previous) }
        jobs[intent.target] = job
        job.start()
    }

    private suspend fun runUpload(
        intent: GeneralEditorIntent.Upload,
        previous: Any?,
    ) {
        val job = checkNotNull(kotlinx.coroutines.currentCoroutineContext()[Job])
        var image: GeneralStoryImage? = null
        var applied = false
        try {
            if (!gate.isCurrentGeneration(generation)) return
            val kind =
                if (intent.target.field ==
                    GeneralField.COVER
                ) {
                    GeneralImageKind.COVER
                } else {
                    GeneralImageKind.CHARACTER
                }
            val result = repository.uploadImage(intent.path, kind, storyId)
            image = (result as? DomainResult.Success)?.value
            if (!isCurrent(intent.target, job, previous)) return
            when (result) {
                is DomainResult.Success -> {
                    apply(state().form.withImage(intent.target, result.value))
                    applied = true
                }
                is DomainResult.Failure -> failed()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (jobs[intent.target] === job && gate.isCurrentGeneration(generation)) failed()
        } finally {
            withContext(NonCancellable) {
                try {
                    if (!applied) image?.let { repository.discardImage(it) }
                } finally {
                    if (jobs[intent.target] === job) {
                        jobs.remove(intent.target)
                        event(GeneralEditorEvent.Uploading(intent.target, false))
                    }
                }
            }
        }
    }

    private fun isCurrent(
        target: GeneralFieldTarget,
        job: Job,
        previous: Any?,
    ): Boolean {
        if (jobs[target] !== job || !gate.isCurrentGeneration(generation)) return false
        return state().form.hasImageTarget(target) && previous == generalFieldValue(state().form, target)
    }
}

private fun GeneralStoryForm.hasImageTarget(target: GeneralFieldTarget): Boolean =
    target.field == GeneralField.COVER ||
        (target.field == GeneralField.IMAGE && supporting.any { it.id == target.itemId })

private fun GeneralStoryForm.withImage(
    target: GeneralFieldTarget,
    image: GeneralStoryImage,
): GeneralStoryForm =
    if (target.field == GeneralField.COVER) {
        copy(cover = image)
    } else {
        copy(supporting = supporting.map { if (it.id == target.itemId) it.copy(image = image) else it })
    }
