package com.derscalisma.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** İçerik alanının boyutu (menü ve çentik payı çıkarılmış). Ekranlar düzeni buna göre seçer. */
data class LayoutInfo(val width: Dp, val height: Dp) {
    val columns: Int
        get() = when {
            width < 500.dp -> 1
            width < 900.dp -> 2
            else -> 3
        }
    val compact: Boolean get() = columns == 1
}

val LocalLayout = staticCompositionLocalOf { LayoutInfo(800.dp, 400.dp) }

/** Arka plan fotoğrafı seçiliyse paneller yarı saydam çizilir. */
val LocalHasBackground = staticCompositionLocalOf { false }

class Pane(val weight: Float = 1f, val content: @Composable ColumnScope.() -> Unit)

/**
 * Geniş ekranda paneller yan yana sütunlarda (her sütun kendi içinde kayar),
 * orta genişlikte 2 sütun (ilk pane solda, kalanlar sağda alt alta),
 * dar ekranda (dikey telefon) hepsi alt alta tek kaydırılan sütunda.
 */
@Composable
fun AdaptiveColumns(vararg panes: Pane) {
    val cols = minOf(LocalLayout.current.columns, panes.size)
    val spacing = 12.dp
    if (cols <= 1) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            panes.forEach { it.content(this) }
            Spacer(Modifier.height(4.dp))
        }
    } else {
        val groups = (0 until cols).map { c -> if (c < cols - 1) listOf(panes[c]) else panes.drop(cols - 1) }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
            groups.forEach { g ->
                Column(
                    Modifier
                        .weight(g.maxOf { it.weight })
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    g.forEach { it.content(this) }
                }
            }
        }
    }
}

/** Etiket + kontrol: yer varsa yan yana, dar alanda alt alta. Böylece hiçbir şey iç içe girmez. */
@Composable
fun LabeledControl(
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    stackBelow: Dp = 330.dp,
    control: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < stackBelow) {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                if (hint != null) Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                control()
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    if (hint != null) Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                control()
            }
        }
    }
}

/** Genişliğe göre kendiliğinden sütun sayısı seçen basit ızgara (kaydırma içermez). */
@Composable
fun <T> ResponsiveGrid(
    items: List<T>,
    minItemWidth: Dp,
    spacing: Dp = 10.dp,
    content: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = ((maxWidth + spacing) / (minItemWidth + spacing)).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            items.chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    row.forEach { content(it, Modifier.weight(1f)) }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
