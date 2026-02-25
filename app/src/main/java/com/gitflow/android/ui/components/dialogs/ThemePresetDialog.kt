package com.gitflow.android.ui.components.dialogs

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.gitflow.android.R
import com.gitflow.android.ui.theme.AppColorTheme

@Composable
fun ThemePresetDialog(
    currentTheme: String,
    onThemeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val themes = buildList {
        add(AppColorTheme.DYNAMIC to stringResource(R.string.theme_preset_dynamic_desc))
        add(AppColorTheme.PURPLE  to stringResource(R.string.theme_preset_purple_desc))
        add(AppColorTheme.BLUE    to stringResource(R.string.theme_preset_blue_desc))
        add(AppColorTheme.GREEN   to stringResource(R.string.theme_preset_green_desc))
        add(AppColorTheme.ORANGE  to stringResource(R.string.theme_preset_orange_desc))
        add(AppColorTheme.ROSE    to stringResource(R.string.theme_preset_rose_desc))
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.theme_preset_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.theme_preset_description),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                themes.forEach { (themeId, description) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeSelected(themeId) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (currentTheme == themeId) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = currentTheme == themeId,
                                onClick = { onThemeSelected(themeId) }
                            )

                            // Цветной свотч
                            if (themeId == AppColorTheme.DYNAMIC) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(AppColorTheme.previewColor(themeId))
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(
                                        when (themeId) {
                                            AppColorTheme.DYNAMIC -> R.string.theme_preset_dynamic
                                            AppColorTheme.PURPLE  -> R.string.theme_preset_purple
                                            AppColorTheme.BLUE    -> R.string.theme_preset_blue
                                            AppColorTheme.GREEN   -> R.string.theme_preset_green
                                            AppColorTheme.ORANGE  -> R.string.theme_preset_orange
                                            AppColorTheme.ROSE    -> R.string.theme_preset_rose
                                            else                  -> R.string.theme_preset_purple
                                        }
                                    ),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = if (themeId == AppColorTheme.DYNAMIC && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                        stringResource(R.string.theme_preset_dynamic_unavailable)
                                    } else {
                                        description
                                    },
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.theme_preset_cancel))
                    }
                }
            }
        }
    }
}
