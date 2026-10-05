package app.manyak.create.general.presentation.image

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
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
import app.manyak.create.general.entity.GeneralStoryImage
import app.manyak.designsystem.component.ManyakNeutralButton
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
    enabled: Boolean,
    onChange: (GeneralStoryImage?) -> Unit,
    modifier: Modifier = Modifier,
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
        GeneralImageCropDialog(
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

@Composable
private fun ImagePreview(
    value: GeneralStoryImage?,
    label: String,
    ratio: Float,
    preparing: Boolean,
) {
    Box(
        Modifier
            .width(ManyakTheme.sizes.studioCoverWidth)
            .aspectRatio(ratio)
            .clip(ManyakTheme.shapes.control)
            .background(ManyakTheme.colors.backgroundNeutral),
        contentAlignment = Alignment.Center,
    ) {
        if (value != null) {
            AsyncImage(
                model = value.localPath?.let(::File) ?: value.previewUrl,
                contentDescription = label,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                painterResource(DesignR.drawable.ic_image),
                contentDescription = null,
                tint = ManyakTheme.colors.textDisabled,
                modifier = Modifier.size(ManyakTheme.sizes.tabIcon),
            )
        }
        if (preparing) {
            CircularProgressIndicator(
                Modifier.size(ManyakTheme.sizes.icon),
                color = ManyakTheme.colors.brand,
            )
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

@Composable
private fun ImageInputContent(
    value: GeneralStoryImage?,
    label: String,
    aspectRatio: Float,
    preparing: Boolean,
    enabled: Boolean,
    failed: Boolean,
    error: String?,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
        Text(label, style = ManyakTheme.typography.labelLarge, color = ManyakTheme.colors.text)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
        ) {
            ImagePreview(value, label, aspectRatio, preparing)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
                ManyakNeutralButton(
                    label = stringResource(CreateR.string.general_image_choose),
                    enabled = enabled && !preparing,
                    onClick = onChoose,
                )
                if (value != null) {
                    ManyakTextButton(enabled = enabled, onClick = { onRemove() }) {
                        Text(stringResource(CreateR.string.general_image_remove), color = ManyakTheme.colors.textSubtle)
                    }
                }
                Text(
                    stringResource(CreateR.string.general_image_hint),
                    style = ManyakTheme.typography.bodySmall,
                    color = ManyakTheme.colors.textSubtle,
                )
            }
        }
        val message = error ?: if (failed) stringResource(CreateR.string.general_image_invalid) else null
        if (message !=
            null
        ) {
            Text(message, color = ManyakTheme.colors.textDanger, style = ManyakTheme.typography.bodySmall)
        }
    }
}
