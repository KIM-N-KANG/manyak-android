package app.manyak.create.general.data.database

import androidx.room.Entity
import app.manyak.create.general.entity.GeneralStoredDraft
import app.manyak.create.general.entity.GeneralStoryForm
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Entity(tableName = "general_story_draft", primaryKeys = ["ownerId", "draftId"])
data class GeneralDraftEntity(
    val ownerId: String,
    val draftId: String,
    val snapshot: String?,
    val createdAt: Long,
    val acceptedSubmissionId: String? = null,
)

@Serializable
internal data class GeneralDraftSnapshot(
    val version: Int = 1,
    val form: GeneralStoryForm,
)

internal fun GeneralDraftEntity.toDomain(json: Json): GeneralStoredDraft? {
    if (acceptedSubmissionId != null || snapshot == null) return null
    return try {
        val saved = json.decodeFromString<GeneralDraftSnapshot>(snapshot)
        if (saved.version != 1) return null
        GeneralStoredDraft(draftId, saved.form, createdAt)
    } catch (_: SerializationException) {
        null
    }
}
