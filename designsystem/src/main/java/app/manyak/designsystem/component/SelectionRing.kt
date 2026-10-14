package app.manyak.designsystem.component

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/**
 * 고른 썸네일 바깥에 두르는 선택 테두리. 바깥에 그려 이미지를 가리지 않고, 선택이 바뀌어도 자리가
 * 흔들리지 않는다. 테두리가 [width] 만큼 밖으로 나가므로 부모가 그 자리를 비워 둬야 잘리지 않는다.
 *
 * [selection] 은 0(해제)부터 1(선택)까지의 진행값이라 테두리를 서서히 채우고 비울 수 있다.
 */
fun Modifier.selectionRing(
    selection: Float,
    color: Color,
    width: Dp,
    shape: CornerBasedShape,
): Modifier =
    drawWithContent {
        drawContent()
        if (selection > 0f) {
            val stroke = width.toPx()
            val radius = shape.topStart.toPx(size, this) + stroke / 2
            drawRoundRect(
                color = color,
                topLeft = Offset(-stroke / 2, -stroke / 2),
                size = Size(size.width + stroke, size.height + stroke),
                cornerRadius = CornerRadius(radius),
                style = Stroke(stroke),
                alpha = selection,
            )
        }
    }
