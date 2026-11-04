package vn.bubbledict.ui.main

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import vn.bubbledict.BubbleDictApp
import vn.bubbledict.R
import vn.bubbledict.data.update.UpdateErrorCodes
import vn.bubbledict.data.update.UpdateManager
import vn.bubbledict.ui.settings.PermissionStatus
import vn.bubbledict.ui.settings.SettingsActions
import vn.bubbledict.ui.settings.SettingsRoute
import vn.bubbledict.ui.settings.SettingsScreen
import vn.bubbledict.ui.settings.SettingsViewModel
import vn.bubbledict.ui.settings.openTtsInstall
import vn.bubbledict.ui.theme.BubbleDictTheme
import vn.bubbledict.ui.update.UpdateBanner
import vn.bubbledict.ui.update.UpdateBannerState

/**
 * Màn chính tối giản (Designer UX-01 v2, user chốt 2026-11-04):
 * bảng điều khiển bong bóng + nút mở Cài đặt. Không còn tab Lịch sử/Từ vựng.
 *
 * Điều hướng bằng cờ [MainScreen] state, không dùng thư viện nav.
 */
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TAB = "extra_tab"
        const val TAB_HISTORY = 0
        const val TAB_VOCAB = 1
        const val TAB_SETTINGS = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BubbleDictTheme {
                MainScreen()
            }
        }
    }
}

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    // §1: điều hướng 2 màn bằng state, không cần navigation-compose.
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var settingsRoute by remember { mutableStateOf(SettingsRoute.LIST) }

    // F12: kiểm tra update khi mở app (spec §5.10). Lỗi mạng thì im lặng.
    val updateManager = remember(context) {
        (context.applicationContext as BubbleDictApp).container.updateManager
    }
    val updateState by updateManager.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { runCatching { updateManager.check() } }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            val banner = updateState.toUpdateBannerState()
            if (banner.isVisible) {
                UpdateBanner(
                    state = banner,
                    onUpdate = { scope.launch { runCatching { updateManager.downloadAndInstall() } } },
                    onSkip = { scope.launch { runCatching { updateManager.skipCurrent() } } },
                    onOpenInstallPermission = {
                        updateManager.openInstallPermissionSettings()
                        updateManager.resetError()
                    },
                )
            }
            if (showSettings) {
                BackHandler(enabled = settingsRoute == SettingsRoute.LIST) {
                    showSettings = false
                    settingsRoute = SettingsRoute.LIST
                }
                SettingsContent(
                    initialRoute = settingsRoute,
                    onShowMessage = showMessage,
                    onBackFromRoot = {
                        showSettings = false
                        settingsRoute = SettingsRoute.LIST
                    },
                )
            } else {
                HomeContent(
                    onOpenSettings = {
                        settingsRoute = SettingsRoute.LIST
                        showSettings = true
                    },
                    onOpenOverlayPermission = {
                        settingsRoute = SettingsRoute.PERMISSION_OVERLAY
                        showSettings = true
                    },
                    onShowMessage = showMessage,
                )
            }
        }
    }
}

