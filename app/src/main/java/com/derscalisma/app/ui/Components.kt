package com.derscalisma.app.ui

import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derscalisma.app.data.Focus
import com.derscalisma.app.data.TimerStyleKind

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    title: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = panelColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(14.dp)) {
            if (title != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    action?.invoke()
                }
                Spacer(Modifier.height(8.dp))
            }
            content()
        }
    }
}

@Composable
fun panelColor(): Color {
    val s = MaterialTheme.colorScheme.surface
    return if (LocalHasBackground.current) s.copy(alpha = 0.86f) else s
}

/** Toplu / dağınık cevap yüzdesi ve iki renkli çubuk. */
@Composable
fun FocusSummary(label: String, yes: Int, no: Int) {
    val cs = MaterialTheme.colorScheme
    val p = Focus.percent(yes, no)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                if (p == null) "henüz cevap yok" else "%$p toplu",
                fontWeight = FontWeight.Bold,
                color = if (p == null) cs.onSurfaceVariant else if (p >= 70) cs.secondary else if (p >= 40) cs.tertiary else cs.error,
            )
        }
        if (p != null) {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
                if (yes > 0) Box(Modifier.weight(yes.toFloat()).fillMaxHeight().background(cs.secondary))
                if (no > 0) Box(Modifier.weight(no.toFloat()).fillMaxHeight().background(cs.error.copy(alpha = 0.75f)))
            }
            Text(
                "✅ $yes toplu • 💭 $no dağınık (%${100 - p})",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun Stepper(
    value: Int,
    onChange: (Int) -> Unit,
    min: Int,
    max: Int,
    step: Int = 1,
    suffix: String = "",
    bigStep: Int? = null,
    enabled: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (bigStep != null) RoundBtn("−$bigStep", enabled && value > min) { onChange((value - bigStep).coerceAtLeast(min)) }
        RoundBtn("−", enabled && value > min) { onChange((value - step).coerceAtLeast(min)) }
        Text(
            "$value$suffix",
            modifier = Modifier.widthIn(min = 64.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
        )
        RoundBtn("+", enabled && value < max) { onChange((value + step).coerceAtMost(max)) }
        if (bigStep != null) RoundBtn("+$bigStep", enabled && value < max) { onChange((value + bigStep).coerceAtMost(max)) }
    }
}

@Composable
private fun RoundBtn(text: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(34.dp).widthIn(min = 38.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        shape = CircleShape,
    ) { Text(text, fontSize = 15.sp) }
}

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    color2: Color = MaterialTheme.colorScheme.tertiary,
    track: Color = color.copy(alpha = 0.15f),
    strokeWidth: Dp = 14.dp,
    style: TimerStyleKind = TimerStyleKind.CLASSIC,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = strokeWidth.toPx() * (if (style == TimerStyleKind.BOLD) 1.9f else 1f)
            val d = size.minDimension - sw
            if (d <= 0f) return@Canvas
            val arcSize = Size(d, d)
            val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            val effect = if (style == TimerStyleKind.DOTS) PathEffect.dashPathEffect(floatArrayOf(0.5f, sw * 1.6f)) else null
            val stroke = Stroke(width = sw, cap = StrokeCap.Round, pathEffect = effect)
            drawArc(color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
            val sweep = 360f * progress.coerceIn(0f, 1f)
            if (sweep > 0f) {
                rotate(-90f) {
                    if (style == TimerStyleKind.GRADIENT) {
                        drawArc(
                            brush = Brush.sweepGradient(listOf(color2, color, color2)),
                            startAngle = 0f, sweepAngle = sweep, useCenter = false,
                            topLeft = topLeft, size = arcSize, style = stroke,
                        )
                    } else {
                        drawArc(
                            color = color, startAngle = 0f, sweepAngle = sweep, useCenter = false,
                            topLeft = topLeft, size = arcSize, style = stroke,
                        )
                    }
                }
            }
        }
        content()
    }
}

@Composable
fun StreakCard(emoji: String, title: String, value: Int, modifier: Modifier = Modifier, unit: String = "gün") {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = panelColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 24.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$value $unit",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (value > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

fun pickTime(ctx: Context, initialMinute: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(
        ctx,
        { _, h, m -> onPicked(h * 60 + m) },
        initialMinute / 60,
        initialMinute % 60,
        true,
    ).show()
}
