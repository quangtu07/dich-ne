package vn.bubbledict.ui.vocab

import androidx.compose.runtime.Immutable

/** Một từ đã lưu; map từ `VocabEntry` (BE-05) trong ViewModel. */
@Immutable
data class VocabItem(
    val id: Long,
    val text: String,
    val meaning: String,
    val ipa: String?,
    val savedAt: Long,
)

@Immutable
data class VocabUiState(
    val items: List<VocabItem> = emptyList(),
    val count: Int = 0,
    val query: String = "",
    val newestFirst: Boolean = true,
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
)