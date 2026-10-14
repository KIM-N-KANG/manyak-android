package app.manyak.designsystem.component

import android.os.Build
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp

/**
 * 표지 위 겹침 요소가 뒤의 표지를 흐려 비치게 하는 원천. 웹의 `backdrop-filter: blur()` 를 앱에서 옮긴 것이다.
 *
 * 표지 그림을 [GraphicsLayer] 에 기록해 그리면서 같은 기록을 흐린 사본으로 하나 더 들고 있고, 겹침 요소는
 * 그 사본에서 자기 자리만큼을 잘라 바탕으로 깐다. 흐림은 표지 전체에 한 번만 걸려 뱃지가 여럿이어도
 * 비용이 늘지 않고, 뱃지 가장자리도 바깥 그림을 섞어 흐려져 웹과 같은 결이 나온다.
 */
@Stable
class CoverBackdrop internal constructor(
    internal val content: GraphicsLayer,
    internal val blurred: GraphicsLayer,
) {
    internal var coordinates: LayoutCoordinates? by mutableStateOf(null)
}

/**
 * 흐림 원천을 만든다. 뒤 그림을 흐리는 `RenderEffect` 가 API 31 부터라 그 아래에서는 null 이고,
 * 겹침 요소는 흐림 대신 진한 바탕([StoryOverlayScrim])을 깐다.
 */
@Composable
fun rememberCoverBackdrop(): CoverBackdrop? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val content = rememberGraphicsLayer()
    val blurred = rememberGraphicsLayer()
    return remember(content, blurred) { CoverBackdrop(content, blurred) }
}

/** 흐림 원천이 될 그림에 건다. 겹침 요소는 이 수식어 바깥(형제)에 두어야 자기 자신을 흐리지 않는다. */
fun Modifier.coverBackdropSource(backdrop: CoverBackdrop?): Modifier =
    if (backdrop == null) {
        this
    } else {
        onPlaced { backdrop.coordinates = it }
            .drawWithCache {
                val radius = (BackdropBlurSigma.toPx() - BLUR_SIGMA_OFFSET_PX) / BLUR_SIGMA_SCALE
                val effect = BlurEffect(radius, radius, TileMode.Clamp)
                onDrawWithContent {
                    backdrop.content.record { this@onDrawWithContent.drawContent() }
                    drawLayer(backdrop.content)
                    backdrop.blurred.renderEffect = effect
                    backdrop.blurred.record { drawLayer(backdrop.content) }
                }
            }
    }

/**
 * 겹침 요소의 바탕. [backdrop] 의 흐린 사본에서 자기 자리를 [shape] 로 잘라 깔고 그 위에 [tint] 를 덮는다.
 * 원천이 없으면(API 31 미만) 흐림 대신 [StoryOverlayScrim] 을 깐다. 자식은 자르지 않는다.
 */
fun Modifier.coverBackdrop(
    backdrop: CoverBackdrop?,
    shape: Shape,
    tint: Color = CoverBackdropTint,
): Modifier = this then CoverBackdropElement(backdrop, shape, tint)

/** [StoryCover] 겹침 요소의 자리. [backdrop] 을 [coverBackdrop] 에 넘겨 표지를 흐린 바탕을 깐다. */
@LayoutScopeMarker
@Immutable
interface CoverOverlayScope : BoxScope {
    val backdrop: CoverBackdrop?
}

internal class CoverOverlayScopeImpl(
    boxScope: BoxScope,
    override val backdrop: CoverBackdrop?,
) : CoverOverlayScope,
    BoxScope by boxScope

private data class CoverBackdropElement(
    val backdrop: CoverBackdrop?,
    val shape: Shape,
    val tint: Color,
) : ModifierNodeElement<CoverBackdropNode>() {
    override fun create(): CoverBackdropNode = CoverBackdropNode(backdrop, shape, tint)

    override fun update(node: CoverBackdropNode) {
        node.backdrop = backdrop
        node.shape = shape
        node.tint = tint
        node.invalidateDraw()
    }
}

private class CoverBackdropNode(
    var backdrop: CoverBackdrop?,
    var shape: Shape,
    var tint: Color,
) : Modifier.Node(),
    DrawModifierNode,
    LayoutAwareModifierNode {
    /** 원천 좌상단에서 본 이 요소의 자리. 흐린 사본을 이만큼 끌어와 자기 자리에 맞춘다. */
    private var offset: Offset? = null

    // 스크롤로 목록 항목이 움직여도 표지 안의 상대 자리는 그대로라 다시 그리지 않는다.
    override fun onPlaced(coordinates: LayoutCoordinates) {
        val source = backdrop?.coordinates?.takeIf { it.isAttached } ?: return
        val placed = source.localPositionOf(coordinates, Offset.Zero)
        if (placed != offset) {
            offset = placed
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        val outline = shape.createOutline(size, layoutDirection, this)
        val layer = backdrop?.blurred
        val at = offset
        if (layer != null && at != null) {
            clipPath(Path().apply { addOutline(outline) }) {
                translate(-at.x, -at.y) { drawLayer(layer) }
            }
            drawOutline(outline, tint)
        } else {
            drawOutline(outline, StoryOverlayScrim)
        }
        drawContent()
    }
}

/**
 * ORIGINAL 태그(`ic_story_original_tag`)의 바탕. 웹 도안의 바탕 필드(#111414 20%)를 표지 흐림 위에 깐다.
 * 좌상단은 표지 안쪽 곡률, 우하단은 도안의 곡률이다.
 */
fun Modifier.originalTagBackdrop(backdrop: CoverBackdrop?): Modifier =
    coverBackdrop(backdrop, OriginalTagShape, OriginalTagTint)

private val OriginalTagShape = RoundedCornerShape(topStart = 15.dp, bottomEnd = 6.dp)

private val OriginalTagTint = Color(0xFF111414).copy(alpha = 0.2f)

/** 흐림 위에 덮는 옅은 그늘. 웹 `bg-black/20` 과 같다. */
val CoverBackdropTint = Color.Black.copy(alpha = 0.2f)

/** 웹 `backdrop-blur-md` 의 흐림 정도. CSS 의 blur 값은 가우스 표준편차다. */
private val BackdropBlurSigma = 12.dp

// Android 의 흐림 반경은 내부에서 `0.57735 × 반경 + 0.5px` 로 표준편차가 된다. 거꾸로 풀어 CSS 와 같은 표준편차를 낸다.
private const val BLUR_SIGMA_SCALE = 0.57735f
private const val BLUR_SIGMA_OFFSET_PX = 0.5f
