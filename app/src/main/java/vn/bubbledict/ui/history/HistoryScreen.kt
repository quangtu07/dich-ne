package vn.bubbledict.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vn.bubbledict.R
import vn.bubbledict.domain.model.TextKind
import vn.bubbledict.domain.model.TextSource
import vn.bubbledict.ui.result.ResultActivityContract

/**
 * FE-06 — tab Lịch sử (UX-04, spec F6/AC11–AC13).
 *
 * Nhóm theo ngày, header sticky (Hôm nay / Hôm qua / dd-MM), tìm kiếm debounce,
 * vuốt trái để xoá + snackbar "Hoàn tác" (chèn lại đúng id qua [HistoryViewModel.undoLast]),
 * overflow "Xoá toàn bộ" có dialog xác nhận. Chạm item sẽ mở lại kết quả khi FE-03 xong.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchActive by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var showDeleteAll by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }

    LaunchedEffect(query) {
        delay(250)
        viewModel.load(query)
    }

    val onDelete: (HistoryItem) -> Unit = { item ->
        viewModel.delete(item)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.history_deleted_item),
                actionLabel = context.getString(R.string.undo),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoLast()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (searchActive) {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(searchFocus),
                        placeholder = { Text(stringResource(R.string.history_search_hint)) },
                        singleLine = true,
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            searchActive = false
                            query = ""
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_close_search),
                        )
                    }
                },
            )
            LaunchedEffect(Unit) { runCatching { searchFocus.requestFocus() } }
        } else {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_history)) },
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
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.menu_delete_all),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    showDeleteAll = true
                                },
                            )
                        }
                    }
                },
            )
        }

        when {
            state.isLoading && state.items.isEmpty() -> LoadingRows()

            state.isEmpty -> HistoryEmptyState()

            else -> HistoryList(
                groups = groupByDay(state.items),
                onDelete = onDelete,
                onOpen = { item ->
                    context.startActivity(ResultActivityContract.newHistoryIntent(context, item.id))
                },
            )
        }
    }

    if (showDeleteAll) {
        AlertDialog(
            onDismissRequest = { showDeleteAll = false },
            title = { Text(stringResource(R.string.delete_history_title)) },
            text = { Text(stringResource(R.string.delete_history_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAll = false
                        viewModel.clearAll()
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                context.getString(R.string.deleted_history_toast),
                            )
                        }
                    },
                ) {
                    Text(
                        text = stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAll = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

private data class DayGroup(val label: String, val items: List<HistoryItem>)

@Composable
private fun groupByDay(items: List<HistoryItem>): List<DayGroup> {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    val zone = ZoneId.systemDefault()
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale("vi", "VN")) }
    val dayFormatter = remember { DateTimeFormatter.ofPattern("dd-MM", Locale("vi", "VN")) }
    val todayLabel = stringResource(R.string.history_today)
    val yesterdayLabel = stringResource(R.string.history_yesterday)

    fun labelOf(createdAt: Long): String {
        val date = Instant.ofEpochMilli(createdAt).atZone(zone).toLocalDate()
        return when (date) {
            today -> todayLabel
            yesterday -> yesterdayLabel
            else -> date.format(dayFormatter)
        }
    }

    return remember(items, todayLabel, yesterdayLabel) {
        items.groupBy { labelOf(it.createdAt) }
            .map { (label, groupItems) -> DayGroup(label, groupItems) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryList(
    groups: List<DayGroup>,
    onDelete: (HistoryItem) -> Unit,
    onOpen: (HistoryItem) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale("vi", "VN")) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        groups.forEach { group ->
            stickyHeader(key = "header-${group.label}") {
                Text(
                    text = group.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(group.items, key = { it.id }) { item ->
                SwipeableHistoryRow(
                    item = item,
                    timeLabel = Instant.ofEpochMilli(item.createdAt)
                        .atZone(zone)
                        .format(timeFormatter),
                    onDelete = onDelete,
                    onOpen = onOpen,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableHistoryRow(
    item: HistoryItem,
    timeLabel: String,
    onDelete: (HistoryItem) -> Unit,
    onOpen: (HistoryItem) -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete(item)
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        HistoryRow(item = item, timeLabel = timeLabel, onClick = { onOpen(item) })
    }
}

@Composable
private fun HistoryRow(item: HistoryItem, timeLabel: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(typeIcon(item)),
            contentDescription = null,
            modifier = Modifier
                .size(20.dp)
                .padding(top = 2.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.rawText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (item.isError) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.history_error_chip),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(
                text = if (item.isError) {
                    stringResource(R.string.error_source)
                } else {
                    item.resultText.orEmpty()
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = timeLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun typeIcon(item: HistoryItem): Int = when {
    item.source == TextSource.OCR -> R.drawable.ic_document_scanner
    item.kind == TextKind.WORD -> R.drawable.ic_text_fields
    else -> R.drawable.ic_notes
}

@Composable
private fun LoadingRows() {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(
            text = stringResource(R.string.history_loading),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        repeat(4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp)),
            )
        }
    }
}

@Composable
private fun HistoryEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_history),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = stringResource(R.string.history_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = stringResource(R.string.history_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}