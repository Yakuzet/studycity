package com.derscalisma.app.ui.game

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derscalisma.app.audio.Sfx
import com.derscalisma.app.audio.Sound
import com.derscalisma.app.data.AppData
import com.derscalisma.app.data.MINUTE_MS
import com.derscalisma.app.data.Repo
import com.derscalisma.app.data.game.*
import com.derscalisma.app.ui.ConfirmDialog
import com.derscalisma.app.ui.panelColor
import kotlinx.coroutines.delay

/** Köyün üstündeki şerit: haftanın canavarı, sandıklar, iksirler. */
@Composable
fun SurpriseBar(data: AppData, onStudy: () -> Unit) {
    val g = data.game
    var chestOpen by remember { mutableStateOf(false) }
    var potionAsk by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        g.boss?.let { BossCard(it, Modifier.weight(1f), onStudy) }
        if (g.chests.isNotEmpty()) {
            val pulse = rememberInfiniteTransition(label = "chest").animateFloat(
                0.92f, 1.08f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "s",
            )
            Button(onClick = { chestOpen = true; Sound.play(Sfx.CHEST_CREAK) }, modifier = Modifier.scale(pulse.value)) {
                Text("🎁 ${g.chests.size}")
            }
        }
        if (g.potions > 0) {
            FilledTonalButton(onClick = { potionAsk = true }) { Text("🧪 ${g.potions}") }
        }
    }
    if (chestOpen) ChestDialog(data) { chestOpen = false }
    if (potionAsk) {
        ConfirmDialog(
            title = "🧪 İnşaat İksiri",
            text = "Tüm yürüyen inşaatlara ve araştırmaya 30 dakikalık ders ilerlemesi eklenir. İçilsin mi?",
            confirmText = "İç",
            onConfirm = { SurpriseActions.usePotion()?.let(Repo::toast) },
            onDismiss = { potionAsk = false },
        )
    }
}

@Composable
private fun BossCard(b: BossState, modifier: Modifier, onStudy: () -> Unit) {
    val k = Surprises.bossKind(b)
    val cs = MaterialTheme.colorScheme
    val newHit = (b.dealtMs - b.shownMs) / MINUTE_MS
    var showHit by remember(b.shownMs) { mutableStateOf(newHit > 0) }
    LaunchedEffect(b.dealtMs) {
        if (b.dealtMs > b.shownMs) {
            Sound.play(Sfx.PUNCH)
            delay(2500)
            SurpriseActions.markBossShown()
        }
    }
    val shake = rememberInfiniteTransition(label = "boss").animateFloat(
        -3f, 3f, infiniteRepeatable(tween(260), RepeatMode.Reverse), label = "r",
    )
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = panelColor(), contentColor = cs.onSurface) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (b.defeated) "💀" else k.emoji,
                fontSize = 28.sp,
                modifier = Modifier.rotate(if (showHit && !b.defeated) shake.value * 3 else shake.value * 0.4f),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(k.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1)
                    if (showHit && newHit > 0) {
                        Text("-$newHit dk! 💥", color = Color(0xFFFF6B4A), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                    }
                }
                val ratio = 1f - b.dealtMs.toFloat() / b.maxMs
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(cs.onSurface.copy(alpha = 0.15f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(ratio.coerceIn(0f, 1f)).background(Color(0xFFE5484D)))
                }
                Text(
                    if (b.defeated) "Yenildi! Gelecek hafta yenisi gelir."
                    else "\"${k.taunt}\" • ${((b.maxMs - b.dealtMs) / MINUTE_MS)} dk ders çalışırsan yenilir",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (!b.defeated) {
                TextButton(onClick = onStudy, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("⚔️ Vur") }
            }
        }
    }
}

@Composable
fun ChestDialog(data: AppData, onDismiss: () -> Unit) {
    var result by remember { mutableStateOf<Pair<ChestKind, ChestReward>?>(null) }
    var opening by remember { mutableStateOf(false) }
    val next = data.game.chests.firstOrNull()
    val shake = rememberInfiniteTransition(label = "c").animateFloat(-6f, 6f, infiniteRepeatable(tween(90), RepeatMode.Reverse), label = "r")
    val burst = remember { Animatable(0f) }
    LaunchedEffect(opening) {
        if (opening) {
            delay(650)
            result = SurpriseActions.openChest()
            burst.snapTo(0f)
            burst.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 220f))
            opening = false
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(result?.first?.title ?: next?.title ?: "Sandık") },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val r = result
                if (r == null) {
                    Text(
                        next?.emoji ?: "🎁",
                        fontSize = 72.sp,
                        modifier = Modifier.rotate(if (opening) shake.value else 0f).scale(if (opening) 1.15f else 1f),
                    )
                    Text(if (opening) "Açılıyor..." else "Dokun ve aç!", textAlign = TextAlign.Center)
                } else {
                    Text("✨", fontSize = (40 + 40 * burst.value).sp)
                    val lines = buildList {
                        add("🪙 +${r.second.coins} altın")
                        if (r.second.potions > 0) add("🧪 +${r.second.potions} İnşaat İksiri")
                        r.second.spells.forEach { (s, n) -> add("${s.emoji} +$n ${s.title}") }
                        r.second.decor?.let { add("🏆 NADİR: ${it.title}! (İnşa Et menüsünden kurabilirsin)") }
                    }
                    lines.forEachIndexed { i, l ->
                        val a = ((burst.value * (lines.size + 1)) - i).coerceIn(0f, 1f)
                        Text(l, fontWeight = FontWeight.Bold, modifier = Modifier.scale(0.6f + 0.4f * a).padding(vertical = 2.dp), color = LocalContentColor.current.copy(alpha = a))
                    }
                }
            }
        },
        confirmButton = {
            when {
                result == null -> Button(enabled = !opening && next != null, onClick = { opening = true; Sound.play(Sfx.CHEST_CREAK) }) { Text("Aç") }
                data.game.chests.isNotEmpty() -> Button(onClick = { result = null }) { Text("Sıradaki (${data.game.chests.size})") }
                else -> Button(onClick = onDismiss) { Text("Harika!") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
    )
}
