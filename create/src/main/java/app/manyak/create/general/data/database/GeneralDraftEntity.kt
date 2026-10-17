package app.manyak.create.general.data.database

import androidx.room.Entity
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

internal fun GeneralDraftEntity.toForm(json: Json): GeneralStoryForm? {
    if (acceptedSubmissionId != null || snapshot == null) return null
    return try {
        json.decodeFromString<GeneralDraftSnapshot>(snapshot).takeIf { it.version == 1 }?.form
    } catch (_: SerializationException) {
        null
    }
}
