package com.derscalisma.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import com.derscalisma.app.data.Catalog

@Composable
fun AppTheme(themeId: String, content: @Composable () -> Unit) {
    val t = Catalog.theme(themeId)
    val primary = Color(t.primary)
    val secondary = Color(t.secondary)
    val tertiary = Color(t.tertiary)
    val bg = Color(t.background)
    val surface = Color(t.surface)
    val on = if (t.dark) Color(0xFFE9ECF5) else Color(0xFF1A1C22)
    val onAccent = if (t.dark) Color(0xFF10131C) else Color.White
    fun mix(f: Float) = lerp(surface, on, f)
    fun container(c: Color) = c.copy(alpha = 0.22f).compositeOver(surface)

    val scheme = if (t.dark) darkColorScheme(
        primary = primary, onPrimary = onAccent,
        primaryContainer = container(primary), onPrimaryContainer = on,
        secondary = secondary, onSecondary = onAccent,
        secondaryContainer = container(secondary), onSecondaryContainer = on,
        tertiary = tertiary, onTertiary = onAccent,
        tertiaryContainer = container(tertiary), onTertiaryContainer = on,
        background = bg, onBackground = on,
        surface = surface, onSurface = on,
        surfaceVariant = mix(0.08f), onSurfaceVariant = on.copy(alpha = 0.75f),
        surfaceContainerLowest = bg, surfaceContainerLow = mix(0.02f),
        surfaceContainer = mix(0.04f), surfaceContainerHigh = mix(0.07f),
        surfaceContainerHighest = mix(0.10f),
        outline = on.copy(alpha = 0.35f), outlineVariant = on.copy(alpha = 0.15f),
    ) else lightColorScheme(
        primary = primary, onPrimary = onAccent,
        primaryContainer = container(primary), onPrimaryContainer = on,
        secondary = secondary, onSecondary = onAccent,
        secondaryContainer = container(secondary), onSecondaryContainer = on,
        tertiary = tertiary, onTertiary = onAccent,
        tertiaryContainer = container(tertiary), onTertiaryContainer = on,
        background = bg, onBackground = on,
        surface = surface, onSurface = on,
        surfaceVariant = mix(0.05f), onSurfaceVariant = on.copy(alpha = 0.75f),
        surfaceContainerLowest = Color.White, surfaceContainerLow = mix(0.02f),
        surfaceContainer = mix(0.03f), surfaceContainerHigh = mix(0.05f),
        surfaceContainerHighest = mix(0.08f),
        outline = on.copy(alpha = 0.35f), outlineVariant = on.copy(alpha = 0.15f),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
