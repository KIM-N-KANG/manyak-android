// 말하는 동작의 계수는 웹 안내 마스코트와 같은 값을 한 번씩만 쓰는 연출 수치다.
@file:Suppress("MagicNumber")

package app.manyak.designsystem.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import app.manyak.designsystem.mascot.Act
import app.manyak.designsystem.mascot.Choreography
import app.manyak.designsystem.mascot.MascotEyes
import app.manyak.designsystem.mascot.MascotPose
import app.manyak.designsystem.mascot.Move
import app.manyak.designsystem.mascot.blinkOpenness
import app.manyak.designsystem.mascot.rememberStageMillis
import app.manyak.designsystem.mascot.stand
import app.manyak.designsystem.theme.ManyakTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import app.manyak.designsystem.R as DesignsystemR

/**
 * 간편 제작 첫 진입 안내의 대화창. 둥근 프로필에서 마냑 마스코트가 말하듯 움직이고 단계 문구와 버튼을 둔다.
 * 문구와 버튼은 단계를 아는 화면이 넘긴다.
 *
 * 웹과 같은 모양으로 화면 폭을 거의 다 쓰는 카드를 50% 딤 위 가운데에 띄운다. 단계가 바뀌어도 부르는 쪽이 같은 자리에서
 * 다시 부르면 창은 그대로 두고 문구만 바꿔 끼운다. 창을 닫고 새로 열면 딤이 두 번 페이드돼 화면이 깜빡인다.
 *
 * 바깥을 눌러도 닫지 않는다. 버튼으로 닫을 때만 안내를 마친 것으로 기록하기 때문이다. 뒤로가기는 보조 버튼과 같고,
 * 보조 버튼이 없는 단계는 주 버튼과 같다.
 *
 * @param secondaryLabel 보조 버튼 문구. 없으면 주 버튼 하나만 둔다.
 */
@Composable
fun CreateTutorialDialog(
    message: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
) {
    val label = stringResource(DesignsystemR.string.create_tutorial_label)
    val scrim = ManyakTheme.colors.tourScrim
    Dialog(
        onDismissRequest = if (secondaryLabel == null) onPrimary else onSecondary,
        properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        // 창 딤을 웹과 같은 검정 50%로 맞춘다. 카드는 아래에서 따로 나타나므로 창 전환은 끈다.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(scrim.alpha)
            window?.setWindowAnimations(0)
        }
        val entrance = remember { Animatable(0f) }
        LaunchedEffect(entrance) {
            entrance.animateTo(1f, tween(ENTER_MILLIS, easing = LinearOutSlowInEasing))
        }
        Column(
            modifier =
                modifier
                    .padding(horizontal = ManyakTheme.spacing.component)
                    .graphicsLayer {
                        alpha = entrance.value
                        val scale = ENTER_SCALE + (1f - ENTER_SCALE) * entrance.value
                        scaleX = scale
                        scaleY = scale
                    }.fillMaxWidth()
                    .clip(ManyakTheme.shapes.overlay)
                    .background(ManyakTheme.colors.surface)
                    .border(
                        ManyakTheme.sizes.inputBorderWidth,
                        ManyakTheme.colors.borderOverlay,
                        ManyakTheme.shapes.overlay,
                    ).padding(ManyakTheme.spacing.gutter)
                    .semantics { paneTitle = label },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component)) {
                TalkingMascot()
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(DesignsystemR.string.create_tutorial_speaker),
                        style = ManyakTheme.typography.labelLarge,
                        color = ManyakTheme.colors.textBrand,
                    )
                    Crossfade(
                        targetState = message,
                        modifier = Modifier.padding(top = ManyakTheme.spacing.inline),
                        animationSpec = tween(ManyakTheme.motion.elementEnterMillis),
                        label = "create-tutorial-message",
                    ) { text ->
                        Text(text = text, style = ManyakTheme.typography.bodyLarge, color = ManyakTheme.colors.text)
                    }
                }
            }
            TutorialButtons(primaryLabel, onPrimary, secondaryLabel, onSecondary)
        }
    }
}

