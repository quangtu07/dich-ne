package vn.bubbledict.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import vn.bubbledict.data.HistoryRepository
import vn.bubbledict.data.db.AppDatabase
import vn.bubbledict.data.db.entity.HistoryEntry

/**
 * FE-06 — Lịch sử tra cứu. Đọc/ghi qua `HistoryRepository` (BE-05).
 *
 * Hoàn tác xoá: giữ entity vừa xoá, "Hoàn tác" gọi `undoDelete` chèn lại đúng `id` cũ
 * (BE-05 đã kiểm chứng id AUTOINCREMENT không bị cấp lại nên không trùng PK).
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as vn.bubbledict.BubbleDictApp).container.historyRepository

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    private var lastQuery: String = ""
    private var pendingUndo: HistoryEntry? = null

    fun load(query: String = lastQuery) {
        lastQuery = query
        viewModelScope.launch {
            val entries = if (query.isBlank()) {
                repository.recent()
            } else {
                repository.search(query)
            }
            _state.value = HistoryUiState(
                items = entries.map(HistoryEntry::toItem),
                query = query,
                isLoading = false,
                isEmpty = entries.isEmpty(),
            )
        }
    }

    /** Xoá 1 dòng; giữ entity để [undoLast] chèn lại đúng id. */
    fun delete(item: HistoryItem) {
        viewModelScope.launch {
            val entry = repository.findById(item.id) ?: return@launch
            repository.delete(item.id)
            pendingUndo = entry
            load()
        }
    }

    fun undoLast() {
        val entry = pendingUndo ?: return
        pendingUndo = null
        viewModelScope.launch {
            repository.undoDelete(entry)
            load()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clear()
            pendingUndo = null
            load()
        }
    }
}

private fun HistoryEntry.toItem(): HistoryItem = HistoryItem(
    id = id,
    rawText = rawText,
    resultText = resultText,
    kind = kind,
    source = source,
    state = state,
    errorCode = errorCode,
    createdAt = createdAt,
)