/** Màn chính: bong bóng 56dp + switch + hướng dẫn + nút Cài đặt (UX-01 v2 §2). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    onOpenSettings: () -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var overlayStatus by remember { mutableStateOf(overlayPermissionStatus(context)) }

    // Đọc lại quyền mỗi lần quay về app (user cấp quyền ở Settings hệ thống).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayStatus = overlayPermissionStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message -> onShowMessage(context.getString(message.textRes)) }
    }
    // F2/P3: service bị kill sau reboot — bật lại nếu user đã bật bong bóng.
    LaunchedEffect(Unit) { viewModel.restoreBubbleIfEnabled() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.app_name)) },
            actions = {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.home_open_settings),
                    )
                }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            // Visual bong bóng thật của app (BUG-18: không dùng painterResource cho
            // GradientDrawable — Compose chỉ nhận vector/raster; vẽ lại bằng Brush).
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .shadow(10.dp, CircleShape)
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFF4A5FA5), Color(0xFF7C5AC8)),
                        ),
                        shape = CircleShape,
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.bubble_glyph),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.home_bubble_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    if (state.bubbleEnabled) R.string.home_bubble_on else R.string.home_bubble_off,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Switch(
                checked = state.bubbleEnabled,
                onCheckedChange = { checked ->
                    if (checked && overlayStatus != PermissionStatus.GRANTED) {
                        // UX-03 §3.1: chưa có quyền -> màn giải thích, switch chưa bật.
                        onOpenOverlayPermission()
                    } else {
                        viewModel.setBubbleEnabled(checked)
                    }
                },
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.home_guide),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 280.dp),
            )
            if (overlayStatus != PermissionStatus.GRANTED) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.tertiaryContainer,
                            RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.home_need_overlay),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = onOpenOverlayPermission) {
                        Text(stringResource(R.string.home_grant))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            FilledTonalButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            ) {
                Text(stringResource(R.string.home_open_settings))
            }
        }
    }
}

/** Bọc [SettingsScreen] ở dạng màn con: state + actions + back về màn chính. */
@Composable
private fun SettingsContent(
    initialRoute: SettingsRoute,
    onShowMessage: (String) -> Unit,
    onBackFromRoot: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: SettingsViewModel = viewModel()
    val baseState by viewModel.state.collectAsStateWithLifecycle()
    var overlayStatus by remember { mutableStateOf(overlayPermissionStatus(context)) }
    var notificationStatus by remember { mutableStateOf(notificationPermissionStatus(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayStatus = overlayPermissionStatus(context)
                notificationStatus = notificationPermissionStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message -> onShowMessage(context.getString(message.textRes)) }
    }
    LaunchedEffect(Unit) { viewModel.refreshCacheBytes() }

    SettingsScreen(
        state = baseState.copy(
            overlayPermission = overlayStatus,
            notificationPermission = notificationStatus,
        ),
        initialRoute = initialRoute,
        onShowMessage = onShowMessage,
        onBackFromRoot = onBackFromRoot,
        actions = SettingsActions(
            onBubbleToggle = viewModel::setBubbleEnabled,
            onNotificationResult = { granted ->
                notificationStatus = if (granted) PermissionStatus.GRANTED else PermissionStatus.MISSING
            },
            onApiKeySave = viewModel::saveApiKey,
            onApiKeyDelete = viewModel::clearApiKey,
            onApiKeyTest = viewModel::testApiKey,
            onMyMemoryEmailSave = viewModel::saveMyMemoryContactEmail,
            onOpenAiKeySave = viewModel::saveOpenAiKey,
            onOpenAiConfigSave = viewModel::saveOpenAiConfig,
            onStartModelDownload = viewModel::requestModelDownload,
            onDeleteModel = viewModel::deleteModel,
            onVoiceChange = viewModel::setVoice,
            onDownloadVoice = { openTtsInstall(context) },
            onOnlineSourcesToggle = viewModel::setOnlineSourcesEnabled,
            onTargetLangChange = viewModel::setTargetLang,
            onDownloadModelFor = viewModel::downloadModelFor,
            onDeleteModelFor = viewModel::deleteModelFor,
            onDownloadAllModels = viewModel::downloadAllModels,
            onDeleteHistory = viewModel::deleteHistory,
            onDeleteVocab = viewModel::deleteVocab,
            onDeleteCache = viewModel::deleteCache,
        ),
    )
}

private fun overlayPermissionStatus(context: Context): PermissionStatus =
    if (Settings.canDrawOverlays(context)) PermissionStatus.GRANTED else PermissionStatus.MISSING

private fun notificationPermissionStatus(context: Context): PermissionStatus {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return PermissionStatus.GRANTED
    val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
    return if (granted) PermissionStatus.GRANTED else PermissionStatus.MISSING
}

/**
 * F12: map trạng thái BE [UpdateManager.State] sang trạng thái banner UI.
 * E24 (mất mạng/manifest lỗi) im lặng theo spec §5.10; E26/E27 có nhánh riêng.
 */
private fun UpdateManager.State.toUpdateBannerState(): UpdateBannerState = when (this) {
    UpdateManager.State.Idle,
    UpdateManager.State.Checking,
    UpdateManager.State.InstallerOpened,
    -> UpdateBannerState()

    is UpdateManager.State.Available -> UpdateBannerState(
        versionName = versionName,
        notes = notes.ifBlank { null },
        phase = UpdateBannerState.Phase.AVAILABLE,
    )

    is UpdateManager.State.Downloading -> UpdateBannerState(
        phase = UpdateBannerState.Phase.DOWNLOADING,
        progress = (percent.coerceIn(0, 100) / 100f),
    )

    is UpdateManager.State.Failed -> when (code) {
        // E24: kiểm tra lỗi/timeout/offline -> không hiện gì.
        UpdateErrorCodes.NETWORK,
        UpdateErrorCodes.BAD_MANIFEST,
        -> UpdateBannerState()

        // E26: sha256 sai hoặc file không phải APK.
        UpdateErrorCodes.CHECKSUM -> UpdateBannerState(phase = UpdateBannerState.Phase.INVALID_FILE)
        // E27: Android chặn cài.
        UpdateErrorCodes.INSTALL_PERMISSION ->
            UpdateBannerState(phase = UpdateBannerState.Phase.NEED_INSTALL_PERMISSION)

        // E25: tải lỗi/timeout.
        else -> UpdateBannerState(phase = UpdateBannerState.Phase.FAILED)
    }
}