@Composable
private fun TutorialButtons(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String?,
    onSecondary: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = ManyakTheme.spacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (secondaryLabel != null) {
            ManyakTextButton(
                onClick = onSecondary,
                modifier = Modifier.heightIn(min = ManyakTheme.sizes.controlCompact),
            ) {
                Text(text = secondaryLabel, style = ManyakTheme.typography.labelLarge, color = ManyakTheme.colors.text)
            }
        }
        Button(
            onClick = onPrimary,
            modifier = Modifier.heightIn(min = ManyakTheme.sizes.controlCompact),
            shape = ManyakTheme.shapes.control,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = ManyakTheme.colors.brand,
                    contentColor = ManyakTheme.colors.textInverse,
                ),
        ) {
            Text(text = primaryLabel, style = ManyakTheme.typography.labelLarge)
        }
    }
}

/** 장식이라 보조 기술에 읽히지 않는다. 기기에서 애니메이션을 끄면 서 있는 첫 자세에 멈춘다. */
@Composable
private fun TalkingMascot() {
    val time by rememberStageMillis(stillMillis = 0)
    val color = ManyakTheme.colors.brand
    Canvas(
        modifier =
            Modifier
                .size(ManyakTheme.sizes.mascotAvatar)
                .clip(ManyakTheme.shapes.pill)
                .background(ManyakTheme.colors.backgroundBrandSubtle),
    ) {
        val millis = time
        val pose = TalkChoreography.momentAt(millis).pose
        drawManyakMascot(
            feet = Offset(pose.x, pose.y) * size.width,
            size = TALK_MASCOT_SIZE * size.width,
            color = color,
            scaleX = pose.scaleX,
            scaleY = pose.scaleY,
            rotationDegrees = pose.rotation,
            look = pose.look,
            eyeOpenness = blinkOpenness(millis),
            eyes = pose.eyes,
        )
    }
}

/** 카드 등장. 웹과 같이 0.95배에서 커지며 나타난다. */
private const val ENTER_MILLIS = 250
private const val ENTER_SCALE = 0.95f

/** 프로필 폭 대비 마스코트 크기. 통통 튀어도 둥근 프로필 모서리에 몸이 잘리지 않는다. */
private const val TALK_MASCOT_SIZE = 0.66f

private val TalkHome = Offset(0.5f, 0.86f)

/** 말하는 동안 몸이 뜨는 가장 큰 높이(프로필 폭 단위). */
private const val TALK_HOP = 0.035f

/** 음절마다 살짝 떠올라 늘어나고 좌우로 기울며 말한다. 처음과 끝은 가만히 서 있어 앞뒤 동작과 이어진다. */
private fun talk(
    millis: Int,
    syllables: Int,
    eyes: MascotEyes,
) = Move<Nothing>(millis) { f ->
    val settle = sin(PI.toFloat() * f)
    val beat = abs(sin(PI.toFloat() * f * syllables)) * settle
    MascotPose(
        x = TalkHome.x,
        y = TalkHome.y - TALK_HOP * beat,
        scaleX = 1f - 0.03f * beat,
        scaleY = 1f + 0.04f * beat,
        rotation = 3f * sin(PI.toFloat() * f * syllables / 2f) * settle,
        look = Offset(0.2f, 0.3f),
        eyes = eyes,
    )
}

/** 5초 동안 두 번 말하고 쉰다. */
private val TalkChoreography =
    Choreography(
        listOf(
            Act(
                kind = Unit,
                moves =
                    listOf(
                        talk(1800, 7, MascotEyes.ROUND),
                        stand(900, TalkHome),
                        talk(1500, 6, MascotEyes.SMILE),
                        stand(800, TalkHome),
                    ),
            ),
        ),
    )
