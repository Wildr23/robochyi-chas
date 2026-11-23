package ua.robochyichas.app.data.local

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDate

class LocalSnapshotStore(private val context: Context) {

    private val backupDir: File by lazy {
        File(context.filesDir, "backups").apply { mkdirs() }
    }

    private fun atomicWrite(target: File, content: String) {
        val tmp = File(backupDir, "${target.name}.tmp")
        FileOutputStream(tmp).use { fos ->
            fos.write(content.toByteArray(StandardCharsets.UTF_8))
            fos.flush()
            runCatching { fos.fd.sync() }
        }
        if (target.exists() && !tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        } else if (!target.exists()) {
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }
    }

    fun writeSnapshot(jsonContent: String) {
        runCatching {
            val latestFile = File(backupDir, "latest.json")
            atomicWrite(latestFile, jsonContent)

            val todayStr = LocalDate.now().toString()
            val dailyFile = File(backupDir, "daily_$todayStr.json")
            atomicWrite(dailyFile, jsonContent)

            pruneOldDailySnapshots(keepDays = 7)
        }
    }

    fun readLatestSnapshot(): String? = runCatching {
        val f = File(backupDir, "latest.json")
        if (f.exists()) f.readText(StandardCharsets.UTF_8) else null
    }.getOrNull()

    fun getLatestSnapshotFile(): File = File(backupDir, "latest.json")

    private fun pruneOldDailySnapshots(keepDays: Int) {
        val dailyFiles = backupDir.listFiles { _, name ->
            name.startsWith("daily_") && name.endsWith(".json")
        }?.sortedByDescending { it.name } ?: return

        if (dailyFiles.size > keepDays) {
            dailyFiles.drop(keepDays).forEach { it.delete() }
        }
    }
}
