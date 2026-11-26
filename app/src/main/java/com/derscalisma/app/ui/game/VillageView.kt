package com.derscalisma.app.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.derscalisma.app.data.game.*
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

data class Ghost(val type: BType, val x: Int, val y: Int, val ok: Boolean)

/** Köy haritası: yakınlaştır / kaydır, dokunulan karoyu bildirir. */
@Composable
fun VillageView(
    game: GameState,
    now: Long,
    selectedId: String?,
    ghost: Ghost?,
    onTap: (tileX: Int, tileY: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sprites = rememberSprites()
    val ground = remember(sprites) { sprites.ground(seed = 4242) }
    val clock = rememberAnimClock()
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var view by remember { mutableStateOf(IntSize.Zero) }
    val tapHandler by rememberUpdatedState(onTap)

    fun base(): Triple<Float, Float, Float> {
        val bt = minOf(view.width, view.height).toFloat() / VILLAGE_SIZE
        return Triple((view.width - bt * VILLAGE_SIZE) / 2f, (view.height - bt * VILLAGE_SIZE) / 2f, bt)
    }

    fun clampPan(p: Offset, z: Float): Offset {
        val (bl, bt0, bt) = base()
        fun axis(v: Float, start: Float, viewLen: Int): Float {
            val lo = viewLen - z * (start + VILLAGE_SIZE * bt)
            val hi = -z * start
            return if (lo > hi) (lo + hi) / 2f else v.coerceIn(lo, hi)
        }
        return Offset(axis(p.x, bl, view.width), axis(p.y, bt0, view.height))
    }

    Canvas(
        modifier
            .onSizeChanged {
                val first = view == IntSize.Zero
                view = it
                if (first && it.width > 0 && it.height > 0) {
                    // Biraz yakınlaştırılmış ve köyün ortasına odaklanmış başla.
                    val ratio = maxOf(it.width, it.height).toFloat() / minOf(it.width, it.height)
                    val z = ratio.coerceIn(1.4f, 2.2f)
                    val (bl, btop, bt) = base()
                    val c = VILLAGE_SIZE / 2f
                    zoom = z
                    pan = clampPan(Offset(it.width / 2f - z * (bl + c * bt), it.height / 2f - z * (btop + c * bt)), z)
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                    val nz = (zoom * zoomChange).coerceIn(1f, 3.5f)
                    val np = centroid - (centroid - pan) * (nz / zoom) + panChange
                    zoom = nz
                    pan = clampPan(np, nz)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { p ->
                    val (bl, btop, bt) = base()
                    val mx = ((p.x - pan.x) / zoom - bl) / bt
                    val my = ((p.y - pan.y) / zoom - btop) / bt
                    if (mx >= 0 && my >= 0) tapHandler(mx.toInt(), my.toInt())
                }
            }
    ) {
        val (bl, btop, bt) = base()
        val tsec = clock.value
        withTransform({
            translate(pan.x, pan.y)
            scale(zoom, zoom, pivot = Offset.Zero)
        }) {
            drawVillage(game, now, sprites, ground, bl, btop, bt, tsec, selectedId, ghost)
        }
    }
}

private fun DrawScope.drawVillage(
    g: GameState,
    now: Long,
    sp: Sprites,
    ground: androidx.compose.ui.graphics.ImageBitmap,
    left: Float,
    top: Float,
    t: Float,
    time: Float,
    selectedId: String?,
    ghost: Ghost?,
) {
    fun sx(x: Float) = left + x * t
    fun sy(y: Float) = top + y * t

    drawImage(
        ground, IntOffset.Zero, IntSize(ground.width, ground.height),
        IntOffset(left.roundToInt(), top.roundToInt()),
        IntSize((t * VILLAGE_SIZE).roundToInt(), (t * VILLAGE_SIZE).roundToInt()),
        filterQuality = FilterQuality.Low,
    )

    if (ghost != null) {
        val grid = Color.White.copy(alpha = 0.12f)
        for (i in 1 until VILLAGE_SIZE) {
            drawLine(grid, Offset(sx(i.toFloat()), sy(1f)), Offset(sx(i.toFloat()), sy(VILLAGE_SIZE - 1f)), strokeWidth = 1f)
            drawLine(grid, Offset(sx(1f), sy(i.toFloat())), Offset(sx(VILLAGE_SIZE - 1f), sy(i.toFloat())), strokeWidth = 1f)
        }
    }

    g.buildings.sortedBy { it.y + it.type.size }.forEach { b ->
        val s = b.type.size * t
        val x = sx(b.x.toFloat())
        val y = sy(b.y.toFloat())
        val pad = s * 0.04f
        val img = sp.building(b.type)
        val moving = ghost != null && selectedId == b.id

        if (b.level == 0) {
            // Yeni inşaat: toprak zemin + yarı saydam bina
            drawImage(sp.dirt, IntOffset.Zero, IntSize(sp.dirt.width, sp.dirt.height), IntOffset(x.roundToInt(), y.roundToInt()), IntSize(s.roundToInt(), s.roundToInt()), alpha = 0.9f)
            drawFit(img, x + pad, y + pad, s - pad * 2, s - pad * 2, alpha = 0.35f)
        } else {
            drawFit(img, x + pad, y + pad, s - pad * 2, s - pad * 2, alpha = if (moving) 0.35f else 1f)
            if (b.type == BType.WINDMILL) {
                val c = Offset(x + s / 2f, y + s * 0.38f)
                rotate(time * 50f, pivot = c) {
                    drawFit(sp.millBlades, c.x - s * 0.5f, c.y - s * 0.5f, s, s, alignBottom = false)
                }
            }
            if (b.type == BType.ARMY_CAMP) {
                val flick = 0.85f + 0.15f * sin(time * 15f + b.x)
                val fw = t * 0.55f * flick
                drawFit(sp.fire, x + s - fw * 1.1f, y + s - fw * 1.5f, fw, fw * 1.4f)
            }
        }

        if (b.upgrade != null) {
            // İskele
            val sc = Color(0xCC8B5A2B)
            val step = s / 4f
            for (k in 0..4) {
                drawLine(sc, Offset(x + k * step, y + s * 0.15f), Offset(x + k * step, y + s), strokeWidth = t * 0.06f)
            }
            drawLine(sc, Offset(x, y + s * 0.45f), Offset(x + s, y + s * 0.45f), strokeWidth = t * 0.06f)
            drawLine(sc, Offset(x, y + s * 0.8f), Offset(x + s, y + s * 0.8f), strokeWidth = t * 0.06f)
            // Çekiç sallayan işçi
            val hop = abs(sin(time * 6f + b.x)) * t * 0.18f
            val ww = t * 0.9f
            drawFit(sp.worker, x - ww * 0.25f, y + s - ww - hop, ww, ww, pixelated = true)
            val u = b.upgrade!!
            val ratio = (u.progressMs.toFloat() / u.requiredMs).coerceIn(0f, 1f)
            healthBar(x + s / 2f, y + s + t * 0.08f, s * 0.8f, ratio, good = Color(0xFFFFD166))
        }

        if (b.type == BType.GOLD_MINE && Game.mineStored(b, now) >= 1) {
            val bounce = abs(sin(time * 3f)) * t * 0.25f
            val c = Offset(x + s / 2f, y - t * 0.1f - bounce)
            drawCircle(Color(0x55000000), radius = t * 0.32f, center = c + Offset(0f, t * 0.05f))
            drawCircle(Color(0xFFFFC83D), radius = t * 0.3f, center = c)
            drawCircle(Color(0xFFFFE89A), radius = t * 0.18f, center = c)
        }

        if (b.type.cat != BCat.DECOR && b.level >= 1) {
            val r = t * 0.24f
            val c = Offset(x + r, y + r)
            drawCircle(Color(0xDD1B2230), radius = r, center = c)
            drawCircle(Color(0xFFFFD166), radius = r, center = c, style = Stroke(t * 0.04f))
            // Seviye noktaları (yazı çizmeden seviyeyi göstermek için)
            val dots = b.level.coerceAtMost(6)
            for (k in 0 until dots) {
                val a = (k / dots.toFloat()) * 6.283f - 1.57f
                drawCircle(Color(0xFFFFD166), radius = t * 0.045f, center = c + Offset(kotlin.math.cos(a) * r * 0.55f, kotlin.math.sin(a) * r * 0.55f))
            }
        }

        if (selectedId == b.id && ghost == null) {
            val pulse = 0.6f + 0.4f * sin(time * 5f)
            drawRoundRect(
                Color.White.copy(alpha = pulse), Offset(x, y), Size(s, s),
                cornerRadius = CornerRadius(t * 0.2f), style = Stroke(t * 0.08f),
            )
        }
    }

    if (ghost != null) {
        val s = ghost.type.size * t
        val x = sx(ghost.x.toFloat())
        val y = sy(ghost.y.toFloat())
        drawRoundRect(
            (if (ghost.ok) Color(0xFF3DDC84) else Color(0xFFFF4D4D)).copy(alpha = 0.35f),
            Offset(x, y), Size(s, s), cornerRadius = CornerRadius(t * 0.15f),
        )
        drawFit(sp.building(ghost.type), x + s * 0.04f, y + s * 0.04f, s * 0.92f, s * 0.92f, alpha = 0.85f, colorFilter = if (ghost.ok) GhostOk else GhostBad)
    }
}
