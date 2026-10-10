// 소품의 자리와 크기는 표지 폭에 대한 비율로 한 번씩만 쓰는 연출 수치다.
@file:Suppress("MagicNumber")

package app.manyak.studio.presentation.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import app.manyak.designsystem.component.drawManyakMascot
import app.manyak.designsystem.mascot.StagePalette
import app.manyak.designsystem.mascot.blinkOpenness
import app.manyak.designsystem.mascot.drawStageDots
import app.manyak.designsystem.mascot.drawStageShadow
import app.manyak.designsystem.mascot.rememberStageMillis
import app.manyak.designsystem.mascot.stagePalette
import app.manyak.designsystem.theme.ManyakTheme

/**
 * 스토리 완성 중 표지. 빈 표지 바탕에 옅은 점을 깔고, 로고 마스코트가 키워드를 고르고 · 스토리라인을 고르고 ·
 * 에너지 드링크를 마시고 원고를 쓰고 · 베레모를 쓰고 붓으로 인물화를 그린 뒤 뛰어다니는 막을, 사이사이 쉬어
 * 가며 이어서 연기한다. 안무는 [completingMoment] 가 정하고 여기서는 그리기만 한다.
 *
 * 크기는 받은 자리에 맞추고 모든 소품을 폭에 대한 비율로 그려, 표지 폭이 바뀌어도 장면이 같은 구도로 남는다.
 */
@Composable
internal fun StoryCompletingStage(
    label: String,
    modifier: Modifier = Modifier,
) {
    val time by rememberStageMillis(stillMillis = 0)
    val palette = stagePalette()
    val dotGap = ManyakTheme.sizes.generationDotGap
    val dotRadius = ManyakTheme.sizes.generationDotRadius

    Canvas(
        modifier =
            modifier.semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            },
    ) {
        val millis = time
        val (act, actMillis, pose) = completingMoment(millis)
        val unit = size.width
        drawStageDots(palette.ink.copy(alpha = 0.25f), dotGap.toPx(), dotRadius.toPx())
        when (act) {
            CompletingAct.KEYWORDS -> drawKeywordChips(actMillis, unit, palette)
            CompletingAct.STORYLINE -> drawStorylines(actMillis, unit, palette)
            CompletingAct.TYPING -> drawManuscript(actMillis, unit, palette)
            CompletingAct.PAINTING -> drawEasel(actMillis, unit, palette)
            else -> Unit
        }
        // 칩을 밟고 있을 때는 바닥 그림자가 허공에 뜬 것처럼 보여 두지 않는다.
        if (act != CompletingAct.KEYWORDS) drawStageShadow(pose, unit, MASCOT_SIZE, FLOOR, palette.brand)
        drawManyakMascot(
            feet = Offset(pose.x, pose.y) * unit,
            size = MASCOT_SIZE * unit,
            color = palette.brand,
            scaleX = pose.scaleX,
            scaleY = pose.scaleY,
            rotationDegrees = pose.rotation,
            look = pose.look,
            eyeOpenness = blinkOpenness(millis) * (1f - 0.9f * pose.squint),
            eyes = pose.eyes,
        )
        if (act == CompletingAct.PAINTING) drawPainterGear(actMillis, unit, palette, pose, millis)
        if (act == CompletingAct.ENERGY) drawEnergyDrink(actMillis, unit, palette, pose)
        drawMoodProps(act, actMillis, unit, palette, pose, millis)
        if (act == CompletingAct.TYPING) drawKeyboard(actMillis, unit, palette)
    }
}

/** 원고·키보드·카드·칩이 함께 쓰는 종이. 다크 테마에서도 바탕과 갈리도록 진한 경계로 두른다. */
internal fun DrawScope.drawCard(
    rect: Rect,
    unit: Float,
    palette: StagePalette,
    outline: Color = palette.ink,
    fill: Color = palette.paper,
    corner: Float = 0.04f,
) {
    val topLeft = rect.topLeft * unit
    val cardSize = rect.size * unit
    val radius = CornerRadius(corner * unit)
    drawRoundRect(fill, topLeft, cardSize, radius)
    drawRoundRect(outline, topLeft, cardSize, radius, style = Stroke(width = 0.008f * unit))
}
