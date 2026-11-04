package vn.bubbledict.ui.result

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale
import vn.bubbledict.R
import vn.bubbledict.ui.theme.sheetGradientBrush
import vn.bubbledict.ui.theme.sheetTopHighlight
import vn.bubbledict.domain.model.TextKind
import vn.bubbledict.domain.model.TextSource
import vn.bubbledict.ui.theme.extendedColors

/**
 * C3 — Cửa sổ kết quả (bottom sheet) theo docs/design/result-sheet.md.
 *
 * Render đủ 10 state S1–S10 (mục 5), biến thể WORD/SENTENCE/PARAGRAPH/OCR, hàng phát âm
 * UK/US, mode sửa text OCR (E11). Composable không gọi repository: dữ liệu đi qua
 * [ResultUiState], hành động đi qua [ResultActions] để ViewModel (FE-03) nối vào.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultSheet(
    state: ResultUiState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    actions: ResultActions = ResultActions(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        // UX-P1 §3.2: nền sheet = đầu gradient để chỗ trống cuối sheet không lệch màu.
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        // §3.2: scrim 38% (trước 32%) — mắt dồn về sheet, cảm giác chiều sâu hơn.
        scrimColor = Color.Black.copy(alpha = 0.38f),
        // §3.2: drag handle "đúc" — gradient ngang, giữa đậm hơn hai đầu.
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.30f),
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.30f),
                            ),
                        ),
                        shape = RoundedCornerShape(percent = 50),
                    ),
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(sheetGradientBrush())
                .sheetTopHighlight(),
        ) {
            ResultSheetContent(state = state, actions = actions, onClose = onDismissRequest)
        }
    }
}

@Composable
private fun ResultSheetContent(
    state: ResultUiState,
    actions: ResultActions,
    onClose: () -> Unit,
) {
    val config = LocalConfiguration.current
    val maxHeightFraction = when {
        state.editing -> 0.85f
        config.orientation == Configuration.ORIENTATION_LANDSCAPE -> 0.70f
        else -> 0.60f
    }
    val maxHeight = (config.screenHeightDp * maxHeightFraction).dp

    val closeFocus = remember { FocusRequester() }
    var showVoiceDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // UX-02 §9: nút X là phần tử focus đầu tiên trong sheet.
        runCatching { closeFocus.requestFocus() }
    }

    val loading = state.status == ResultStatus.LOADING
    val onSpeak = {
        when {
            !state.ttsReady -> showVoiceDialog = true
            state.voice == Voice.UK && !state.ukAvailable -> showVoiceDialog = true
            else -> actions.onSpeakToggle()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
    ) {
        HeaderRow(state = state, onClose = onClose, closeFocus = closeFocus)
        // BUG-23 (F14): nguồn = đích -> nói rõ, không gọi API, không hứa bản dịch.
        if (state.sameLanguage) {
            Text(
                text = stringResource(R.string.result_same_language_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // E29 (F14): không chắc ngôn ngữ nguồn -> nói rõ dưới hàng chip, không chặn.
        if (state.sourceLangUncertain) {
            Text(
                text = stringResource(R.string.result_lang_uncertain_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        if (state.editing) {
            OriginalTextField(state = state, actions = actions)
        } else {
            OriginalTextBlock(state = state, actions = actions)
        }

        if (state.isOcr && !state.editing && !loading) {
            Text(
                text = stringResource(R.string.ocr_recognized_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // F14: TTS chỉ có giọng EN -> nguồn khác EN ẩn hàng phát âm im lặng.
        if (state.isWord && !state.editing && state.ttsAvailableForSource) {
            Spacer(Modifier.height(8.dp))
            PronunciationRow(state = state, onSpeak = onSpeak, onVoiceChange = actions.onVoiceChange)
        } else if (!state.editing && state.ttsReady && state.ttsAvailableForSource) {
            Spacer(Modifier.height(8.dp))
        }

        if (!state.editing) {
            Spacer(Modifier.height(12.dp))
            ResultBody(state = state)
            Spacer(Modifier.height(16.dp))

            if (state.examples.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(16.dp))
            }
        }

        ActionRow(
            state = state,
            actions = actions,
            loading = loading,
            onSpeak = onSpeak,
        )
    }

    if (showVoiceDialog) {
        VoiceMissingDialog(
            ttsReady = state.ttsReady,
            usAvailable = state.usAvailable,
            onDismiss = { showVoiceDialog = false },
            onOpenSettings = {
                showVoiceDialog = false
                actions.onOpenTtsSettings()
            },
            onUseUs = {
                showVoiceDialog = false
                actions.onUseUsVoice()
            },
        )
    }
}

@Composable
private fun HeaderRow(
    state: ResultUiState,
    onClose: () -> Unit,
    closeFocus: FocusRequester,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindChip(kind = state.kind, source = state.source)
        Spacer(Modifier.width(8.dp))
        // F14: chip cặp ngôn ngữ thật "· ZH → VI"; E29 (không chắc nguồn) -> "? → VI".
        Text(
            text = languagePairLabel(state),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .size(48.dp)
                .focusRequester(closeFocus),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.a11y_close_sheet),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** F14: "· ZH → VI" (ghép sau chip loại); E29 -> "· ? → VI". */
