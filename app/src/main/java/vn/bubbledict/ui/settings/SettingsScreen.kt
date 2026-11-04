package vn.bubbledict.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import vn.bubbledict.R
import vn.bubbledict.ui.result.Voice
import vn.bubbledict.ui.theme.extendedColors

/**
 * Tab Cài đặt (UX-03, FE-05).
 *
 * Gồm màn danh sách + 5 màn con (API key, gói offline, 3 màn giải thích quyền).
 * Trạng thái lấy qua [SettingsUiState], thao tác qua [SettingsActions] — chưa nối ViewModel
 * (BE-06/BE-09/BE-13); không tự đọc/ghi DataStore hay SecretStore ở đây.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions = SettingsActions(),
    modifier: Modifier = Modifier,
    initialRoute: SettingsRoute = SettingsRoute.LIST,
    onShowMessage: (String) -> Unit = {},
) {
    var route by rememberSaveable { mutableStateOf(initialRoute) }
    val context = LocalContext.current

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        actions.onNotificationResult(granted)
        route = SettingsRoute.LIST
    }

    when (route) {
        SettingsRoute.LIST -> SettingsList(
            state = state,
            actions = actions,
            modifier = modifier,
            onRoute = { route = it },
            onShowMessage = onShowMessage,
        )

        SettingsRoute.API_KEY -> ApiKeyScreen(
            state = state,
            actions = actions,
            onBack = { route = SettingsRoute.LIST },
            onShowMessage = onShowMessage,
        )

        SettingsRoute.PROVIDER -> ProviderScreen(
            state = state,
            actions = actions,
            onBack = { route = SettingsRoute.LIST },
            onShowMessage = onShowMessage,
        )

        SettingsRoute.MODEL_DOWNLOAD -> ModelDownloadScreen(
            state = state,
            actions = actions,
            onBack = { route = SettingsRoute.LIST },
        )

        SettingsRoute.PERMISSION_OVERLAY -> PermissionRationaleScreen(
            icon = painterResource(R.drawable.ic_layers),
            title = stringResource(R.string.perm_overlay_title),
            body = stringResource(R.string.perm_overlay_body),
            whereText = stringResource(R.string.perm_overlay_where),
            primaryLabel = if (state.overlayPermission == PermissionStatus.GRANTED) {
                stringResource(R.string.perm_overlay_revoke)
            } else {
                stringResource(R.string.perm_allow)
            },
            primaryDestructive = state.overlayPermission == PermissionStatus.GRANTED,
            onPrimary = {
                openOverlaySettings(context)
                route = SettingsRoute.LIST
            },
            onLater = { route = SettingsRoute.LIST },
        )

        SettingsRoute.PERMISSION_NOTIFICATION -> PermissionRationaleScreen(
            icon = Icons.Filled.Notifications,
            title = stringResource(R.string.perm_notification_title),
            body = stringResource(R.string.perm_notification_body),
            whereText = stringResource(R.string.perm_notification_where),
            primaryLabel = stringResource(R.string.perm_allow),
            onPrimary = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openNotificationSettings(context)
                    route = SettingsRoute.LIST
                }
            },
            onLater = { route = SettingsRoute.LIST },
        )

        SettingsRoute.PERMISSION_SCREENSHOT -> PermissionRationaleScreen(
            icon = painterResource(R.drawable.ic_screenshot_monitor),
            title = stringResource(R.string.perm_screenshot_title),
            body = stringResource(R.string.perm_screenshot_body),
            whereText = stringResource(R.string.perm_screenshot_where),
            primaryLabel = stringResource(R.string.perm_screenshot_continue),
            onPrimary = { route = SettingsRoute.LIST },
            onLater = { route = SettingsRoute.LIST },
        )
    }
}

private enum class SettingsDialog { CONTACT_EMAIL, DELETE_HISTORY, DELETE_VOCAB, DELETE_CACHE }

@Composable
private fun SettingsList(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier,
    onRoute: (SettingsRoute) -> Unit,
    onShowMessage: (String) -> Unit,
) {
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        item { SectionHeader(stringResource(R.string.settings_section_bubble)) }
        item {
            SettingItem(
                title = stringResource(R.string.settings_bubble),
                subtitle = if (state.bubbleEnabled) {
                    stringResource(R.string.settings_bubble_on_hint)
                } else {
                    null
                },
                trailing = {
                    Switch(
                        checked = state.bubbleEnabled,
                        onCheckedChange = { checked ->
                            if (checked && state.overlayPermission != PermissionStatus.GRANTED) {
                                // UX-03 §3.1: bật khi chưa có quyền -> màn giải thích, switch chưa bật.
                                onRoute(SettingsRoute.PERMISSION_OVERLAY)
                            } else {
                                actions.onBubbleToggle(checked)
                            }
                        },
                    )
                },
                onClick = null,
            )
        }
        item {
            SettingItem(
                title = stringResource(R.string.settings_overlay_permission),
                subtitle = permissionSubtitle(state.overlayPermission),
                subtitleColor = permissionColor(state.overlayPermission),
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PermissionBadge(state.overlayPermission)
                        Chevron()
                    }
                },
                onClick = { onRoute(SettingsRoute.PERMISSION_OVERLAY) },
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            item {
                SettingItem(
                    title = stringResource(R.string.settings_notification_permission),
                    subtitle = permissionSubtitle(state.notificationPermission),
                    subtitleColor = permissionColor(state.notificationPermission),
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PermissionBadge(state.notificationPermission)
                            Chevron()
                        }
                    },
                    onClick = { onRoute(SettingsRoute.PERMISSION_NOTIFICATION) },
                )
            }
            if (state.needsNotificationSetup) {
                item {
                    NotificationWarningCard(onOpen = { openNotificationSettings(context) })
                }
            }
        }

        item { SectionHeader(stringResource(R.string.settings_section_translate)) }
        item {
            // F11 + spec §7: privacy rõ ràng, mặc định BẬT; tắt = chế độ chỉ offline.
            SettingItem(
                title = stringResource(R.string.settings_online_sources),
                subtitle = stringResource(R.string.settings_online_sources_hint),
                trailing = {
                    Switch(
                        checked = state.onlineSourcesEnabled,
                        onCheckedChange = actions.onOnlineSourcesToggle,
                    )
                },
                onClick = null,
            )
        }
        item {
            // Designer §3.4: dòng trạng thái nguồn keyless (Đang tắt / Hết lượt tới 0h / Còn lượt).
            SettingItem(
                title = stringResource(R.string.settings_free_source),
                trailing = {
                    Text(
                        text = stringResource(
                            when (state.freeSourceStatus) {
                                FreeSourceStatus.ON -> R.string.settings_free_source_on
                                FreeSourceStatus.QUOTA_EXHAUSTED -> R.string.settings_free_source_quota
                                FreeSourceStatus.OFF -> R.string.settings_free_source_off
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (state.freeSourceStatus) {
                            FreeSourceStatus.ON -> MaterialTheme.extendedColors.success
                            FreeSourceStatus.QUOTA_EXHAUSTED -> MaterialTheme.extendedColors.warning
                            FreeSourceStatus.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                onClick = null,
            )
        }
        item {
            // Designer §3.4c: gộp 1 item, màn con segmented Gemini | Groq/OpenAI.
            val preset = when {
                state.apiKeyStatus == ApiKeyStatus.SAVED -> "Gemini"
                state.openAiKeyStatus == ApiKeyStatus.SAVED -> "Groq/OpenAI"
                else -> null
            }
            val last4 = when {
                state.apiKeyStatus == ApiKeyStatus.SAVED -> state.apiKeyLast4
                state.openAiKeyStatus == ApiKeyStatus.SAVED -> state.openAiKeyLast4
                else -> null
            }
            SettingItem(
                title = stringResource(R.string.settings_provider),
                subtitle = if (preset != null) {
                    listOfNotNull(preset, last4?.let { "••••$it" }).joinToString(" · ")
                } else {
                    stringResource(R.string.settings_provider_hint)
                },
                subtitleColor = if (preset != null) {
                    MaterialTheme.extendedColors.success
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(
                                if (preset != null) {
                                    R.string.settings_api_key_saved
                                } else {
                                    R.string.settings_api_key_missing
                                },
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (preset != null) {
                                MaterialTheme.extendedColors.success
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Chevron()
                    }
                },
                onClick = { onRoute(SettingsRoute.PROVIDER) },
            )
        }
        item {
            // AC27: email liên hệ MyMemory — trống = 5k/ngày ẩn danh, có email = 50k/ngày.
            SettingItem(
                title = stringResource(R.string.settings_contact_email),
                subtitle = if (state.myMemoryContactEmail.isBlank()) {
                    stringResource(R.string.settings_contact_email_empty)
                } else {
                    stringResource(R.string.settings_contact_email_set)
                },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.myMemoryContactEmail.isNotBlank()) {
                            Text(
                                text = state.myMemoryContactEmail,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.extendedColors.success,
                            )
                        }
                        Chevron()
                    }
                },
                onClick = { dialog = SettingsDialog.CONTACT_EMAIL },
            )
        }
        item {
            SettingItem(
                title = stringResource(R.string.settings_model),
                subtitle = modelSubtitle(state.model),
                subtitleColor = modelSubtitleColor(state.model),
                trailing = { ModelTrailing(state.model, onRetry = actions.onStartModelDownload) },
                onClick = { onRoute(SettingsRoute.MODEL_DOWNLOAD) },
            )
        }

        item { SectionHeader(stringResource(R.string.settings_section_tts)) }
        item { VoiceRow(state = state, actions = actions) }
        if (!state.ukAvailable) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_voice_uk_missing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.extendedColors.warning,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = actions.onDownloadVoice) {
                        Text(stringResource(R.string.settings_download_voice))
                    }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.settings_section_data)) }
        item {
            SettingItem(
                title = stringResource(R.string.settings_delete_history),
                titleColor = MaterialTheme.colorScheme.error,
                trailing = { Chevron() },
                onClick = { dialog = SettingsDialog.DELETE_HISTORY },
            )
        }
        item {
            SettingItem(
                title = stringResource(R.string.settings_delete_vocab),
                titleColor = MaterialTheme.colorScheme.error,
                trailing = { Chevron() },
                onClick = { dialog = SettingsDialog.DELETE_VOCAB },
            )
        }
        item {
            SettingItem(
                title = stringResource(R.string.settings_delete_cache),
                titleColor = MaterialTheme.colorScheme.error,
                subtitle = state.cacheSizeLabel,
                trailing = { Chevron() },
                onClick = { dialog = SettingsDialog.DELETE_CACHE },
            )
        }

        item { SectionHeader(stringResource(R.string.settings_section_about)) }
        item {
            SettingItem(
                title = stringResource(R.string.settings_version),
                trailing = {
                    Text(
                        text = state.versionLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = null,
            )
        }
    }

    when (dialog) {
        SettingsDialog.CONTACT_EMAIL -> ContactEmailDialog(
            current = state.myMemoryContactEmail,
            onSave = { email ->
                dialog = null
                actions.onMyMemoryEmailSave(email)
                onShowMessage(context.getString(R.string.contact_email_saved_toast))
            },
            onDismiss = { dialog = null },
        )

        SettingsDialog.DELETE_HISTORY -> DeleteDataDialog(
            title = stringResource(R.string.delete_history_title),
            body = stringResource(R.string.delete_history_body),
            onConfirm = {
                dialog = null
                actions.onDeleteHistory()
                onShowMessage(context.getString(R.string.deleted_history_toast))
            },
            onDismiss = { dialog = null },
        )

        SettingsDialog.DELETE_VOCAB -> DeleteDataDialog(
            title = stringResource(R.string.delete_vocab_title),
            body = stringResource(R.string.delete_vocab_body),
            onConfirm = {
                dialog = null
                actions.onDeleteVocab()
                onShowMessage(context.getString(R.string.deleted_vocab_toast))
            },
            onDismiss = { dialog = null },
        )

        SettingsDialog.DELETE_CACHE -> DeleteDataDialog(
            title = stringResource(R.string.delete_cache_title),
            body = stringResource(R.string.delete_cache_body, state.cacheSizeLabel),
            onConfirm = {
                dialog = null
                actions.onDeleteCache()
                onShowMessage(context.getString(R.string.deleted_cache_toast))
            },
            onDismiss = { dialog = null },
        )

        null -> Unit
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingItem(
    title: String,
    subtitle: String? = null,
    subtitleColor: Color? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun Chevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PermissionBadge(status: PermissionStatus) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (status == PermissionStatus.GRANTED) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.extendedColors.success,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.extendedColors.warning,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(
                when (status) {
                    PermissionStatus.GRANTED -> R.string.settings_permission_granted
                    PermissionStatus.MISSING -> R.string.settings_permission_missing
                    PermissionStatus.BLOCKED -> R.string.settings_permission_blocked
                },
            ),
            style = MaterialTheme.typography.labelMedium,
            color = permissionColor(status),
        )
    }
}

@Composable
private fun permissionSubtitle(status: PermissionStatus): String? = when (status) {
    PermissionStatus.GRANTED -> null
    PermissionStatus.MISSING -> null
    PermissionStatus.BLOCKED -> stringResource(R.string.settings_permission_blocked_hint)
}

@Composable
private fun permissionColor(status: PermissionStatus): Color = when (status) {
    PermissionStatus.GRANTED -> MaterialTheme.extendedColors.success
    PermissionStatus.MISSING -> MaterialTheme.extendedColors.warning
    PermissionStatus.BLOCKED -> MaterialTheme.colorScheme.error
}

@Composable
private fun NotificationWarningCard(onOpen: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.extendedColors.warningContainer,
        contentColor = MaterialTheme.extendedColors.onWarningContainer,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.settings_notification_warning),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onOpen) {
                Text(stringResource(R.string.settings_open_settings))
            }
        }
    }
}

@Composable
private fun modelSubtitle(model: ModelDownloadUi): String = when (model.status) {
    ModelDownloadStatus.NOT_DOWNLOADED -> stringResource(R.string.settings_model_not_downloaded)
    ModelDownloadStatus.DOWNLOADING -> stringResource(R.string.settings_model_downloading_indeterminate)

    ModelDownloadStatus.READY -> stringResource(R.string.settings_model_ready)
    ModelDownloadStatus.FAILED -> stringResource(R.string.settings_model_failed)
}

@Composable
private fun modelSubtitleColor(model: ModelDownloadUi): Color = when (model.status) {
    ModelDownloadStatus.READY -> MaterialTheme.extendedColors.success
    ModelDownloadStatus.FAILED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun ModelTrailing(model: ModelDownloadUi, onRetry: () -> Unit) {
    when (model.status) {
        ModelDownloadStatus.DOWNLOADING -> CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
        )

        ModelDownloadStatus.READY -> Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.extendedColors.success,
        )

        ModelDownloadStatus.FAILED -> TextButton(onClick = onRetry) {
            Text(stringResource(R.string.settings_model_retry))
        }

        else -> Chevron()
    }
}

@Composable
private fun VoiceRow(state: SettingsUiState, actions: SettingsActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_voice_default),
            style = MaterialTheme.typography.bodyLarge,
        )
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                selected = state.voice == Voice.US,
                onClick = { actions.onVoiceChange(Voice.US) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                label = { Text(stringResource(R.string.voice_us)) },
            )
            SegmentedButton(
                selected = state.voice == Voice.UK,
                onClick = { actions.onVoiceChange(Voice.UK) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                enabled = state.ukAvailable,
                label = { Text(stringResource(R.string.voice_uk)) },
            )
        }
    }
}

@Composable
private fun ContactEmailDialog(
    current: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by rememberSaveable(current) { mutableStateOf(current) }
    val trimmed = input.trim()
    // Designer: email phải có '@' và không có khoảng trắng; rỗng = cho phép xoá.
    val isValid = trimmed.isEmpty() || (trimmed.contains('@') && !trimmed.contains(' '))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.contact_email_title)) },
        text = {
            Column {
                Text(stringResource(R.string.contact_email_body))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.contact_email_label)) },
                    singleLine = true,
                    isError = !isValid,
                    supportingText = if (!isValid) {
                        { Text(stringResource(R.string.contact_email_invalid)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(trimmed) },
                enabled = isValid,
            ) {
                Text(stringResource(R.string.contact_email_save))
            }
        },
        dismissButton = {
            Row {
                if (current.isNotBlank()) {
                    TextButton(onClick = { onSave("") }) {
                        Text(stringResource(R.string.contact_email_clear))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        },
    )
}

@Composable
private fun DeleteDataDialog(title: String, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.common_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
) {
    // Designer §3.4c: mặc định Gemini; đổi preset không xoá key của preset kia (store riêng).
    var preset by rememberSaveable {
        mutableStateOf(
            if (state.apiKeyStatus == ApiKeyStatus.MISSING &&
                state.openAiKeyStatus == ApiKeyStatus.SAVED
            ) {
                ProviderPreset.OPENAI
            } else {
                ProviderPreset.GEMINI
            },
        )
    }
    var keyInput by rememberSaveable { mutableStateOf("") }
    var baseUrl by rememberSaveable(state.openAiBaseUrl) { mutableStateOf(state.openAiBaseUrl) }
    var model by rememberSaveable(state.openAiModel) { mutableStateOf(state.openAiModel) }
    var visible by rememberSaveable { mutableStateOf(false) }
    val savedMessage = stringResource(R.string.provider_saved_toast)
    val isGemini = preset == ProviderPreset.GEMINI
    val savedLast4 = if (isGemini) state.apiKeyLast4 else state.openAiKeyLast4
    val hasSavedKey = if (isGemini) {
        state.apiKeyStatus == ApiKeyStatus.SAVED
    } else {
        state.openAiKeyStatus == ApiKeyStatus.SAVED
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_provider_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = isGemini,
                    onClick = {
                        preset = ProviderPreset.GEMINI
                        keyInput = ""
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) {
                    Text(stringResource(R.string.provider_preset_gemini))
                }
                SegmentedButton(
                    selected = !isGemini,
                    onClick = {
                        preset = ProviderPreset.OPENAI
                        keyInput = ""
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) {
                    Text(stringResource(R.string.provider_preset_openai))
                }
            }
            if (!isGemini) {
                // Gemini: baseUrl/model đặt sẵn, ẩn đi (Designer §3.4c).
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.provider_base_url)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.provider_model)) },
                    singleLine = true,
                )
            }
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        if (isGemini) {
                            stringResource(R.string.api_key_label)
                        } else {
                            stringResource(R.string.provider_key)
                        },
                    )
                },
                singleLine = true,
                visualTransformation = if (visible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    TextButton(onClick = { visible = !visible }) {
                        Text(stringResource(if (visible) R.string.api_key_hide else R.string.api_key_show))
                    }
                },
                supportingText = { Text(stringResource(R.string.provider_key_support)) },
            )
            if (hasSavedKey) {
                Text(
                    text = "••••${savedLast4.orEmpty()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.extendedColors.success,
                )
            }
            Button(
                onClick = {
                    if (isGemini) {
                        if (keyInput.isNotBlank()) {
                            actions.onApiKeySave(keyInput.trim())
                            keyInput = ""
                        }
                    } else {
                        actions.onOpenAiConfigSave(baseUrl, model)
                        if (keyInput.isNotBlank()) {
                            actions.onOpenAiKeySave(keyInput.trim())
                            keyInput = ""
                        }
                    }
                    onShowMessage(savedMessage)
                },
                enabled = if (isGemini) {
                    keyInput.isNotBlank() || hasSavedKey
                } else {
                    baseUrl.isNotBlank() && model.isNotBlank()
                },
            ) {
                Text(stringResource(R.string.provider_save))
            }
        }
    }
}

private enum class ProviderPreset { GEMINI, OPENAI }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiKeyScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val savedMessage = stringResource(R.string.api_key_saved_toast)
    val deletedMessage = stringResource(R.string.api_key_deleted_toast)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.api_key_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.api_key_label)) },
                singleLine = true,
                visualTransformation = if (visible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    TextButton(onClick = { visible = !visible }) {
                        Text(
                            stringResource(if (visible) R.string.api_key_hide else R.string.api_key_show),
                        )
                    }
                },
                supportingText = { Text(stringResource(R.string.api_key_support)) },
            )

            Button(
                onClick = {
                    actions.onApiKeySave(input.trim())
                    input = ""
                    onShowMessage(savedMessage)
                },
                enabled = input.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.api_key_save))
            }

            TextButton(
                onClick = { actions.onApiKeyTest(input.trim()) },
                enabled = (input.isNotBlank() || state.apiKeyStatus == ApiKeyStatus.SAVED) &&
                    state.apiKeyTest != ApiKeyTestStatus.CHECKING,
            ) {
                if (state.apiKeyTest == ApiKeyTestStatus.CHECKING) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.api_key_checking))
                } else {
                    Text(stringResource(R.string.api_key_check))
                }
            }

            when (state.apiKeyTest) {
                ApiKeyTestStatus.OK -> InfoBanner(
                    text = stringResource(R.string.api_key_check_ok),
                    container = MaterialTheme.extendedColors.successContainer,
                    content = MaterialTheme.extendedColors.onSuccessContainer,
                    icon = Icons.Filled.Check,
                )

                ApiKeyTestStatus.FAILED -> InfoBanner(
                    text = stringResource(R.string.api_key_check_failed) + "\n" +
                        stringResource(R.string.api_key_check_failed_hint),
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                    icon = Icons.Filled.Warning,
                )

                else -> Unit
            }

            if (confirmDelete) {
                AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    title = { Text(stringResource(R.string.api_key_delete_title)) },
                    text = { Text(stringResource(R.string.api_key_delete_body)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                confirmDelete = false
                                actions.onApiKeyDelete()
                                onShowMessage(deletedMessage)
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Text(stringResource(R.string.common_delete))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDelete = false }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    },
                )
            }

            if (state.apiKeyStatus == ApiKeyStatus.SAVED) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "••••${state.apiKeyLast4 ?: ""}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(R.string.settings_api_key_saved),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.extendedColors.success,
                        )
                    }
                }
                TextButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.api_key_delete))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDownloadScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.model_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.model_desc_size), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.model_desc_quality), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(R.string.model_desc_private),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            when (state.model.status) {
                ModelDownloadStatus.DOWNLOADING -> {
                    // UX-03 (Designer chốt): ML Kit không có API tiến trình -> indeterminate, không hứa %.
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = stringResource(R.string.settings_model_downloading_indeterminate),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(R.string.model_downloading_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                ModelDownloadStatus.READY -> InfoBanner(
                    text = stringResource(R.string.settings_model_ready),
                    container = MaterialTheme.extendedColors.successContainer,
                    content = MaterialTheme.extendedColors.onSuccessContainer,
                    icon = Icons.Filled.Check,
                )

                ModelDownloadStatus.FAILED -> InfoBanner(
                    text = stringResource(R.string.settings_model_failed),
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                    icon = Icons.Filled.Warning,
                )

                ModelDownloadStatus.NOT_DOWNLOADED -> Text(
                    text = stringResource(R.string.settings_model_not_downloaded),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))
            when (state.model.status) {
                ModelDownloadStatus.READY -> {
                    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.model_downloaded))
                    }
                    TextButton(
                        onClick = actions.onDeleteModel,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(stringResource(R.string.model_delete))
                    }
                }

                ModelDownloadStatus.DOWNLOADING -> {
                    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.model_downloading_button))
                    }
                }

                else -> {
                    Button(onClick = actions.onStartModelDownload, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.model_download))
                    }
                    Text(
                        text = stringResource(R.string.model_wifi_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionRationaleScreen(
    icon: Any,
    title: String,
    body: String,
    whereText: String,
    primaryLabel: String,
    primaryDestructive: Boolean = false,
    onPrimary: () -> Unit,
    onLater: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onLater) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.perm_later),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            when (icon) {
                is Painter -> Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                is androidx.compose.ui.graphics.vector.ImageVector -> Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.perm_where_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = whereText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (primaryDestructive) {
                TextButton(
                    onClick = onPrimary,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(primaryLabel)
                }
            } else {
                Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) {
                    Text(primaryLabel)
                }
            }
            TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.perm_later))
            }
        }
    }
}

@Composable
private fun InfoBanner(text: String, container: Color, content: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text = text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Mở trang "Hiển thị trên ứng dụng khác" của app trong Settings hệ thống. */
fun openOverlaySettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    )
    runCatching { context.startActivity(intent) }
}

/** Mở trang thông báo của app trong Settings hệ thống. */
fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching { context.startActivity(intent) }
}

/** Mở màn tải voice pack TTS (E14/AC21). */
fun openTtsInstall(context: Context) {
    val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
    runCatching { context.startActivity(intent) }.onFailure {
        runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }
}