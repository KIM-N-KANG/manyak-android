package app.manyak.create.general.presentation.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.manyak.create.general.entity.GeneralField
import app.manyak.create.general.entity.GeneralFieldTarget
import app.manyak.create.general.entity.GeneralStoryImage
import app.manyak.create.general.entity.GeneralTab
import app.manyak.create.general.presentation.image.GeneralImageInput
import app.manyak.create.presentation.component.KeywordSectionLabel
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.create.R as CreateR

@Composable
internal fun GeneralFormScope.ProfileSection() {
    Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.section)) {
        ImageField(
            GeneralFieldTarget(GeneralTab.PROFILE, GeneralField.COVER),
            form.cover,
            COVER_RATIO,
            stringResource(CreateR.string.general_cover_ratio_hint),
        ) { onChange(form.copy(cover = it)) }
        Input(
            GeneralFieldTarget(GeneralTab.PROFILE, GeneralField.TITLE),
            form.title,
            TITLE_MAX_LENGTH,
            { onChange(form.copy(title = it)) },
            placeholder = stringResource(CreateR.string.general_title_placeholder),
            hint = stringResource(CreateR.string.general_title_hint),
        )
        Input(
            GeneralFieldTarget(GeneralTab.PROFILE, GeneralField.ONE_LINE_INTRO),
            form.oneLineIntro,
            INTRO_MAX_LENGTH,
            { onChange(form.copy(oneLineIntro = it)) },
            height = GeneralTextHeight.SHORT,
            placeholder = stringResource(CreateR.string.general_intro_placeholder),
            hint = stringResource(CreateR.string.general_intro_hint),
        )
    }
}

@Composable
internal fun GeneralFormScope.SettingsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.section)) {
        Input(
            GeneralFieldTarget(GeneralTab.SETTINGS, GeneralField.WORLD),
            form.world,
            WORLD_MAX_LENGTH,
            { onChange(form.copy(world = it)) },
            height = GeneralTextHeight.WORLD,
            placeholder = stringResource(CreateR.string.general_world_placeholder),
            hint = stringResource(CreateR.string.general_world_hint),
        )
        Input(
            GeneralFieldTarget(GeneralTab.SETTINGS, GeneralField.PROGRESSION),
            form.progression,
            PROGRESSION_MAX_LENGTH,
            { onChange(form.copy(progression = it)) },
            height = GeneralTextHeight.LONG,
            placeholder = stringResource(CreateR.string.general_progression_placeholder),
            hint = stringResource(CreateR.string.general_progression_hint),
        )
        LengthRatioField()
    }
}

@Composable
private fun GeneralFormScope.LengthRatioField() {
    val label = stringResource(CreateR.string.general_ratio)
    Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
        KeywordSectionLabel(label, required = false)
        val ratio = form.descriptionRatio
        GeneralInlineSlider(
            value = ratio,
            onValueChange = { onChange(form.copy(descriptionRatio = it)) },
            valueRange = RatioRange,
            label = stringResource(CreateR.string.general_ratio_description_part, ratio),
            readout = stringResource(CreateR.string.general_ratio_dialogue_part, RATIO_TOTAL - ratio),
            contentDescription = label,
            valueText = stringResource(CreateR.string.general_ratio_value, ratio, RATIO_TOTAL - ratio),
            enabled = enabled,
        )
        GeneralFieldHint(null, stringResource(CreateR.string.general_ratio_description))
    }
}

@Composable
internal fun GeneralFormScope.ImageField(
    target: GeneralFieldTarget,
    image: GeneralStoryImage?,
    ratio: Float,
    ratioHint: String,
    description: String? = null,
    onChange: (GeneralStoryImage?) -> Unit,
) {
    GeneralImageInput(
        value = image,
        label = stringResource(target.field.labelRes()),
        aspectRatio = ratio,
        ratioHint = ratioHint,
        description = description,
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

private const val COVER_RATIO = 3f / 4f
private val RatioRange = 1..9
private const val RATIO_TOTAL = 10
private const val TITLE_MAX_LENGTH = 100
private const val INTRO_MAX_LENGTH = 255
private const val WORLD_MAX_LENGTH = 5000
private const val PROGRESSION_MAX_LENGTH = 1000
