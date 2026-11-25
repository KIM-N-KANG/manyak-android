package app.manyak.create.general.presentation.image

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.manyak.create.general.entity.GeneralStoryImage
import app.manyak.create.general.presentation.form.GeneralFieldHint
import app.manyak.designsystem.component.ManyakAddButton
import app.manyak.designsystem.component.ManyakTextButton
import app.manyak.designsystem.theme.ManyakTheme
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.IOException
import app.manyak.create.R as CreateR
import app.manyak.designsystem.R as DesignR

@Composable
internal fun GeneralImageInput(
    value: GeneralStoryImage?,
    label: String,
    aspectRatio: Float,
    ratioHint: String,
    enabled: Boolean,
    onChange: (GeneralStoryImage?) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    error: String? = null,
    uploading: Boolean = false,
    onPickerActiveChanged: (Boolean) -> Unit = {},
) {
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var cropPath by rememberSaveable { mutableStateOf<String?>(null) }
    var failed by rememberSaveable { mutableStateOf(false) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            onPickerActiveChanged(false)
            if (uri != null) {
                pendingUri = uri.toString()
                failed = false
            }
        }
    PrepareSelectedImage(pendingUri, { path ->
        cropPath = path
        pendingUri = null
    }, {
        failed = true
        pendingUri = null
    })
    ImageInputContent(
        value,
        label,
        aspectRatio,
        ratioHint,
        description,
        pendingUri != null || uploading,
        enabled,
        failed,
        error,
        onChoose = {
            onPickerActiveChanged(true)
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onRemove = { onChange(null) },
        modifier = modifier,
    )
    cropPath?.let { path ->
        GeneralImageCropSheet(
            path = path,
            aspectRatio = aspectRatio,
            onDismiss = {
                File(path).delete()
                cropPath = null
            },
            onCropped = { result ->
                onChange(GeneralStoryImage(localPath = result))
                File(path).delete()
                cropPath = null
            },
        )
    }
}

/** 고른 이미지 미리보기. 누르면 버튼과 같이 이미지를 고르며, 같은 동작이 둘이라 접근성 트리에서는 뺀다. */
@Composable
private fun ImagePreview(
    value: GeneralStoryImage?,
    ratio: Float,
    preparing: Boolean,
    hasError: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = ManyakTheme.shapes.thumbnail
    Box(
        Modifier
            .width(ManyakTheme.sizes.studioCoverWidth)
            .aspectRatio(ratio)
            .clip(shape)
            .background(ManyakTheme.colors.backgroundNeutral)
            .border(
                ManyakTheme.sizes.inputBorderWidth,
                if (hasError) ManyakTheme.colors.borderDanger else ManyakTheme.colors.border,
                shape,
            ).clickable(enabled = enabled && !preparing, onClick = onClick)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        if (value != null) {
            AsyncImage(
                model = value.localPath?.let(::File) ?: value.previewUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                painterResource(DesignR.drawable.ic_manyak_symbol),
                contentDescription = null,
                tint = ManyakTheme.colors.textSubtlest,
                modifier = Modifier.size(PlaceholderSymbolSize),
            )
        }
        if (preparing) {
            Box(
                Modifier.fillMaxSize().background(ManyakTheme.colors.surface.copy(alpha = UPLOADING_SCRIM_ALPHA)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    Modifier.size(ManyakTheme.sizes.tabIcon),
                    color = ManyakTheme.colors.text,
                    strokeWidth = ManyakTheme.sizes.selectionBorderWidth,
                )
            }
        }
    }
}

@Composable
private fun PrepareSelectedImage(
    pendingUri: String?,
    onPrepared: (String) -> Unit,
    onFailure: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(pendingUri) {
        val uri = pendingUri ?: return@LaunchedEffect
        try {
            onPrepared(GeneralImageCropper.prepare(context, Uri.parse(uri)))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            onFailure()
        } catch (_: SecurityException) {
            onFailure()
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun ImageInputContent(
    value: GeneralStoryImage?,
    label: String,
    aspectRatio: Float,
    ratioHint: String,
    description: String?,
    preparing: Boolean,
    enabled: Boolean,
    failed: Boolean,
    error: String?,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier,
) {
    val message = error ?: if (failed) stringResource(CreateR.string.general_image_invalid) else null
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
        Text(label, style = ManyakTheme.typography.labelLarge, color = ManyakTheme.colors.text)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
        ) {
            ImagePreview(value, aspectRatio, preparing, message != null, enabled, onChoose)
            Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
                ImageButtons(value != null, enabled && !preparing, label, onChoose, onRemove)
                Column {
                    Text(ratioHint, style = ManyakTheme.typography.bodyMedium, color = ManyakTheme.colors.textSubtle)
                    Text(
                        stringResource(CreateR.string.general_image_hint),
                        style = ManyakTheme.typography.bodySmall,
                        color = ManyakTheme.colors.textSubtle,
                    )
                }
            }
        }
        GeneralFieldHint(message, description)
    }
}

@Composable
private fun ImageButtons(
    hasImage: Boolean,
    enabled: Boolean,
    label: String,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
) {
    val removeLabel = stringResource(CreateR.string.general_image_remove)
    val removeDescription = stringResource(CreateR.string.general_remove_named, label)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        ManyakAddButton(
            label =
                stringResource(
                    if (hasImage) CreateR.string.general_image_change else CreateR.string.general_image_add,
                ),
            enabled = enabled,
            onClick = onChoose,
            iconRes = CreateR.drawable.ic_image_upload,
        )
        if (hasImage) {
            ManyakTextButton(
                modifier = Modifier.semantics { contentDescription = removeDescription },
                enabled = enabled,
                onClick = onRemove,
            ) {
                Text(removeLabel, style = ManyakTheme.typography.labelLarge, color = ManyakTheme.colors.textSubtle)
            }
        }
    }
}

private val PlaceholderSymbolSize = 32.dp
private const val UPLOADING_SCRIM_ALPHA = 0.6f
