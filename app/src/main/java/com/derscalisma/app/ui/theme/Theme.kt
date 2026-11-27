package com.derscalisma.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derscalisma.app.data.Catalog
import com.derscalisma.app.data.ThemeDef
import com.derscalisma.app.i18n.I18n

/**
 * Tasarım sistemi ("Sakin"): anlamlı renk jetonları. Ekranlarda sabit renk (hex) yazma; buradan al.
 * - accent: tek vurgu rengi (temaya göre değişir).
 * - success: hedef tamam, odaklı, mola. warning: seri, plandan geride, risk. danger: silme, dağınık, ceza.
 */
@Immutable
data class AppColors(
    val dark: Boolean,
    /** Ekran zemini. */
    val canvas: Color,
    /** Kartlar. */
    val surface: Color,
    /** Kart içi yükseltilmiş alanlar: adım düğmesi, segment izi, giriş alanları, alt sayfalar. */
    val surfaceRaised: Color,
    /** Seçili segment, snackbar. */
    val surfaceHigh: Color,
    /** Ayırıcı çizgiler, halka ve çubuk izleri. */
    val hairline: Color,
    /** Etkileşimli kontrol çerçeveleri. */
    val outline: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    /** Ders parası simgesi. */
    val coin: Color,
    val coinRim: Color,
    /** Seri alevi. */
    val flame: Color,
    val flameCore: Color,
    val scrim: Color,
)

val LocalAppColors = staticCompositionLocalOf { buildColors(Catalog.theme("")) }

/** Boşluk jetonları (4'lük ızgara). */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
    /** Ekran kenar boşluğu (dar / geniş). */
    val gutter = 16.dp
    val gutterWide = 24.dp
    /** Tek sütunlu içerik en fazla bu kadar geniş olur. */
    val maxContent = 640.dp
    /** Ana düğme en fazla bu kadar geniş olur. */
    val maxButton = 400.dp
}

/** Köşe yarıçapları: 8 küçük (çip), 12 iç kutu, 16 kart, 28 alt sayfa / diyalog. */
object Radius {
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 28.dp
}

private fun hex(v: Long) = Color(v)

fun buildColors(t: ThemeDef): AppColors {
    val accentRaw = hex(t.primary)
    return if (t.dark) {
        val canvas = hex(t.background)
        val surface = hex(t.surface)
        val white = Color.White
        AppColors(
            dark = true,
            canvas = canvas,
            surface = surface,
            surfaceRaised = lerp(surface, white, 0.055f),
            surfaceHigh = lerp(surface, white, 0.11f),
            hairline = lerp(surface, white, 0.09f),
            outline = lerp(surface, white, 0.34f),
            text = Color(0xFFF1F3F7),
            textSecondary = Color(0xFFA9B0BD),
            textTertiary = Color(0xFF8A92A1),
            accent = accentRaw,
            onAccent = Color(0xFF0F1115),
            accentContainer = lerp(surface, accentRaw, 0.24f),
            onAccentContainer = lerp(accentRaw, white, 0.55f),
            success = Color(0xFF5FD3A3),
            successContainer = lerp(surface, Color(0xFF5FD3A3), 0.20f),
            warning = Color(0xFFF5A35C),
            warningContainer = lerp(surface, Color(0xFFF5A35C), 0.20f),
            danger = Color(0xFFFF7B7B),
            dangerContainer = lerp(surface, Color(0xFFFF7B7B), 0.20f),
            coin = Color(0xFFF2C94C),
            coinRim = Color(0xFFB7861B),
            flame = Color(0xFFFF8A3D),
            flameCore = Color(0xFFFFD36B),
            scrim = Color.Black.copy(alpha = 0.6f),
        )
    } else {
        val canvas = hex(t.background)
        val surface = hex(t.surface)
        // Açık temada vurgu beyaz zeminde okunaklı olmalı (en az 4.5:1): gerekirse koyulaştır.
        var accent = accentRaw
        while (accent.luminance() > 0.18f) accent = lerp(accent, Color.Black, 0.08f)
        AppColors(
            dark = false,
            canvas = canvas,
            surface = surface,
            surfaceRaised = lerp(canvas, Color.Black, 0.025f),
            surfaceHigh = lerp(canvas, Color.Black, 0.06f),
            hairline = lerp(canvas, Color.Black, 0.075f),
            outline = Color(0xFF8B919C),
            text = Color(0xFF14161B),
            textSecondary = Color(0xFF5A6170),
            textTertiary = Color(0xFF646B78),
            accent = accent,
            onAccent = Color.White,
            accentContainer = lerp(Color.White, accent, 0.12f),
            onAccentContainer = lerp(accent, Color.Black, 0.45f),
            success = Color(0xFF0E7F55),
            successContainer = Color(0xFFE2F4EC),
            warning = Color(0xFFA85A0A),
            warningContainer = Color(0xFFFBEBDB),
            danger = Color(0xFFC2362F),
            dangerContainer = Color(0xFFFBE4E2),
            coin = Color(0xFFF2C94C),
            coinRim = Color(0xFFB7861B),
            flame = Color(0xFFF2711C),
            flameCore = Color(0xFFFFC94D),
            scrim = Color.Black.copy(alpha = 0.32f),
        )
    }
}