@Composable
private fun languagePairLabel(state: ResultUiState): String {
    val source = if (state.sourceLangUncertain) "?" else state.sourceLang.uppercase()
    return "· $source → ${state.targetLang.uppercase()}"
}

@Composable
private fun KindChip(kind: TextKind, source: TextSource) {
    val label = stringResource(
        if (source == TextSource.OCR) {
            R.string.result_type_ocr
        } else when (kind) {
            TextKind.WORD -> R.string.result_type_word
            TextKind.SENTENCE -> R.string.result_type_sentence
            TextKind.PARAGRAPH -> R.string.result_type_passage
        },
    )
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun OriginalTextBlock(state: ResultUiState, actions: ResultActions) {
    var overflow by remember(state.originalText) { mutableStateOf(false) }
    val collapsed = !state.isExpanded
    val maxLines = when {
        state.isWord -> 2
        else -> 3
    }
    Text(
        text = state.originalText,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = if (collapsed) maxLines else Int.MAX_VALUE,
        overflow = if (collapsed) TextOverflow.Ellipsis else TextOverflow.Clip,
        onTextLayout = { layout -> if (collapsed) overflow = layout.hasVisualOverflow },
    )
    if (overflow) {
        TextButton(
            onClick = actions.onToggleExpanded,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(
                text = stringResource(
                    if (collapsed) R.string.action_see_more else R.string.action_collapse,
                ),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** E11 — mode sửa text OCR trong cùng sheet (UX-02 §4.4). */
@Composable
private fun OriginalTextField(state: ResultUiState, actions: ResultActions) {
    val editFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { editFocus.requestFocus() } }
    OutlinedTextField(
        value = state.editText,
        onValueChange = actions.onEditTextChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(editFocus),
        label = { Text(stringResource(R.string.ocr_review_label)) },
        minLines = 5,
        maxLines = 8,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = MaterialTheme.shapes.small,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (state.editText.isBlank()) {
            Text(
                text = stringResource(R.string.ocr_no_text_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extendedColors.warning,
            )
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { actions.onEditTextChange("") }) {
            Text(stringResource(R.string.action_clear))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PronunciationRow(
    state: ResultUiState,
    onSpeak: () -> Unit,
    onVoiceChange: (Voice) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        state.ipa?.let { ipa ->
            Text(
                text = ipa,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
        SpeakButton(state = state, onSpeak = onSpeak)
        if (state.ttsReady && (state.usAvailable || state.ukAvailable)) {
            VoiceSelector(state = state, onVoiceChange = onVoiceChange)
        }
    }
    if (!state.ukAvailable && state.ttsReady) {
        Text(
            text = stringResource(R.string.voice_uk_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.extendedColors.warning,
        )
    }
    if (!state.ttsReady) {
        Text(
            text = stringResource(R.string.tts_missing_generic),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.extendedColors.warning,
        )
    }
}

@Composable
private fun VoiceSelector(state: ResultUiState, onVoiceChange: (Voice) -> Unit) {
    SingleChoiceSegmentedButtonRow {
        SegmentedButton(
            selected = state.voice == Voice.US,
            onClick = { onVoiceChange(Voice.US) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            enabled = state.usAvailable,
            label = { Text(stringResource(R.string.voice_us)) },
        )
        SegmentedButton(
            selected = state.voice == Voice.UK,
            onClick = { onVoiceChange(Voice.UK) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            enabled = state.ukAvailable,
            label = { Text(stringResource(R.string.voice_uk)) },
        )
    }
}

@Composable
private fun SpeakButton(state: ResultUiState, onSpeak: () -> Unit) {
    val descRes = if (state.voice == Voice.UK) {
        R.string.a11y_listen_uk
    } else {
        R.string.a11y_listen_us
    }
    IconButton(
        onClick = onSpeak,
        enabled = state.originalText.isNotBlank(),
    ) {
        Icon(
            painter = painterResource(
                if (state.isSpeaking) R.drawable.ic_stop else R.drawable.ic_volume_up,
            ),
            contentDescription = stringResource(
                if (state.isSpeaking) R.string.a11y_stop else descRes,
            ),
            tint = if (state.originalText.isNotBlank()) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun ResultBody(state: ResultUiState) {
    when (state.status) {
        ResultStatus.LOADING -> LoadingSkeleton()

        ResultStatus.SUCCESS -> TranslationBody(state)

        ResultStatus.CACHED -> Column {
            StatusChip(
                text = stringResource(R.string.result_chip_cached),
                container = MaterialTheme.extendedColors.successContainer,
                content = MaterialTheme.extendedColors.onSuccessContainer,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
            state.cachedAtLabel?.let { label ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.result_cached_at, label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            TranslationBody(state)
        }

        ResultStatus.OFFLINE_RESULT -> Column {
            StatusChip(
                text = stringResource(R.string.result_chip_offline),
                container = MaterialTheme.extendedColors.warningContainer,
                content = MaterialTheme.extendedColors.onWarningContainer,
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_cloud_off),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.result_offline_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            TranslationBody(state)
        }

        ResultStatus.TRUNCATED -> Column {
            StatusChip(
                text = stringResource(
                    R.string.result_chip_truncated,
                    formatThousands(state.truncatedPrefixLength ?: 2000),
                ),
                container = MaterialTheme.extendedColors.warningContainer,
                content = MaterialTheme.extendedColors.onWarningContainer,
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_content_cut),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
            Spacer(Modifier.height(12.dp))
            TranslationBody(state)
        }

        ResultStatus.DOWNLOADING_MODEL -> DownloadingModelBody()

        // F14 / UX-06 §9b: chip "Không có trong từ điển" (S10) CHỈ đúng khi đích = VI.
        // Đích khác VI: hiện bản dịch bình thường, không chip, không dòng "chưa có từ điển".
        ResultStatus.WORD_NOT_FOUND -> if (state.targetLang.equals("vi", ignoreCase = true)) {
            Column {
                StatusChip(
                    text = stringResource(R.string.result_chip_not_in_dict),
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    icon = null,
                )
                Spacer(Modifier.height(12.dp))
                TranslationBody(state)
            }
        } else {
            TranslationBody(state)
        }

        ResultStatus.ERROR_EMPTY -> ErrorBanner(
            title = stringResource(R.string.error_empty),
            body = null,
        )

        ResultStatus.ERROR_NO_NETWORK_SHORT -> WarningBanner(
            title = stringResource(R.string.error_need_model_short),
            body = stringResource(R.string.error_need_model_short_hint),
        )

        // E3c: cùng layout S5 nhưng máy đang có mạng -> không nhắc "Bật mạng".
        ResultStatus.ERROR_MODEL_MISSING -> WarningBanner(
            title = stringResource(R.string.error_need_model_short),
            body = stringResource(R.string.error_model_missing_short_hint),
        )

        // E31: cặp ngoài 5 ngôn ngữ — layout S7 + lối mở Cài đặt đổi ngôn ngữ đích.
        ResultStatus.ERROR_PAIR_UNSUPPORTED -> ErrorBanner(
            title = stringResource(R.string.error_pair_unsupported_title),
            body = stringResource(R.string.error_pair_unsupported_hint),
        )

        // E30: cùng layout S5, copy nói rõ cặp có thể cần 2 gói (zh->vi = zh + vi).
        ResultStatus.ERROR_LANG_MODEL_MISSING -> WarningBanner(
            title = stringResource(R.string.error_need_model_short),
            body = stringResource(R.string.error_lang_model_missing_hint),
        )

        ResultStatus.ERROR_NO_NETWORK_LONG -> WarningBanner(
            title = stringResource(R.string.error_need_network_long),
            body = stringResource(R.string.error_need_network_long_hint),
        )

        ResultStatus.ERROR_SOURCE -> ErrorBanner(
            title = stringResource(R.string.error_source),
            body = stringResource(R.string.error_source_hint),
        )

        // E28: nguồn dịch miễn phí chết nhưng máy vẫn có mạng — không dùng copy "Bật mạng".
        ResultStatus.ERROR_SOURCES_DOWN -> ErrorBanner(
            title = stringResource(R.string.error_sources_down_title),
            body = stringResource(R.string.error_sources_down_hint),
        )

        ResultStatus.ERROR_QUOTA -> WarningBanner(
            title = stringResource(R.string.error_quota_title),
            body = stringResource(R.string.error_quota_body),
        )

        ResultStatus.ERROR_KEY_INVALID -> ErrorBanner(
            title = stringResource(R.string.error_key_invalid_title),
            body = stringResource(R.string.error_key_invalid_body),
        )
    }
}

@Composable
private fun TranslationBody(state: ResultUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val fallbackTranslation = state.translation?.takeIf { it.isNotBlank() }
        if (!state.isWord) {
            // Câu/đoạn: bản dịch là nội dung chính.
            fallbackTranslation?.let { translated ->
                Text(
                    text = translated,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            return@Column
        }

        // WORD: nghĩa tiếng Việt (senses có cấu trúc, hoặc translation thô của ML Kit).
        if (!state.noVietnameseMeaning) {
            if (state.senses.isNotEmpty()) {
                state.senses.forEach { sense ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "${sense.index}.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Column {
                            // Designer: loại từ là nhãn tiếng Việt muted, tách khỏi câu nghĩa.
                            sense.partOfSpeech?.let { partOfSpeech ->
                                Text(
                                    text = "($partOfSpeech)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = sense.meaning,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            } else if (fallbackTranslation != null) {
                Text(
                    text = fallbackTranslation,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // BUG-09: khối định nghĩa EN phải render cho MỌI WORD (không phụ thuộc senses/status) —
        // trước đây nhánh fallback (senses rỗng) bỏ qua khối này.
        if (state.examples.isEmpty()) {
            // F14: WORD nguồn ZH/KO/JA không hiện khối "Nghĩa tiếng Anh".
            if (state.englishDefinitions.isNotEmpty() && state.showsEnglishDetails) {
                // Designer §4.1b: nhãn cách nghĩa tiếng Việt 12dp (Column đã spacedBy(8.dp)),
                // định nghĩa đánh số 1. 2. và tối đa 2 dòng.
                // E22: không có nghĩa tiếng Việt -> nhãn "Chưa có nghĩa tiếng Việt" thay "Nghĩa tiếng Anh".
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (state.noVietnameseMeaning) {
                                R.string.result_no_vi_meaning
                            } else {
                                R.string.result_english_meanings
                            },
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.englishDefinitions.take(2).forEachIndexed { index, definition ->
                        Text(
                            text = "${index + 1}. $definition",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            } else if (state.noVietnameseMeaning) {
                // E22 + không enrich được: không bịa nghĩa, báo rõ chưa tra được.
                Text(
                    text = stringResource(R.string.result_no_meaning),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.examples.forEach { example ->
            Column {
                Text(
                    text = "\"${example.text}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontStyle = FontStyle.Italic,
                )
                example.translation?.let { translated ->
                    Text(
                        text = "→ $translated",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (state.synonyms.isNotEmpty()) {
            Text(
                text = stringResource(
                    R.string.result_synonyms,
                    state.synonyms.joinToString(", "),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** S11 — đang tải gói dịch offline ngay trong sheet; không có API % nên indeterminate. */
@Composable
private fun DownloadingModelBody() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(
            text = stringResource(R.string.result_status_downloading),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.model_downloading_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SkeletonBar(widthFraction = 1f, height = 16.dp)
        SkeletonBar(widthFraction = 0.85f, height = 16.dp)
        SkeletonBar(widthFraction = 0.6f, height = 12.dp)
    }
}

@Composable
private fun SkeletonBar(widthFraction: Float, height: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.extraSmall),
    )
}

@Composable
private fun StatusChip(
    text: String,
    container: Color,
    content: Color,
    icon: (@Composable () -> Unit)? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            icon?.invoke()
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun WarningBanner(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.extendedColors.warningContainer,
        contentColor = MaterialTheme.extendedColors.onWarningContainer,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_wifi_off),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(text = body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ErrorBanner(title: String, body: String?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_error_outline),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                body?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionRow(
    state: ResultUiState,
    actions: ResultActions,
    loading: Boolean,
    onSpeak: () -> Unit,
) {
    val interactive = !loading && !state.editing
    val status = state.status

    if (status == ResultStatus.DOWNLOADING_MODEL) {
        // S11: chỉ còn nút X ở header; không có hành động nào khác.
        return
    }
    if (state.editing) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = actions.onSubmitEdit,
                enabled = state.editText.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_translate_now))
            }
            TextButton(onClick = actions.onCancelEdit) {
                Text(stringResource(R.string.action_close))
            }
        }
        return
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (status) {
            // UX-02 §5: hành động chính nằm trái nhất. S5: Tải gói offline · Thử lại · Copy.
            ResultStatus.ERROR_NO_NETWORK_SHORT,
            ResultStatus.ERROR_MODEL_MISSING,
            ResultStatus.ERROR_LANG_MODEL_MISSING,
            -> {
                FilledTonalButton(onClick = actions.onDownloadModel) {
                    Text(stringResource(R.string.action_download_model))
                }
                RetryButton(onRetry = actions.onRetry)
                CopyButton(onCopy = actions.onCopy, enabled = interactive)
            }

            ResultStatus.ERROR_NO_NETWORK_LONG,
            ResultStatus.ERROR_SOURCE,
            ResultStatus.ERROR_SOURCES_DOWN,
            -> {
                RetryButton(onRetry = actions.onRetry)
                CopyButton(onCopy = actions.onCopy, enabled = interactive)
            }

            // S12/E19 (Designer chốt): KHÔNG có Thử lại — bấm cũng không được gì tới 0h.
            ResultStatus.ERROR_QUOTA -> {
                FilledTonalButton(onClick = actions.onOpenApiKeySettings) {
                    Text(stringResource(R.string.action_enter_key))
                }
                CopyButton(onCopy = actions.onCopy, enabled = interactive)
            }

            // S13/E20: lỗi key -> mở Cài đặt sửa key, vẫn cho Thử lại.
            // E31 (F14): đổi ngôn ngữ đích trong Cài đặt rồi thử lại.
            ResultStatus.ERROR_PAIR_UNSUPPORTED -> {
                FilledTonalButton(onClick = actions.onOpenApiKeySettings) {
                    Text(stringResource(R.string.settings_open_settings))
                }
                RetryButton(onRetry = actions.onRetry)
                CopyButton(onCopy = actions.onCopy, enabled = interactive)
            }

            ResultStatus.ERROR_KEY_INVALID -> {
                FilledTonalButton(onClick = actions.onOpenApiKeySettings) {
                    Text(stringResource(R.string.settings_open_settings))
                }
                RetryButton(onRetry = actions.onRetry)
                CopyButton(onCopy = actions.onCopy, enabled = interactive)
            }

            ResultStatus.ERROR_EMPTY -> CopyButton(
                onCopy = actions.onCopy,
                enabled = interactive,
            )

            else -> {
                // User chốt 2026-11-04: bỏ tab Từ vựng → không còn nút "Lưu".
                CopyButton(onCopy = actions.onCopy, enabled = state.canCopy && interactive)
                // BUG-24 (F14): TTS chỉ có giọng EN -> nguồn khác EN ẩn nút Nghe im lặng.
                if (!state.isWord && state.ttsAvailableForSource) {
                    SpeakIconButton(state = state, onSpeak = onSpeak)
                }
                if (state.isOcr) {
                    TextButton(onClick = actions.onStartEdit) {
                        Text(stringResource(R.string.action_edit_text))
                    }
                }
            }
        }
    }
}

@Composable
private fun CopyButton(onCopy: () -> Unit, enabled: Boolean) {
    TextButton(onClick = onCopy, enabled = enabled) {
        Icon(
            painter = painterResource(R.drawable.ic_content_copy),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.action_copy))
    }
}

@Composable
private fun RetryButton(onRetry: () -> Unit) {
    TextButton(onClick = onRetry) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.action_retry))
    }
}

@Composable
private fun SpeakIconButton(state: ResultUiState, onSpeak: () -> Unit) {
    IconButton(onClick = onSpeak, enabled = state.originalText.isNotBlank()) {
        Icon(
            painter = painterResource(
                if (state.isSpeaking) R.drawable.ic_stop else R.drawable.ic_volume_up,
            ),
            contentDescription = stringResource(
                if (state.isSpeaking) R.string.a11y_stop else R.string.a11y_listen_us,
            ),
        )
    }
}

@Composable
private fun VoiceMissingDialog(
    ttsReady: Boolean,
    usAvailable: Boolean,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onUseUs: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_volume_up),
                contentDescription = null,
            )
        },
        title = {
            Text(
                stringResource(
                    if (ttsReady) R.string.tts_missing_uk_title else R.string.tts_missing_generic_title,
                ),
            )
        },
        text = {
            Text(
                stringResource(
                    if (ttsReady) R.string.tts_missing_uk_body else R.string.tts_missing_generic,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.tts_open_settings))
            }
        },
        dismissButton = {
            Row {
                if (usAvailable) {
                    TextButton(onClick = onUseUs) {
                        Text(stringResource(R.string.tts_use_us))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.tts_dialog_close))
                }
            }
        },
    )
}

private fun formatThousands(value: Int): String =
    NumberFormat.getIntegerInstance(Locale("vi", "VN")).format(value)