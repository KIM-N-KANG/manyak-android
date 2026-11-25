package app.manyak.create.general.presentation

import app.manyak.auth.domain.SessionGate
import app.manyak.common.domain.error.DomainResult
import app.manyak.create.general.entity.GeneralField
import app.manyak.create.general.entity.GeneralFieldTarget
import app.manyak.create.general.entity.GeneralStoryImage
import app.manyak.create.general.entity.GeneralTab
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GeneralEditorUploadsTest {
    @Test
    fun an_older_upload_cannot_replace_the_latest_image_and_its_file_is_released() =
        runTest {
            val first = CompletableDeferred<Unit>()
            val second = CompletableDeferred<Unit>()
            val repository =
                GeneralEditorRepositoryFake().apply {
                    uploadResult = { source ->
                        withContext(NonCancellable) {
                            if (source == "first") first.await() else second.await()
                            DomainResult.Success(GeneralStoryImage(objectKey = source, localPath = "/owned/$source"))
                        }
                    }
                }
            var state = GeneralEditorState(form = validGeneralForm(), loading = false)
            val uploads =
                GeneralEditorUploads(
                    repository,
                    this,
                    SessionGate(),
                    0,
                    null,
                    state = { state },
                    event = { state = reduceGeneralEditor(state, it) },
                    apply = { state = state.copy(form = it) },
                    failed = { error("unexpected error") },
                )
            val target = GeneralFieldTarget(GeneralTab.PROFILE, GeneralField.COVER)
            uploads.upload(GeneralEditorIntent.Upload(target, "first"))
            runCurrent()
            uploads.upload(GeneralEditorIntent.Upload(target, "second"))
            runCurrent()
            first.complete(Unit)
            runCurrent()
            assertTrue(target in state.uploading)
            second.complete(Unit)
            runCurrent()
            assertEquals("second", state.form.cover?.objectKey)
            assertTrue(state.uploading.isEmpty())
            assertEquals(listOf("first"), repository.discarded.map { it.objectKey })
        }

    @Test
    fun deleting_a_character_cancels_its_upload_and_clears_progress() =
        runTest {
            val repository = GeneralEditorRepositoryFake().apply { uploadResult = { awaitCancellation() } }
            var state = GeneralEditorState(form = validGeneralForm(), loading = false)
            val uploads =
                GeneralEditorUploads(
                    repository,
                    this,
                    SessionGate(),
                    0,
                    null,
                    state = { state },
                    event = { state = reduceGeneralEditor(state, it) },
                    apply = { error("removed character must not receive an image") },
                    failed = { error("cancellation is not a failure") },
                )
            val target =
                GeneralFieldTarget(
                    GeneralTab.SUPPORTING,
                    GeneralField.IMAGE,
                    state.form.supporting
                        .single()
                        .id,
                )
            uploads.upload(GeneralEditorIntent.Upload(target, "image"))
            runCurrent()
            val previous = state.form
            state = state.copy(form = state.form.copy(supporting = emptyList()))
            uploads.invalidateChanged(previous, state.form)
            runCurrent()
            assertFalse(target in state.uploading)
        }

    @Test
    fun an_unexpected_upload_error_releases_progress_and_keeps_the_previous_image() =
        runTest {
            val repository = GeneralEditorRepositoryFake().apply { uploadResult = { error("upload failed") } }
            val existing = GeneralStoryImage(objectKey = "existing")
            var state = GeneralEditorState(form = validGeneralForm().copy(cover = existing), loading = false)
            var failures = 0
            val uploads =
                GeneralEditorUploads(
                    repository,
                    this,
                    SessionGate(),
                    0,
                    null,
                    state = { state },
                    event = { state = reduceGeneralEditor(state, it) },
                    apply = { state = state.copy(form = it) },
                    failed = { failures++ },
                )
            uploads.upload(
                GeneralEditorIntent.Upload(GeneralFieldTarget(GeneralTab.PROFILE, GeneralField.COVER), "image"),
            )
            runCurrent()
            assertEquals(existing, state.form.cover)
            assertTrue(state.uploading.isEmpty())
            assertEquals(1, failures)
        }
}
