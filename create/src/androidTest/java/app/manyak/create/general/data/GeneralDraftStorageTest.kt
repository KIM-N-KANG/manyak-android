package app.manyak.create.general.data

import android.database.sqlite.SQLiteException
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.user.UserProfileRepository
import app.manyak.common.entity.user.AccountStatus
import app.manyak.common.entity.user.UserProfile
import app.manyak.create.data.database.ManyakDatabase
import app.manyak.create.general.data.database.GeneralDraftDao
import app.manyak.create.general.data.database.GeneralDraftRoomStore
import app.manyak.create.general.entity.GeneralStoryForm
import app.manyak.create.general.entity.GeneralStoryImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class GeneralDraftStorageTest {
    @Test
    fun accountIsolationAndImageLifetimeSurviveDraftRestore() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val owner = "storage-test-${UUID.randomUUID()}"
            val profile = StorageProfileRepository(owner)
            val files = GeneralImageFiles(context, Dispatchers.IO)
            val created = mutableListOf<File>()
            val room = Room.inMemoryDatabaseBuilder(context, ManyakDatabase::class.java).build()
            try {
                val store = GeneralDraftRoomStore(room.generalDraftDao(), profile, Json, Dispatchers.IO, files)
                val first = files.persist(owner, byteArrayOf(1, 2, 3), "image/png").also(created::add)
                val image = first.asImage()
                val form = GeneralStoryForm(title = "보존할 초안", cover = image)
                assertTrue(store.save("draft", form))
                assertEquals(form, store.read("draft")?.form)
                profile.profile.value = null
                assertNull(store.read("draft"))
                assertTrue(store.drafts.first().isEmpty())
                assertTrue(first.exists())

                profile.switchTo("$owner-other")
                assertNull(store.read("draft"))
                assertTrue(store.save("draft", GeneralStoryForm(title = "다른 계정")))
                profile.switchTo(owner)
                assertEquals(form, store.read("draft")?.form)
                val second = files.persist(owner, byteArrayOf(4, 5, 6), "image/png").also(created::add)
                val changed = form.copy(cover = second.asImage())
                assertTrue(store.save("draft", changed))
                assertFalse(first.exists())
                assertTrue(second.exists())

                assertTrue(store.markAccepted("draft", "submission"))
                assertNull(store.read("draft"))
                assertTrue(store.drafts.first().isEmpty())
                assertFalse(store.save("draft", changed))
                assertTrue(second.exists())
                val restored = GeneralDraftRoomStore(room.generalDraftDao(), profile, Json, Dispatchers.IO, files)
                assertEquals("submission", restored.acceptedSubmissionId("draft"))
                assertFalse(restored.save("draft", changed))
                files.discard(second.asImage())
                assertFalse(second.exists())
                profile.switchTo("$owner-other")
                assertEquals("다른 계정", restored.read("draft")?.form?.title)
            } finally {
                room.close()
                created.forEach { it.delete() }
                created.firstOrNull()?.parentFile?.delete()
            }
        }

    @Test
    fun acceptedResponseStillBlocksResubmitWhileDatabaseIsUnavailable() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val room = Room.inMemoryDatabaseBuilder(context, ManyakDatabase::class.java).build()
            var attempted = false
            val dao =
                object : GeneralDraftDao by room.generalDraftDao() {
                    override suspend fun accept(
                        ownerId: String,
                        draftId: String,
                        submissionId: String,
                        now: Long,
                    ) {
                        attempted = true
                        throw SQLiteException("DB unavailable")
                    }
                }
            try {
                val store =
                    GeneralDraftRoomStore(
                        dao,
                        StorageProfileRepository("owner"),
                        Json,
                        Dispatchers.IO,
                        GeneralImageFiles(context, Dispatchers.IO),
                    )
                assertFalse(store.markAccepted("draft", "accepted"))
                assertTrue(attempted)
                assertEquals("accepted", store.acceptedSubmissionId("draft"))
                assertFalse(store.save("draft", GeneralStoryForm(title = "이전 입력")))
            } finally {
                room.close()
            }
        }
}

private fun File.asImage(): GeneralStoryImage =
    GeneralStoryImage(
        objectKey = "uploaded/$name",
        previewUrl = Uri.fromFile(this).toString(),
        localPath = absolutePath,
    )

private class StorageProfileRepository(
    ownerId: String,
) : UserProfileRepository {
    override val profile = MutableStateFlow<UserProfile?>(profileFor(ownerId))

    fun switchTo(ownerId: String) {
        profile.value = profileFor(ownerId)
    }

    override suspend fun refresh(): DomainResult<UserProfile> =
        profile.value?.let { DomainResult.Success(it) } ?: DomainResult.Failure(DomainError.Unauthorized)
}

private fun profileFor(ownerId: String): UserProfile =
    UserProfile(ownerId, "테스트", null, null, AccountStatus.ACTIVE, 0, false, emptyList())
