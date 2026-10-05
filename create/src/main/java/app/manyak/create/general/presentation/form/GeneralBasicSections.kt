package app.manyak.create.general.presentation.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.manyak.create.general.entity.GeneralField
import app.manyak.create.general.entity.GeneralFieldTarget
import app.manyak.create.general.entity.GeneralStoryImage
import app.manyak.create.general.entity.GeneralTab
import app.manyak.create.general.presentation.image.GeneralImageInput
import app.manyak.create.presentation.component.KeywordSectionLabel
import app.manyak.designsystem.theme.ManyakTheme
import kotlin.math.roundToInt
import app.manyak.create.R as CreateR

@Composable
internal fun GeneralFormScope.ProfileSection() {
    Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.block)) {
        ImageField(GeneralFieldTarget(GeneralTab.PROFILE, GeneralField.COVER), form.cover, 3f / 4f) {
            onChange(form.copy(cover = it))
        }
        Input(GeneralFieldTarget(GeneralTab.PROFILE, GeneralField.TITLE), form.title, 100, {
            onChange(form.copy(title = it))
        })
        Input(GeneralFieldTarget(GeneralTab.PROFILE, GeneralField.ONE_LINE_INTRO), form.oneLineIntro, 255, {
            onChange(form.copy(oneLineIntro = it))
        })
    }
}

@Composable
internal fun GeneralFormScope.SettingsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.block)) {
        Input(GeneralFieldTarget(GeneralTab.SETTINGS, GeneralField.WORLD), form.world, 5000, {
            onChange(form.copy(world = it))
        }, multiline = true, hint = stringResource(CreateR.string.general_world_hint))
        Input(GeneralFieldTarget(GeneralTab.SETTINGS, GeneralField.PROGRESSION), form.progression, 1000, {
            onChange(form.copy(progression = it))
        }, multiline = true, hint = stringResource(CreateR.string.general_progression_hint))
        val label = stringResource(CreateR.string.general_ratio)
        Column {
            KeywordSectionLabel(label, required = false)
            Text(
                stringResource(CreateR.string.general_ratio_value, form.descriptionRatio, 10 - form.descriptionRatio),
                style = ManyakTheme.typography.bodyMedium,
                color = ManyakTheme.colors.text,
            )
            Slider(
                value = form.descriptionRatio.toFloat(),
                onValueChange = { onChange(form.copy(descriptionRatio = it.roundToInt())) },
                valueRange = 1f..9f,
                steps = 7,
                enabled = enabled,
                colors =
                    SliderDefaults.colors(
                        thumbColor = ManyakTheme.colors.brand,
                        activeTrackColor = ManyakTheme.colors.brand,
                    ),
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
    }
}

@Composable
internal fun GeneralFormScope.ImageField(
    target: GeneralFieldTarget,
    image: GeneralStoryImage?,
    ratio: Float,
    onChange: (GeneralStoryImage?) -> Unit,
) {
    GeneralImageInput(
        value = image,
        label = stringResource(target.field.labelRes()),
        aspectRatio = ratio,
        enabled = enabled && target !in uploadingTargets,
        error = message(target),
        onChange = { value ->
            val path = value?.localPath
            if (path == null) onChange(value) else uploadImage(target, path)
        },
        onPickerActiveChanged = onPickerActiveChanged,
        uploading = target in uploadingTargets,
        modifier = anchor(target),
    )
}
