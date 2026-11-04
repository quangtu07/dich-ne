package vn.bubbledict.ui.vocab

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import vn.bubbledict.data.VocabRepository
import vn.bubbledict.data.db.AppDatabase
import vn.bubbledict.data.db.entity.VocabEntry

/**
 * FE-07 — Từ vựng đã lưu (UX-04, spec F7/AC12).
 * Đọc/ghi qua `VocabRepository` (BE-05). Bỏ lưu có Hoàn tác = chèn lại entity cũ.
 */
class VocabViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as vn.bubbledict.BubbleDictApp).container.vocabRepository

    private val _state = MutableStateFlow(VocabUiState())
    val state: StateFlow<VocabUiState> = _state.asStateFlow()

    private var lastQuery: String = ""
    private var newestFirst: Boolean = true
    private var pendingUndo: VocabEntry? = null

    fun load(query: String = lastQuery, newestFirst: Boolean = this.newestFirst) {
        lastQuery = query
        this.newestFirst = newestFirst
        viewModelScope.launch {
            val all = repository.all()
            val filtered = if (query.isBlank()) {
                all
            } else {
                all.filter { it.text.contains(query, ignoreCase = true) || it.meaning.contains(query, ignoreCase = true) }
            }
            val sorted = if (newestFirst) {
                filtered.sortedWith(compareByDescending<VocabEntry> { it.savedAt }.thenByDescending { it.id })
            } else {
                filtered.sortedBy { it.text.lowercase() }
            }
            _state.value = VocabUiState(
                items = sorted.map(VocabEntry::toItem),
                count = all.size,
                query = query,
                newestFirst = newestFirst,
                isLoading = false,
                isEmpty = sorted.isEmpty(),
            )
        }
    }

    fun setSort(newestFirst: Boolean) = load(newestFirst = newestFirst)

    fun remove(item: VocabItem) {
        viewModelScope.launch {
            val entry = repository.find(item.text) ?: return@launch
            repository.remove(item.text)
            pendingUndo = entry
            load()
        }
    }

    fun undoRemove() {
        val entry = pendingUndo ?: return
        pendingUndo = null
        viewModelScope.launch {
            repository.save(entry)
            load()
        }
    }
}

private fun VocabEntry.toItem(): VocabItem = VocabItem(
    id = id,
    text = text,
    meaning = meaning,
    ipa = ipa,
    savedAt = savedAt,
)