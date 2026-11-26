package com.derscalisma.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.derscalisma.app.data.ExternalBackup
import com.derscalisma.app.data.Repo
import com.derscalisma.app.data.Time
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupActions(val export: () -> Unit, val import: () -> Unit)

@Composable
fun rememberBackupActions(): BackupActions {
    val ctx = LocalContext.current
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val text = Repo.exportJson()
            Thread {
                val ok = runCatching {
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
                }.getOrDefault(false)
                Repo.toast(if (ok) "📤 Yedek kaydedildi" else "⚠️ Yedek kaydedilemedi")
            }.start()
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            if (text == null) Repo.toast("⚠️ Dosya okunamadı") else Repo.importJson(text)?.let { Repo.toast("⚠️ $it") }
        }
    }
    return remember(exporter, importer) {
        BackupActions(
            export = { exporter.launch("studycity-yedek-${Time.dayKey(System.currentTimeMillis())}.json") },
            import = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
        )
    }
}

private fun stamp(ms: Long) = SimpleDateFormat("d MMM HH:mm", Locale("tr")).format(Date(ms))

@Composable
fun BackupPanel() {
    val ctx = LocalContext.current
    val actions = rememberBackupActions()
    var confirm by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val files = remember(refresh) { Repo.saveFiles() }
    val cs = MaterialTheme.colorScheme

    Panel(Modifier.fillMaxWidth(), title = "💾 Yedekleme") {
        if (ExternalBackup.supported) {
            val last = ExternalBackup.lastBackupAt(ctx)
            Text(
                "Otomatik yedek: İndirilenler/${ExternalBackup.FOLDER}/${ExternalBackup.FILE_NAME}" +
                    if (last > 0) " (son: ${stamp(last)})" else " (henüz yok)",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Bu dosya uygulamayı silsen bile telefonda kalır. Yeniden kurunca \"İçe aktar\" ile seçip geri yükleyebilirsin.",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
            )
        } else {
            Text("Bu Android sürümünde otomatik dış yedek yok; ara sıra \"Dışa aktar\" ile yedek al.", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = actions.export) { Text("📤 Dışa aktar") }
            FilledTonalButton(onClick = actions.import) { Text("📥 İçe aktar") }
        }
        if (ExternalBackup.supported) {
            TextButton(onClick = { Repo.backupExternally(force = true) }) { Text("Şimdi otomatik yedek al") }
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text("Uygulama içindeki kayıt dosyaları", fontWeight = FontWeight.SemiBold)
        files.forEach { f ->
            val restorable = f.name != "data.json" && f.name != "data.json.tmp"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(f.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${f.length() / 1024} KB • ${stamp(f.lastModified())}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
                if (restorable) TextButton(onClick = { confirm = f.name }) { Text("Geri yükle") }
            }
        }
    }

    confirm?.let { name ->
        ConfirmDialog(
            title = "Bu yedek yüklensin mi?",
            text = "$name içindeki kayıt yüklenecek. Şu anki kaydın ayrıca saklanır, istersen geri dönebilirsin.",
            confirmText = "Geri yükle",
            onConfirm = {
                Repo.restoreFromFile(name)?.let { Repo.toast("⚠️ $it") }
                refresh++
            },
            onDismiss = { confirm = null },
        )
    }
}

/** Yeni kurulumda (kayıt yokken) ana sayfada çıkan geri yükleme kartı. */
@Composable
fun FreshInstallCard() {
    var hidden by remember { mutableStateOf(false) }
    if (hidden || !Repo.freshInstall) return
    val actions = rememberBackupActions()
    Panel(Modifier.fillMaxWidth(), title = "📦 Önceki kaydın var mı?") {
        Text(
            "Uygulamayı yeniden kurduysan, telefonundaki İndirilenler/${ExternalBackup.FOLDER} klasöründeki " +
                "${ExternalBackup.FILE_NAME} dosyasını (ya da dışa aktardığın bir yedeği) seçerek her şeyi geri getirebilirsin.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = actions.import) { Text("📥 Yedekten geri yükle") }
            TextButton(onClick = { hidden = true; Repo.dismissFreshInstall() }) { Text("Yeni başla") }
        }
    }
}
