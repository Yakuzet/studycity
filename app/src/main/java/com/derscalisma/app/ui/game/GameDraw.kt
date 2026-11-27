package com.derscalisma.app.ui.game

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.derscalisma.app.R
import com.derscalisma.app.data.game.BType
import com.derscalisma.app.data.game.TType
import com.derscalisma.app.data.game.VILLAGE_SIZE
import kotlin.math.roundToInt
import kotlin.random.Random

/** Kenney "Medieval RTS" (CC0) görselleri. */
class Sprites(private val res: Resources) {
    private fun img(id: Int) = ImageBitmap.imageResource(res, id)

    private val buildings = mapOf(
        BType.TOWN_HALL to img(R.drawable.g_townhall),
        BType.BUILDER_HUT to img(R.drawable.g_builder),
        BType.BARRACKS to img(R.drawable.g_barracks),
        BType.ARMY_CAMP to img(R.drawable.g_camp),
        BType.ACADEMY to img(R.drawable.g_academy),
        BType.ARCHER_TOWER to img(R.drawable.g_tower_archer),
        BType.CANNON to img(R.drawable.g_tower_cannon),
        BType.MAGIC_TOWER to img(R.drawable.g_tower_magic),
        BType.LIBRARY to img(R.drawable.g_library),
        BType.GOLD_MINE to img(R.drawable.g_mine),
        BType.TREE to img(R.drawable.g_tree1),
        BType.PINE to img(R.drawable.g_tree2),
        BType.BUSH to img(R.drawable.g_bush),
        BType.FIELD to img(R.drawable.g_field),
        BType.HOUSE to img(R.drawable.g_house),
        BType.STALL to img(R.drawable.g_stall),
        BType.STATUE to img(R.drawable.g_statue),
        BType.WINDMILL to img(R.drawable.g_mill_base),
        BType.SPELL_FORGE to img(R.drawable.g_forge),
        BType.HERO_ALTAR to img(R.drawable.g_altar),
        BType.WALL to img(R.drawable.g_rock),
        BType.GOLD_MONUMENT to img(R.drawable.g_statue),
        BType.SAGE_STATUE to img(R.drawable.g_statue),
        BType.DRAGON_STATUE to img(R.drawable.g_rock),
    )
    private val troops = mapOf(
        TType.SWORD to img(R.drawable.g_u_sword),
        TType.APPRENTICE to img(R.drawable.g_u_apprentice),
        TType.KNIGHT to img(R.drawable.g_u_knight),
        TType.MAGE to img(R.drawable.g_u_mage),
        TType.HEALER to img(R.drawable.g_u_healer),
        TType.BREAKER to img(R.drawable.g_u_breaker),
        TType.HERO to img(R.drawable.g_u_hero),
    )
    val millBlades = img(R.drawable.g_mill_blades)
    val fire = img(R.drawable.g_fire)
    val rubble = img(R.drawable.g_rubble)
    val rock = img(R.drawable.g_rock)
    val worker = img(R.drawable.g_u_worker)
    val grass = img(R.drawable.g_grass)
    val grass2 = img(R.drawable.g_grass2)
    val forest = img(R.drawable.g_forest)
    val forest2 = img(R.drawable.g_forest2)
    val dirt = img(R.drawable.g_dirt)
    val tree = img(R.drawable.g_tree3)

    fun building(t: BType): ImageBitmap = buildings.getValue(t)
    fun troop(t: TType): ImageBitmap = troops.getValue(t)

    /** Haritanın zeminini (çimen + kenarda orman) tek bir resme önceden çizer. */
    fun ground(seed: Int, tilePx: Int = 48): ImageBitmap {
        val n = VILLAGE_SIZE
        val bmp = ImageBitmap(n * tilePx, n * tilePx)
        val canvas = Canvas(bmp)
        val paint = Paint().apply { filterQuality = FilterQuality.Low }
        val rnd = Random(seed)
        for (i in 0 until n) for (j in 0 until n) {
            val edge = i == 0 || j == 0 || i == n - 1 || j == n - 1
            val img = when {
                edge -> if (rnd.nextBoolean()) forest else forest2
                rnd.nextInt(5) == 0 -> grass2
                else -> grass
            }
            canvas.drawImageRect(
                img, IntOffset.Zero, IntSize(img.width, img.height),
                IntOffset(i * tilePx, j * tilePx), IntSize(tilePx, tilePx), paint,
            )
        }
        return bmp
    }
}

@Composable
fun rememberSprites(): Sprites {
    val res = LocalContext.current.resources
    return remember(res) { Sprites(res) }
}

/** Saniye cinsinden sürekli artan saat; sadece çizim içinde okunursa yalnızca yeniden çizim tetikler. */
@Composable
fun rememberAnimClock(): State<Float> = produceState(0f) {
    val start = withFrameNanos { it }
    while (true) {
        withFrameNanos { value = (it - start) / 1_000_000_000f }
    }
}

