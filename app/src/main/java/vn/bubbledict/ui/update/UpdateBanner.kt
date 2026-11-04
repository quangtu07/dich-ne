package vn.bubbledict.ui.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.bubbledict.R

/**
 * F12 — banner cập nhật (APK cài ngoài Play), spec mvp-spec §5.10.
 *
 * UI thuần, không gọi mạng: trạng thái do tầng BE (`UpdateChecker`) đẩy vào qua
 * [UpdateBannerState], hành động đi ra bằng callback.
 *
 * Ràng buộc spec: chỉ hiện khi có bản mới hơn và user chưa bỏ qua bản đó (BE lo);
 * bấm "Cập nhật" mới tải; sha256 sai (E26) và Android chặn cài (E27) có nhánh riêng.
 */
data class UpdateBannerState(
    val versionCode: Int = 0,
    val versionName: String = "",
    val notes: String? = null,
    val phase: Phase = Phase.IDLE,
    /** 0f..1f khi biết tiến độ; null = đang tải nhưng chưa biết %. */
    val progress: Float? = null,
) {
    enum class Phase {
        /** Không có bản mới, hoặc E24 (lỗi mạng) — im lặng, không hiện gì. */
        IDLE,
        AVAILABLE,
        DOWNLOADING,
        /** E25 — tải lỗi/timeout. */
        FAILED,
        /** E26 — sha256 không khớp hoặc file không phải APK. */
        INVALID_FILE,
        /** E27 — Android chặn cài (chưa cho phép "Cài từ nguồn không xác định"). */
        NEED_INSTALL_PERMISSION,
    }

    val isVisible: Boolean get() = phase != Phase.IDLE
}

@Composable
fun UpdateBanner(
    state: UpdateBannerState,
    onUpdate: () -> Unit,
    onSkip: () -> Unit,
    onOpenInstallPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state.isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = when (state.phase) {
                            UpdateBannerState.Phase.FAILED,
                            UpdateBannerState.Phase.INVALID_FILE,
                            UpdateBannerState.Phase.NEED_INSTALL_PERMISSION,
                            -> Icons.Filled.Warning

                            else -> Icons.Filled.Refresh
                        },
                        contentDescription = null,
                        tint = when (state.phase) {
                            UpdateBannerState.Phase.FAILED,
                            UpdateBannerState.Phase.INVALID_FILE,
                            UpdateBannerState.Phase.NEED_INSTALL_PERMISSION,
                            -> MaterialTheme.colorScheme.error

                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(24.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = when (state.phase) {
                                UpdateBannerState.Phase.DOWNLOADING ->
                                    stringResource(R.string.update_downloading)

                                UpdateBannerState.Phase.FAILED ->
                                    stringResource(R.string.update_error_download)

                                UpdateBannerState.Phase.INVALID_FILE ->
                                    stringResource(R.string.update_error_checksum)

                                UpdateBannerState.Phase.NEED_INSTALL_PERMISSION ->
                                    stringResource(R.string.update_error_permission)

                                else -> stringResource(R.string.update_banner_title_version, state.versionName)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        when (state.phase) {
                            UpdateBannerState.Phase.DOWNLOADING -> {
                                val progress = state.progress
                                if (progress != null) {
                                    LinearProgressIndicator(
                                        progress = { progress.coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .fillMaxWidth()
                                            .widthIn(max = 220.dp),
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .fillMaxWidth()
                                            .widthIn(max = 220.dp),
                                    )
                                }
                            }

                            UpdateBannerState.Phase.AVAILABLE -> {
                                val notes = state.notes?.takeIf { it.isNotBlank() }
                                Text(
                                    text = notes ?: stringResource(R.string.update_banner_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            UpdateBannerState.Phase.NEED_INSTALL_PERMISSION -> Text(
                                text = stringResource(R.string.update_error_permission),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )

                            else -> Unit
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (state.phase) {
                        UpdateBannerState.Phase.AVAILABLE -> {
                            TextButton(onClick = onSkip) {
                                Text(stringResource(R.string.update_banner_skip))
                            }
                            Button(onClick = onUpdate) {
                                Text(stringResource(R.string.update_banner_action))
                            }
                        }

                        UpdateBannerState.Phase.FAILED -> {
                            TextButton(onClick = onSkip) {
                                Text(stringResource(R.string.update_banner_skip))
                            }
                            Button(onClick = onUpdate) {
                                Text(stringResource(R.string.update_banner_retry))
                            }
                        }

                        UpdateBannerState.Phase.INVALID_FILE -> TextButton(onClick = onSkip) {
                            Text(stringResource(R.string.update_banner_skip))
                        }

                        UpdateBannerState.Phase.NEED_INSTALL_PERMISSION -> {
                            TextButton(onClick = onSkip) {
                                Text(stringResource(R.string.update_banner_skip))
                            }
                            Button(onClick = onOpenInstallPermission) {
                                Text(stringResource(R.string.update_banner_open_settings))
                            }
                        }

                        // Đang tải: không cho bấm gì (tránh tải 2 lần), không có nút.
                        else -> Unit
                    }
                }
            }
        }
    }
}
