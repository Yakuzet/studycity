package com.derscalisma.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.FileOutputStream

/**
 * Kayıt dosyası güvenliği:
 * - Yazma: önce geçici dosyaya yazılır, diske zorla işlenir (fsync), sonra yerine taşınır.
 * - Saatte bir dönen 3 yedek (data.bak1..3).
 * - Okunamayan kayıt asla silinmez: kenara kopyalanır ve okunabilen her parçası kurtarılır.
 */
class SaveStore(private val dir: File, private val json: Json) {
    private val main = File(dir, "data.json")
    private var lastBackupAt = File(dir, "data.bak1.json").takeIf { it.exists() }?.lastModified() ?: 0L

    /** Bu açılışta kayıt dosyası hiç yoksa true (yeni kurulum). */
    var freshInstall = false
        private set

    /** Kayıt kurtarıldıysa kullanıcıya gösterilecek açıklama. */
    var recoveryNotice: String? = null
        private set

    fun load(): AppData {
        if (main.exists()) {
            val text = runCatching { main.readText() }.getOrNull()
            if (text != null && text.isNotBlank()) {
                strict(text)?.let { return it }
                runCatching { main.copyTo(File(dir, "data.corrupt.${System.currentTimeMillis()}.json"), overwrite = true) }
                lenient(text)?.let {
                    recoveryNotice = "Kayıt dosyasının bir kısmı okunamadı; okunabilen her şey kurtarıldı."
                    return it
                }
            }
        } else {
            freshInstall = true
        }
        for (f in backupFiles()) {
            val text = runCatching { f.readText() }.getOrNull() ?: continue
            val d = strict(text) ?: lenient(text) ?: continue
            recoveryNotice = "Kayıt ${f.name} yedeğinden geri yüklendi."
            freshInstall = false
            return d
        }
        return AppData()
    }

    fun save(d: AppData, now: Long) {
        val text = json.encodeToString(AppData.serializer(), d)
        if (main.exists() && now - lastBackupAt > 60 * 60_000L) rotateBackups(now)
        val tmp = File(dir, "data.json.tmp")
        FileOutputStream(tmp).use {
            it.write(text.toByteArray(Charsets.UTF_8))
            it.fd.sync()
        }
        if (!tmp.renameTo(main)) {
            main.writeText(text)
            tmp.delete()
        }
    }

    private fun rotateBackups(now: Long) {
        runCatching {
            val b1 = File(dir, "data.bak1.json")
            val b2 = File(dir, "data.bak2.json")
            val b3 = File(dir, "data.bak3.json")
            b3.delete()
            if (b2.exists()) b2.renameTo(b3)
            if (b1.exists()) b1.renameTo(b2)
            main.copyTo(b1, overwrite = true)
            lastBackupAt = now
        }
    }

    /** Yedekler ve kenara alınmış bozuk kayıtlar, en yeniden eskiye. */
    fun backupFiles(): List<File> =
        dir.listFiles { f -> f.name.startsWith("data.bak") || f.name.startsWith("data.corrupt") || f.name.startsWith("data.before") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    fun allFiles(): List<File> =
        dir.listFiles { f -> f.name.startsWith("data") }?.sortedByDescending { it.lastModified() }.orEmpty()

    fun readFile(name: String): AppData? {
        val f = File(dir, name)
        if (!f.exists() || f.parentFile != dir) return null
        val text = runCatching { f.readText() }.getOrNull() ?: return null
        return strict(text) ?: lenient(text)
    }

    /** Geri yüklemeden önce mevcut kaydı ayrıca sakla. */
    fun keepCurrentCopy() {
        if (main.exists()) runCatching { main.copyTo(File(dir, "data.before-restore.${System.currentTimeMillis()}.json"), overwrite = true) }
    }

    fun dismissFresh() {
        freshInstall = false
    }

    fun strict(text: String): AppData? = runCatching { json.decodeFromString(AppData.serializer(), text) }.getOrNull()

    /** Alan alan çözer; bozuk alanları (ve bozuk alt girdileri) atlayıp gerisini kurtarır. */
    fun lenient(text: String): AppData? {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        fun ok(o: Map<String, JsonElement>) = runCatching { json.decodeFromJsonElement(AppData.serializer(), JsonObject(o)) }.isSuccess
        val acc = linkedMapOf<String, JsonElement>()
        for ((k, v) in obj) {
            if (ok(acc + (k to v))) {
                acc[k] = v
            } else if (v is JsonObject) {
                val inner = linkedMapOf<String, JsonElement>()
                for ((ik, iv) in v) if (ok(acc + (k to JsonObject(inner + (ik to iv))))) inner[ik] = iv
                if (inner.isNotEmpty() && ok(acc + (k to JsonObject(inner)))) acc[k] = JsonObject(inner)
            }
        }
        if (acc.isEmpty()) return null
        return runCatching { json.decodeFromJsonElement(AppData.serializer(), JsonObject(acc)) }.getOrNull()
    }
}
