package com.gitflow.android.data.storage

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import java.io.File

/**
 * JGit works on real files, so a folder picked through the system picker is mapped to its path
 * on the volume ("primary:Projects/app" → /storage/emulated/0/Projects/app, an SD card's
 * "1A2B-3C4D:repo" → /storage/1A2B-3C4D/repo). The path is usable only with [hasSharedStorageAccess].
 */
fun treeUriToFile(treeUri: Uri): File? = runCatching {
    val documentId = DocumentsContract.getTreeDocumentId(treeUri)
    val volume = documentId.substringBefore(':')
    val relativePath = documentId.substringAfter(':', "")
    val volumeRoot = if (volume == "primary") Environment.getExternalStorageDirectory() else File("/storage/$volume")
    File(volumeRoot, relativePath)
}.getOrNull()

/** On Android 11+ arbitrary files in shared storage need the "all files access" grant. */
fun hasSharedStorageAccess(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }
