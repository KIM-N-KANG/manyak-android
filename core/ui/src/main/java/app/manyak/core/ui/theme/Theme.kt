package app.manyak.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal val LocalManyakColors =
    staticCompositionLocalOf<ManyakColors> { error("ManyakTheme 밖에서는 색 토큰을 쓸 수 없습니다.") }
private val LocalManyakTypography =
    staticCompositionLocalOf<ManyakTypography> { error("ManyakTheme 밖에서는 타이포 토큰을 쓸 수 없습니다.") }
private val LocalManyakSpacing =
    staticCompositionLocalOf<ManyakSpacing> { error("ManyakTheme 밖에서는 여백 토큰을 쓸 수 없습니다.") }
private val LocalManyakShapes =
    staticCompositionLocalOf<ManyakShapes> { error("ManyakTheme 밖에서는 모서리 토큰을 쓸 수 없습니다.") }
private val LocalManyakSizes =
    staticCompositionLocalOf<ManyakSizes> { error("ManyakTheme 밖에서는 크기 토큰을 쓸 수 없습니다.") }
internal val LocalManyakMotion =
    staticCompositionLocalOf<ManyakMotion> { error("ManyakTheme 밖에서는 모션 토큰을 쓸 수 없습니다.") }

/**
 * 앱 전역 테마. 디자인 토큰을 [CompositionLocal][staticCompositionLocalOf]로 내린다.
 *
 * 화면 코드는 [ManyakTheme]의 접근자만 쓴다. M3의 `MaterialTheme.colorScheme`·`typography`도 함께
 * 채우지만 이는 M3 컴포넌트 기본값(보라색·Roboto)이 새는 것을 막는 안전망일 뿐 정본이 아니다.
 *
 * 기기 배경화면에서 색을 가져오는 dynamic color는 쓰지 않는다. 브랜드 색이 덮이기 때문이다.
 */
@Composable
fun ManyakTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) ManyakDarkColors else ManyakLightColors
    CompositionLocalProvider(
        LocalManyakColors provides colors,
        LocalManyakTypography provides ManyakDefaultTypography,
        LocalManyakSpacing provides ManyakDefaultSpacing,
        LocalManyakShapes provides ManyakDefaultShapes,
        LocalManyakSizes provides ManyakDefaultSizes,
        LocalManyakMotion provides ManyakDefaultMotion,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(),
            typography = ManyakDefaultTypography.toMaterialTypography(),
            shapes = ManyakDefaultShapes.toMaterialShapes(),
        ) {
            CompositionLocalProvider(
                LocalTextStyle provides ManyakDefaultTypography.bodyMedium,
                LocalContentColor provides colors.text,
                // 눌림은 리플 하나로 말한다 — 색·농도는 토큰이 정하고, 호버·포커스·드래그는 그리지 않는다.
                // 탭 바처럼 눌림을 두지 않는 자리는 그 하위 트리에서 다시 끈다.
                LocalRippleConfiguration provides colors.toRippleConfiguration(),
                content = content,
            )
        }
    }
}

/** 화면 코드가 토큰을 읽는 유일한 통로. */
object ManyakTheme {
    val colors: ManyakColors
        @Composable @ReadOnlyComposable
        get() = LocalManyakColors.current

    val typography: ManyakTypography
        @Composable @ReadOnlyComposable
        get() = LocalManyakTypography.current

    val spacing: ManyakSpacing
        @Composable @ReadOnlyComposable
        get() = LocalManyakSpacing.current

    val shapes: ManyakShapes
        @Composable @ReadOnlyComposable
        get() = LocalManyakShapes.current

    val sizes: ManyakSizes
        @Composable @ReadOnlyComposable
        get() = LocalManyakSizes.current

    val motion: ManyakMotion
        @Composable @ReadOnlyComposable
        get() = LocalManyakMotion.current
}

// 아래 세 변환은 M3 슬롯을 토큰에서 파생시키는 안전망이다. 라이트·다크 구분은 토큰이 이미 하므로
// 두 모드 모두 lightColorScheme 팩토리로 만든다(팩토리는 슬롯 채우기 용도일 뿐이다).
private fun ManyakColors.toMaterialColorScheme(): ColorScheme =
    lightColorScheme(
        primary = backgroundBrandBold,
        onPrimary = textInverse,
        primaryContainer = backgroundBrandSubtle,
        onPrimaryContainer = textBrand,
        inversePrimary = brand,
        secondary = backgroundBrandBold,
        onSecondary = textInverse,
        secondaryContainer = backgroundBrandSubtle,
        onSecondaryContainer = textBrand,
        tertiary = backgroundBrandBold,
        onTertiary = textInverse,
        tertiaryContainer = backgroundBrandSubtle,
        onTertiaryContainer = textBrand,
        background = surface,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = backgroundNeutral,
        onSurfaceVariant = textSubtle,
        surfaceTint = Color.Transparent,
        inverseSurface = text,
        inverseOnSurface = surface,
        error = backgroundDangerBold,
        onError = textInverse,
        errorContainer = backgroundDangerSubtle,
        onErrorContainer = textDanger,
        outline = borderInput,
        outlineVariant = border,
        scrim = Color.Black,
        surfaceBright = surfaceRaised,
        surfaceDim = surface,
        surfaceContainer = backgroundNeutral,
        surfaceContainerHigh = surfaceRaised,
        surfaceContainerHighest = surfaceRaised,
        surfaceContainerLow = surface,
        surfaceContainerLowest = surface,
    )

private fun ManyakTypography.toMaterialTypography(): Typography =
    Typography(
        displayLarge = this.headlineSmall,
        displayMedium = this.headlineSmall,
        displaySmall = this.headlineSmall,
        headlineLarge = this.headlineSmall,
        headlineMedium = this.headlineSmall,
        headlineSmall = this.headlineSmall,
        titleLarge = this.titleLarge,
        titleMedium = this.titleMedium,
        titleSmall = this.labelLarge,
        bodyLarge = this.bodyLarge,
        bodyMedium = this.bodyMedium,
        bodySmall = this.bodySmall,
        labelLarge = this.labelLarge,
        labelMedium = this.labelSmall,
        labelSmall = this.labelSmall,
    )

private fun ManyakShapes.toMaterialShapes(): Shapes =
    Shapes(
        extraSmall = thumbnail,
        small = thumbnail,
        medium = control,
        large = card,
        extraLarge = overlay,
    )

/** 눌림 리플. [ManyakColors.overlayPressed] 의 색을 쓰고 그 알파를 눌림 농도로 삼는다. */
private fun ManyakColors.toRippleConfiguration(): RippleConfiguration =
    RippleConfiguration(
        color = overlayPressed.copy(alpha = 1f),
        rippleAlpha =
            RippleAlpha(
                pressedAlpha = overlayPressed.alpha,
                focusedAlpha = 0f,
                hoveredAlpha = 0f,
                draggedAlpha = 0f,
            ),
    )
