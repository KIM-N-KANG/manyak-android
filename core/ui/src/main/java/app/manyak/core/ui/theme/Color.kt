package app.manyak.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Primitive — 팔레트. 시맨틱이 가리키는 단계만 둔다(전체 사다리는 design/design-tokens.json).
// private 이므로 화면 코드에서 직접 참조할 수 없다. 화면은 아래 시맨틱 이름만 쓴다.
private val Gray0 = Color(0xFFFFFFFF)
private val Gray50 = Color(0xFFFCFCFC)
private val Gray100 = Color(0xFFF5F5F5)
private val Gray150 = Color(0xFFEEEEEE)
private val Gray300 = Color(0xFF9F9F9F)
private val Gray350 = Color(0xFF969696)
private val Gray400 = Color(0xFF8D8D8D)
private val Gray500 = Color(0xFF7E7E7E)
private val Gray550 = Color(0xFF747474)
private val Gray650 = Color(0xFF666666)
private val Gray700 = Color(0xFF5E5E5E)
private val Gray750 = Color(0xFF575757)
private val Gray900 = Color(0xFF1F1F1F)
private val Gray925 = Color(0xFF191919)
private val Gray950 = Color(0xFF131313)

private val Green50 = Color(0xFFE8F8EE)
private val Green400 = Color(0xFF58C58F)
private val Green600 = Color(0xFF05A66B)
private val Green700 = Color(0xFF00995F)
private val Green800 = Color(0xFF00804B)
private val Green900 = Color(0xFF006034)
private val Green950 = Color(0xFF00411F)

private val Red50 = Color(0xFFFFECE8)
private val Red400 = Color(0xFFFF7669)
private val Red700 = Color(0xFFE23531)
private val Red800 = Color(0xFFC1191C)
private val Red900 = Color(0xFF95000A)
private val Red950 = Color(0xFF6A0000)

private val Amber50 = Color(0xFFFDF2E2)
private val Amber400 = Color(0xFFE09E32)
private val Amber700 = Color(0xFFB66E00)
private val Amber800 = Color(0xFF9A5700)
private val Amber950 = Color(0xFF512600)

private val Blue50 = Color(0xFFE9F5FF)
private val Blue400 = Color(0xFF6BB1FD)
private val Blue700 = Color(0xFF2F82D6)
private val Blue800 = Color(0xFF186AB7)
private val Blue950 = Color(0xFF003364)

/**
 * Semantic — 화면 코드가 쓰는 유일한 색 층. 이름은 토큰 정본과 1:1로 대응한다
 * (`color.text.subtle` → [textSubtle], `elevation.surface` → [surface]).
 *
 * 조합 제약은 토큰 빌드가 명도 대비로 검증했다. 특히 다음 두 조합은 쓰지 않는다.
 * - [textSubtlest]를 [backgroundNeutral] 계열 위에 — 4.5:1 미만이다. [textSubtle]을 쓴다.
 * - [textDisabled]를 읽어야 하는 텍스트에 — 배경색 위에서도 3:1 미만이다.
 */
@Immutable
data class ManyakColors(
    val brand: Color,
    val text: Color,
    val textSubtle: Color,
    val textSubtlest: Color,
    val textDisabled: Color,
    val textInverse: Color,
    val textBrand: Color,
    val textDanger: Color,
    val textWarning: Color,
    val textInformation: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val backgroundNeutral: Color,
    val backgroundNeutralPressed: Color,
    val backgroundBrandBold: Color,
    val backgroundBrandBoldPressed: Color,
    val backgroundBrandSubtle: Color,
    val backgroundDangerBold: Color,
    val backgroundDangerBoldPressed: Color,
    val backgroundDangerSubtle: Color,
    val backgroundWarningSubtle: Color,
    val backgroundInformationSubtle: Color,
    val backgroundDisabled: Color,
    val border: Color,
    val borderInput: Color,
    val borderBrand: Color,
    val borderDanger: Color,
    val borderWarning: Color,
    val borderInformation: Color,
    val borderFocused: Color,
    val stepIndicatorActive: Color,
    val progressIndicator: Color,
)

internal val ManyakLightColors =
    ManyakColors(
        brand = Green600,
        text = Gray950,
        textSubtle = Gray750,
        textSubtlest = Gray550,
        textDisabled = Gray350,
        textInverse = Gray0,
        textBrand = Green800,
        textDanger = Red800,
        textWarning = Amber800,
        textInformation = Blue800,
        surface = Gray50,
        surfaceRaised = Gray0,
        backgroundNeutral = Gray100,
        backgroundNeutralPressed = Gray150,
        backgroundBrandBold = Green800,
        backgroundBrandBoldPressed = Green900,
        backgroundBrandSubtle = Green50,
        backgroundDangerBold = Red800,
        backgroundDangerBoldPressed = Red900,
        backgroundDangerSubtle = Red50,
        backgroundWarningSubtle = Amber50,
        backgroundInformationSubtle = Blue50,
        backgroundDisabled = Gray150,
        border = Gray150,
        borderInput = Gray400,
        borderBrand = Green700,
        borderDanger = Red700,
        borderWarning = Amber700,
        borderInformation = Blue700,
        borderFocused = Green700,
        stepIndicatorActive = Gray300,
        progressIndicator = Gray400,
    )

internal val ManyakDarkColors =
    ManyakColors(
        brand = Green600,
        text = Gray50,
        textSubtle = Gray300,
        textSubtlest = Gray500,
        textDisabled = Gray700,
        textInverse = Gray0,
        textBrand = Green400,
        textDanger = Red400,
        textWarning = Amber400,
        textInformation = Blue400,
        surface = Gray950,
        surfaceRaised = Gray900,
        backgroundNeutral = Gray925,
        backgroundNeutralPressed = Gray900,
        backgroundBrandBold = Green800,
        backgroundBrandBoldPressed = Green900,
        backgroundBrandSubtle = Green950,
        backgroundDangerBold = Red800,
        backgroundDangerBoldPressed = Red900,
        backgroundDangerSubtle = Red950,
        backgroundWarningSubtle = Amber950,
        backgroundInformationSubtle = Blue950,
        backgroundDisabled = Gray900,
        border = Gray900,
        borderInput = Gray650,
        borderBrand = Green400,
        borderDanger = Red400,
        borderWarning = Amber400,
        borderInformation = Blue400,
        borderFocused = Green400,
        stepIndicatorActive = Gray650,
        progressIndicator = Gray400,
    )