/** Görseli kutuya oranını koruyarak sığdırır (alta hizalı). */
fun DrawScope.drawFit(
    img: ImageBitmap,
    left: Float,
    top: Float,
    boxW: Float,
    boxH: Float,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
    flipX: Boolean = false,
    pixelated: Boolean = false,
    alignBottom: Boolean = true,
) {
    val s = minOf(boxW / img.width, boxH / img.height)
    val w = img.width * s
    val h = img.height * s
    val x = left + (boxW - w) / 2f
    val y = if (alignBottom) top + (boxH - h) else top + (boxH - h) / 2f
    val draw: DrawScope.() -> Unit = {
        drawImage(
            img,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(img.width, img.height),
            dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
            dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
            alpha = alpha,
            colorFilter = colorFilter,
            filterQuality = if (pixelated) FilterQuality.None else FilterQuality.Low,
        )
    }
    if (flipX) withTransform({ scale(-1f, 1f, pivot = Offset(x + w / 2f, y + h / 2f)) }, draw) else draw()
}

fun DrawScope.healthBar(cx: Float, top: Float, width: Float, ratio: Float, good: Color = Color(0xFF6EE36E)) {
    val h = (width * 0.14f).coerceIn(3f, 9f)
    drawRect(Color(0xAA000000), Offset(cx - width / 2f - 1f, top - 1f), androidx.compose.ui.geometry.Size(width + 2f, h + 2f))
    val c = when {
        ratio > 0.5f -> good
        ratio > 0.25f -> Color(0xFFFFC14D)
        else -> Color(0xFFFF5A4D)
    }
    drawRect(c, Offset(cx - width / 2f, top), androidx.compose.ui.geometry.Size(width * ratio.coerceIn(0f, 1f), h))
}

val HitFlash = ColorFilter.tint(Color(0x55FFFFFF), BlendMode.SrcAtop)
val HurtFlash = ColorFilter.tint(Color(0xAAFF3030), BlendMode.SrcAtop)
val GhostOk = ColorFilter.tint(Color(0x6600FF66), BlendMode.SrcAtop)
val GhostBad = ColorFilter.tint(Color(0x88FF3030), BlendMode.SrcAtop)

/** Sur rengi seviyeye göre: ahşap, taş, koyu taş, altın süslü, kristal. */
fun wallColors(level: Int): Pair<Color, Color> = when (level.coerceIn(1, 5)) {
    1 -> Color(0xFF9A6B3F) to Color(0xFF6E4A28)
    2 -> Color(0xFFB8BEC6) to Color(0xFF7C838D)
    3 -> Color(0xFF7D848F) to Color(0xFF4E545D)
    4 -> Color(0xFF6F7682) to Color(0xFFE0B84A)
    else -> Color(0xFF7FC8E8) to Color(0xFF3A86B4)
}

/**
 * Tek bir sur karosunu çizer. Komşu surlara doğru uzanan bağlantılar sayesinde surlar kesintisiz görünür.
 * [left]/[top] karonun sol üst köşesi, [t] karo boyutu (piksel).
 */
fun DrawScope.drawWall(level: Int, left: Float, top: Float, t: Float, right: Boolean, down: Boolean, damaged: Float = 0f, alpha: Float = 1f) {
    val (body, edge) = wallColors(level)
    val inset = t * 0.18f
    val w = t - inset * 2
    val corner = androidx.compose.ui.geometry.CornerRadius(t * 0.12f)
    // gölge
    drawRoundRect(Color(0x40000000).copy(alpha = 0.25f * alpha), Offset(left + inset, top + inset + t * 0.08f), androidx.compose.ui.geometry.Size(w, w), corner)
    if (right) drawRect(body.copy(alpha = alpha), Offset(left + t / 2f, top + inset + w * 0.15f), androidx.compose.ui.geometry.Size(t, w * 0.7f))
    if (down) drawRect(body.copy(alpha = alpha), Offset(left + inset + w * 0.15f, top + t / 2f), androidx.compose.ui.geometry.Size(w * 0.7f, t))
    drawRoundRect(body.copy(alpha = alpha), Offset(left + inset, top + inset), androidx.compose.ui.geometry.Size(w, w), corner)
    drawRoundRect(edge.copy(alpha = alpha), Offset(left + inset, top + inset), androidx.compose.ui.geometry.Size(w, w), corner,
        style = androidx.compose.ui.graphics.drawscope.Stroke(t * 0.06f))
    // tuğla çizgileri
    val line = edge.copy(alpha = 0.6f * alpha)
    drawLine(line, Offset(left + inset, top + t / 2f), Offset(left + t - inset, top + t / 2f), strokeWidth = t * 0.04f)
    drawLine(line, Offset(left + t / 2f, top + inset), Offset(left + t / 2f, top + t / 2f), strokeWidth = t * 0.04f)
    if (damaged > 0f) {
        drawLine(Color(0xAA2B1B0E), Offset(left + inset * 1.5f, top + inset * 1.4f), Offset(left + t * 0.62f, top + t * 0.55f), strokeWidth = t * 0.05f * damaged)
        drawLine(Color(0xAA2B1B0E), Offset(left + t * 0.62f, top + t * 0.55f), Offset(left + t * 0.7f, top + t - inset), strokeWidth = t * 0.05f * damaged)
    }
}
