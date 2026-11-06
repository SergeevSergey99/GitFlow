package com.gitflow.android.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.gitflow.android.R
import com.gitflow.android.data.settings.AppSettingsManager
import org.koin.compose.koinInject

/**
 * Returns a function that starts a clone, first explaining and requesting notifications once.
 * The clone starts whatever the answer: without notifications its progress is still shown inside the app.
 */
@Composable
fun rememberCloneNotificationPrompt(): (startClone: () -> Unit) -> Unit {
    val context = LocalContext.current
    val settingsManager: AppSettingsManager = koinInject()
    var explainedClone by remember { mutableStateOf<(() -> Unit)?>(null) }
    var cloneAwaitingPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        val startClone = cloneAwaitingPermission
        cloneAwaitingPermission = null
        startClone?.invoke()
    }

    explainedClone?.let { startClone ->
        val startWithoutNotifications = {
            settingsManager.setCloneNotificationPromptShown()
            explainedClone = null
            startClone()
        }
        AlertDialog(
            onDismissRequest = startWithoutNotifications,
            title = { Text(stringResource(R.string.clone_notifications_title)) },
            text = { Text(stringResource(R.string.clone_notifications_message)) },
            confirmButton = {
                TextButton(onClick = {
                    settingsManager.setCloneNotificationPromptShown()
                    explainedClone = null
                    cloneAwaitingPermission = startClone
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text(stringResource(R.string.settings_permission_grant)) }
            },
            dismissButton = {
                TextButton(onClick = startWithoutNotifications) { Text(stringResource(R.string.clone_notifications_not_now)) }
            }
        )
    }

    return { startClone ->
        val needsPrompt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !settingsManager.isCloneNotificationPromptShown()
        if (needsPrompt) explainedClone = startClone else startClone()
    }
}
