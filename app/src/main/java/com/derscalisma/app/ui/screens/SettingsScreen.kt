package com.derscalisma.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.derscalisma.app.data.*
import com.derscalisma.app.notify.AlarmScheduler
import com.derscalisma.app.notify.Notifier
import com.derscalisma.app.ui.*

@Composable
fun SettingsScreen(data: AppData) {
    val ctx = LocalContext.current
    val s = data.settings
    var confirmReset by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val notifGranted = Build.VERSION.SDK_INT < 33 ||
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val exactOk = AlarmScheduler.canExact(ctx)

    AdaptiveColumns(
        Pane(1f) {
            Panel(Modifier.fillMaxWidth(), title = "🔔 Bildirimler") {
                LabeledControl("Hatırlatmalar ve seri uyarıları") {
                    Switch(checked = s.remindersEnabled, onCheckedChange = { v -> Repo.updateSettings { it.copy(remindersEnabled = v) } })
                }
                LabeledControl("Seri uyarı saati", hint = "Hedef tamamlanmadıysa bu saatte ve 22:30'da uyarır") {
                    OutlinedButton(onClick = {
                        pickTime(ctx, s.warnHour * 60 + s.warnMinute) { m ->
                            Repo.updateSettings { it.copy(warnHour = m / 60, warnMinute = m % 60) }
                        }
                    }) { Text(Time.hm(s.warnHour * 60 + s.warnMinute)) }
                }
                LabeledControl("Aralıkta başlamadıysan hatırlat", hint = "Aralık başladıktan sonra (0 = kapalı)") {
                    Stepper(
                        s.windowNudgeMinutes,
                        { v -> Repo.updateSettings { it.copy(windowNudgeMinutes = v) } },
                        min = 0, max = 60, step = 5, suffix = " dk",
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                if (!notifGranted) {
                    Text("⚠️ Bildirim izni kapalı", color = MaterialTheme.colorScheme.error)
                    Button(onClick = { permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Bildirim izni ver") }
                } else {
                    Text("✅ Bildirim izni açık", style = MaterialTheme.typography.bodySmall)
                }
                if (!exactOk && Build.VERSION.SDK_INT >= 31) {
                    Text(
                        "⚠️ Tam zamanlı alarm izni kapalı: mola, bip ve hatırlatmalar birkaç dakika gecikebilir.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = {
                        ctx.startActivity(
                            Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                        )
                    }) { Text("Tam zamanlı alarma izin ver") }
                } else {
                    Text("✅ Tam zamanlı alarmlar açık", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = {
                    Notifier.alert(ctx, "🔔 Test bildirimi", "Bildirimler çalışıyor! Ders çalışma zamanı geldiğinde seni uyaracağım.")
                }) { Text("Test bildirimi gönder") }
            }
        },
        Pane(1f) {
            Panel(Modifier.fillMaxWidth(), title = "🔊 Ses ve Görünüm") {
                LabeledControl("Müzik", hint = "Köy ve savaş müzikleri bu uygulama için bestelendi") {
                    Switch(checked = s.musicEnabled, onCheckedChange = { v -> Repo.updateSettings { it.copy(musicEnabled = v) } })
                }
                if (s.musicEnabled) {
                    Slider(
                        value = s.musicVolume / 100f,
                        onValueChange = { v -> Repo.updateSettings { it.copy(musicVolume = (v * 100).toInt()) } },
                    )
                }
                LabeledControl("Ses efektleri") {
                    Switch(checked = s.sfxEnabled, onCheckedChange = { v -> Repo.updateSettings { it.copy(sfxEnabled = v) } })
                }
                if (s.sfxEnabled) {
                    Slider(
                        value = s.sfxVolume / 100f,
                        onValueChange = { v -> Repo.updateSettings { it.copy(sfxVolume = (v * 100).toInt()) } },
                        onValueChangeFinished = { com.derscalisma.app.audio.Sound.play(com.derscalisma.app.audio.Sfx.COIN) },
                    )
                }
                LabeledControl("3D köy ve savaş", hint = "Kapalıysa 2D çizilir (eski telefonlar için)") {
                    Switch(checked = s.view3d, onCheckedChange = { v -> Repo.updateSettings { it.copy(view3d = v) } })
                }
            }
            Panel(Modifier.fillMaxWidth(), title = "⚙️ Genel") {
                LabeledControl("Çalışırken ekranı açık tut") {
                    Switch(checked = s.keepScreenOn, onCheckedChange = { v -> Repo.updateSettings { it.copy(keepScreenOn = v) } })
                }
            }
            BackupPanel()
            Panel(Modifier.fillMaxWidth(), title = "📊 İstatistikler") {
                val totalMs = data.days.values.sumOf { it.studyMs }
                val sessions = data.days.values.sumOf { it.sessions }
                val metDays = data.days.values.count { it.dailyMet }
                StatLine("Toplam çalışma", Time.duration(totalMs))
                StatLine("Toplam oturum", "$sessions")
                StatLine("Hedefe ulaşılan gün", "$metDays")
                StatLine("En iyi günlük seri", "${data.bestDailyStreak} gün")
                StatLine("Toplam kazanılan", "${data.totalEarned} 🪙")
                FocusSummary(
                    "Tüm zamanlar dikkat",
                    data.days.values.sumOf { it.focusYes },
                    data.days.values.sumOf { it.focusNo },
                )
            }
            Panel(Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { confirmReset = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Tüm verileri sıfırla") }
            }
        },
    )

    if (confirmReset) {
        ConfirmDialog(
            title = "Her şey silinsin mi?",
            text = "Seriler, paralar, hedefler ve satın alımlar kalıcı olarak silinecek. Bu geri alınamaz.",
            confirmText = "Sıfırla",
            onConfirm = { Repo.resetAll() },
            onDismiss = { confirmReset = false },
        )
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
