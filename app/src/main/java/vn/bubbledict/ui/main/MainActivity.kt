package vn.bubbledict.ui.main

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import vn.bubbledict.ui.update.UpdateBanner
import vn.bubbledict.ui.update.UpdateBannerState
import vn.bubbledict.ui.settings.PermissionStatus
import vn.bubbledict.ui.settings.SettingsActions
import vn.bubbledict.ui.history.HistoryScreen
import vn.bubbledict.ui.history.HistoryViewModel
import vn.bubbledict.ui.settings.SettingsScreen
import vn.bubbledict.ui.vocab.VocabScreen
import vn.bubbledict.ui.vocab.VocabViewModel
import vn.bubbledict.ui.settings.SettingsViewModel
import vn.bubbledict.ui.settings.openTtsInstall
import vn.bubbledict.ui.theme.BubbleDictTheme
import vn.bubbledict.ui.theme.Motion
import vn.bubbledict.ui.theme.topHairline

/**
 * FE-01 — MainActivity 3 tab (Lịch sử / Từ vựng / Cài đặt) theo docs/design/mvp-screens.md.
 *
 * Launcher mở thẳng tab Lịch sử, không màn chào, không xin quyền khi mở lần đầu (AC23).
 * Tab Cài đặt dùng [SettingsScreen] (FE-05); hiện state cục bộ, chờ BE-06/BE-09/BE-13 nối ViewModel.
 */
class MainActivity : ComponentActivity() {

    companion object {
        /** Tab đích khi mở app từ ngoài (ví dụ nút "Nhập API key" ở sheet kết quả). */
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
                MainScreen(
                    initialTab = intent.getIntExtra(EXTRA_TAB, TAB_HISTORY),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(initialTab: Int = MainActivity.TAB_HISTORY) {
    // F11/AC25: nút "Nhập API key" trong sheet mở thẳng tab Cài đặt.
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    val historyViewModel: HistoryViewModel = viewModel()
    val vocabViewModel: VocabViewModel = viewModel()

    // F12: kiểm tra update khi mở app (spec §5.10). Lỗi mạng thì im lặng — BE trả Idle/Failed(Network).
    val updateManager = remember(context) {
        (context.applicationContext as BubbleDictApp).container.updateManager
    }
    val updateState by updateManager.state.collectAsStateWithLifecycle()
    // check() có gate 1 lần/ngày phía BE; "Bỏ qua" lưu skippedUpdateVersionCode trong DataStore.
    LaunchedEffect(Unit) { runCatching { updateManager.check() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // UX-P1 §3.3: hairline trên đỉnh nav + icon tab chọn dịch lên 1dp.
            NavigationBar(modifier = Modifier.topHairline()) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    colors = navItemColors(),
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_history),
                            contentDescription = null,
                            modifier = iconLift(selectedTab == 0),
                        )
                    },
                    label = { Text(stringResource(R.string.tab_history)) },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    colors = navItemColors(),
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_star_border),
                            contentDescription = null,
                            modifier = iconLift(selectedTab == 1),
                        )
                    },
                    label = { Text(stringResource(R.string.tab_vocab)) },
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    colors = navItemColors(),
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            modifier = iconLift(selectedTab == 2),
                        )
                    },
                    label = { Text(stringResource(R.string.tab_settings)) },
                )
            }
        },
    ) { innerPadding ->
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
            // UX-P1 §3.3: đổi tab bằng crossfade 120ms, không trượt ngang (3 tab không phải pager).
            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(durationMillis = Motion.ms(Motion.tabCrossfadeMs)),
                label = "tabSwitch",
            ) { tab ->
                when (tab) {
                    0 -> HistoryScreen(
                        viewModel = historyViewModel,
                        snackbarHostState = snackbarHostState,
                    )

                    1 -> VocabScreen(
                        viewModel = vocabViewModel,
                        snackbarHostState = snackbarHostState,
                    )

                    else -> SettingsTab(onShowMessage = showMessage)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListTab(
    title: String,
    searchHint: String,
    query: String,
    onQueryChange: (String) -> Unit,
    menuLabels: List<String>,
    dangerMenuIndex: Int?,
    onMenuClick: (Int) -> Unit,
    emptyTitle: String,
    emptyBody: String,
    iconRes: Int,
) {
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        if (searchActive) {
            TopAppBar(
                modifier = Modifier.topHairline(),
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(searchFocus),
                        placeholder = { Text(searchHint) },
                        singleLine = true,
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            searchActive = false
                            onQueryChange("")
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_close_search),
                        )
                    }
                },
            )
            LaunchedEffect(Unit) {
                runCatching { searchFocus.requestFocus() }
            }
        } else {
            TopAppBar(
                modifier = Modifier.topHairline(),
                title = { Text(title) },
                actions = {
                    IconButton(onClick = { searchActive = true }) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = stringResource(R.string.action_search),
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.action_more),
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            menuLabels.forEachIndexed { index, label ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = label,
                                            color = if (dangerMenuIndex == index) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                Color.Unspecified
                                            },
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onMenuClick(index)
                                    },
                                )
                            }
                        }
                    }
                },
            )
        }

        EmptyTab(title = emptyTitle, body = emptyBody, iconRes = iconRes)
    }
}

@Composable
private fun EmptyTab(title: String, body: String, iconRes: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun SettingsTab(onShowMessage: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: SettingsViewModel = viewModel()
    val baseState by viewModel.state.collectAsStateWithLifecycle()
    var overlayStatus by remember { mutableStateOf(overlayPermissionStatus(context)) }
    var notificationStatus by remember { mutableStateOf(notificationPermissionStatus(context)) }

    // Đọc lại quyền mỗi lần quay về app (user cấp quyền ở Settings hệ thống).
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

    // Dung lượng cache đổi sau mỗi lượt tra → tính lại khi mở tab Cài đặt.
    LaunchedEffect(Unit) { viewModel.refreshCacheBytes() }

    // F2/P3: service bị kill sau reboot — bật lại nếu user đã bật bong bóng.
    LaunchedEffect(Unit) { viewModel.restoreBubbleIfEnabled() }

    SettingsScreen(
        state = baseState.copy(
            overlayPermission = overlayStatus,
            notificationPermission = notificationStatus,
        ),
        onShowMessage = onShowMessage,
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

private fun versionLabel(context: Context): String = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }
    "${info.versionName} ($code)"
}.getOrDefault("?")

/** UX-P1 §3.3 — màu item nav: chữ chưa chọn 62% để item đang chọn nổi hơn. */
@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
)

/** UX-P1 §3.3 — icon tab được chọn dịch lên 1dp (150ms). */
@Composable
private fun iconLift(selected: Boolean): Modifier = if (selected) {
    Modifier.offset(y = (-1).dp)
} else {
    Modifier
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
