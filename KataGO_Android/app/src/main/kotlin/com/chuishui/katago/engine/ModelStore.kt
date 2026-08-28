package com.chuishui.katago.engine

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import java.io.File

object ModelStore {

    val modelDir: File
        get() = File(Environment.getExternalStorageDirectory(), "Download/KataGO-AOS/model")

    val modelPath: String
        get() = modelDir.absolutePath

    fun hasStorageAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    fun ensureDir(): Boolean = modelDir.mkdirs() || modelDir.isDirectory

    fun listModels(): List<File> {
        ensureDir()
        if (!modelDir.isDirectory) return emptyList()
        return runCatching {
            modelDir.listFiles { f ->
                f.isFile && f.name.endsWith(".bin.gz") ||
                        f.isFile && f.name.endsWith(".bin") ||
                        f.isFile && f.name.endsWith(".txt.gz")
            }?.sortedBy { it.name } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    /** Copies an imported model file into Download/KataGO-AOS/model. */
    fun importModel(context: Context, sourceUri: android.net.Uri): File? {
        if (!ensureDir()) return null
        val name = sourceUri.lastPathSegment?.substringAfterLast('/') ?: "model.bin.gz"
        val safeName = name.takeIf { it.matches(Regex("[\\p{Alnum}._-]{1,120}")) } ?: "model.bin.gz"
        val dest = File(modelDir, safeName)
        return runCatching {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                if (dest.exists()) dest.delete()
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (dest.exists()) dest else null
        }.getOrNull()
    }
}