private val tnum = "tnum"

/** Yazı ölçeği. Ekranlarda fontSize / fontWeight yazma; bu rolleri kullan. Sayılar sabit genişlikli (tnum). */
fun appTypography(cjk: Boolean): Typography {
    val extra = if (cjk) 2 else 0
    fun s(size: Int, line: Int, w: FontWeight, numbers: Boolean = false, tracking: Float = 0f) = TextStyle(
        fontSize = size.sp,
        lineHeight = (line + extra).sp,
        fontWeight = w,
        letterSpacing = if (cjk) 0.sp else tracking.sp,
        fontFeatureSettings = if (numbers) tnum else null,
    )
    return Typography(
        // Zamanlayıcı rakamları
        displayLarge = s(72, 76, FontWeight.Medium, numbers = true, tracking = -0.7f),
        // Halka içindeki büyük değer
        displayMedium = s(48, 52, FontWeight.SemiBold, numbers = true, tracking = -0.4f),
        displaySmall = s(36, 42, FontWeight.SemiBold, numbers = true),
        // Büyük ekran başlığı
        headlineLarge = s(32, 38, FontWeight.SemiBold),
        headlineMedium = s(28, 34, FontWeight.SemiBold),
        // İstatistik değeri, fiyat
        headlineSmall = s(24, 30, FontWeight.SemiBold, numbers = true),
        // Diyalog / küçük ekran başlığı
        titleLarge = s(20, 26, FontWeight.SemiBold),
        // Kart, alt sayfa, bölüm başlıkları
        titleMedium = s(17, 24, FontWeight.SemiBold),
        titleSmall = s(15, 20, FontWeight.SemiBold),
        bodyLarge = s(16, 24, FontWeight.Normal),
        // Varsayılan metin
        bodyMedium = s(15, 22, FontWeight.Normal),
        // Açıklama (ikincil renkle kullan)
        bodySmall = s(13, 18, FontWeight.Normal),
        // Düğmeler
        labelLarge = s(15, 20, FontWeight.SemiBold),
        // Çip, segment
        labelMedium = s(13, 16, FontWeight.Medium),
        // Gezinme etiketleri, rozetler
        labelSmall = s(12, 16, FontWeight.Medium),
    )
}

private val appShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.s),
    small = RoundedCornerShape(Radius.m),
    medium = RoundedCornerShape(Radius.l),
    large = RoundedCornerShape(Radius.l),
    extraLarge = RoundedCornerShape(Radius.xl),
)

@Composable
fun AppTheme(themeId: String, content: @Composable () -> Unit) {
    val t = Catalog.theme(themeId)
    val c = remember(t) { buildColors(t) }
    val cjk = I18n.lang.cjk
    val typography = remember(cjk) { appTypography(cjk) }

    val scheme = if (c.dark) darkColorScheme(
        primary = c.accent, onPrimary = c.onAccent,
        primaryContainer = c.accentContainer, onPrimaryContainer = c.onAccentContainer,
        secondary = c.success, onSecondary = c.onAccent,
        secondaryContainer = c.accentContainer, onSecondaryContainer = c.onAccentContainer,
        tertiary = c.warning, onTertiary = c.onAccent,
        tertiaryContainer = c.warningContainer, onTertiaryContainer = c.text,
        error = c.danger, onError = c.onAccent, errorContainer = c.dangerContainer, onErrorContainer = c.text,
        background = c.canvas, onBackground = c.text,
        surface = c.surface, onSurface = c.text,
        surfaceVariant = c.surfaceRaised, onSurfaceVariant = c.textSecondary,
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = c.canvas, surfaceContainerLow = c.surface,
        surfaceContainer = c.surfaceRaised, surfaceContainerHigh = c.surfaceRaised,
        surfaceContainerHighest = c.surfaceHigh,
        inverseSurface = c.text, inverseOnSurface = c.canvas,
        outline = c.outline, outlineVariant = c.hairline, scrim = Color.Black,
    ) else lightColorScheme(
        primary = c.accent, onPrimary = c.onAccent,
        primaryContainer = c.accentContainer, onPrimaryContainer = c.onAccentContainer,
        secondary = c.success, onSecondary = Color.White,
        secondaryContainer = c.accentContainer, onSecondaryContainer = c.onAccentContainer,
        tertiary = c.warning, onTertiary = Color.White,
        tertiaryContainer = c.warningContainer, onTertiaryContainer = c.text,
        error = c.danger, onError = Color.White, errorContainer = c.dangerContainer, onErrorContainer = c.text,
        background = c.canvas, onBackground = c.text,
        surface = c.surface, onSurface = c.text,
        surfaceVariant = c.surfaceRaised, onSurfaceVariant = c.textSecondary,
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = Color.White, surfaceContainerLow = c.surface,
        surfaceContainer = c.surfaceRaised, surfaceContainerHigh = c.surfaceRaised,
        surfaceContainerHighest = c.surfaceHigh,
        inverseSurface = c.text, inverseOnSurface = c.canvas,
        outline = c.outline, outlineVariant = c.hairline, scrim = Color.Black,
    )
    CompositionLocalProvider(LocalAppColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = appShapes, content = content)
    }
}

/** Tema jetonlarına erişim: AppTheme.colors.accent gibi. */
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
}
