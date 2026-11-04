package vn.bubbledict.ui.history

import androidx.compose.runtime.Immutable
import vn.bubbledict.domain.model.ResultState
import vn.bubbledict.domain.model.TextKind
import vn.bubbledict.domain.model.TextSource

/** Một dòng lịch sử; map từ `HistoryEntry` (BE-05) trong ViewModel, UI không thấy Room. */
@Immutable
data class HistoryItem(
    val id: Long,
    val rawText: String,
    val resultText: String?,
    val kind: TextKind,
    val source: TextSource,
    val state: ResultState,
    val errorCode: String?,
    val createdAt: Long,
) {
    val isError: Boolean get() = state == ResultState.ERROR
}

@Immutable
data class HistoryUiState(
    val items: List<HistoryItem> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
)