package vn.bubbledict.ui.vocab

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vn.bubbledict.R
import vn.bubbledict.ui.result.ResultActivityContract
import vn.bubbledict.ui.theme.extendedColors

/**
 * FE-07 — tab Từ vựng (UX-04, spec F7/AC12). Từ đã lưu tách khỏi lịch sử:
 * xoá lịch sử không mất danh sách này. Bỏ lưu có snackbar Hoàn tác.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabScreen(
    viewModel: VocabViewModel,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchActive by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }

    LaunchedEffect(query) {
        delay(250)
        viewModel.load(query)
    }

    val onRemove: (VocabItem) -> Unit = { item ->
        viewModel.remove(item)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.unsave_done),
                actionLabel = context.getString(R.string.undo),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoRemove()
        }
    }

    val onCopy: (VocabItem) -> Unit = { item ->
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText(item.text, "${item.text} — ${item.meaning}"))
        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.copy_done_word)) }
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
                        placeholder = { Text(stringResource(R.string.vocab_search_hint)) },
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
                title = {
                    Text(
                        text = if (state.count > 0) {
                            stringResource(R.string.vocab_count, state.count)
                        } else {
                            stringResource(R.string.tab_vocab)
                        },
                    )
                },
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
                                text = { Text(stringResource(R.string.menu_sort_new)) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.setSort(newestFirst = true)
                                },
                                trailingIcon = {
                                    if (state.newestFirst) {
                                        Icon(Icons.Filled.Check, contentDescription = null)
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_sort_az)) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.setSort(newestFirst = false)
                                },
                                trailingIcon = {
                                    if (!state.newestFirst) {
                                        Icon(Icons.Filled.Check, contentDescription = null)
                                    }
                                },
                            )
                        }
                    }
                },
            )
        }

        when {
            state.isLoading -> VocabLoading()
            state.isEmpty -> VocabEmptyState()
            else -> VocabList(
                items = state.items,
                onClick = { item ->
                    context.startActivity(
                        ResultActivityContract.newSavedIntent(
                            context = context,
                            text = item.text,
                            meaning = item.meaning,
                        ),
                    )
                },
                onRemove = onRemove,
                onCopy = onCopy,
            )
        }
    }
}

@Composable
private fun VocabList(
    items: List<VocabItem>,
    onClick: (VocabItem) -> Unit,
    onRemove: (VocabItem) -> Unit,
    onCopy: (VocabItem) -> Unit,
) {
    val formatter = remember { DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale("vi", "VN")) }
    val zone = ZoneId.systemDefault()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.id }) { item ->
            VocabRow(
                item = item,
                dateLabel = Instant.ofEpochMilli(item.savedAt).atZone(zone).format(formatter),
                onClick = { onClick(item) },
                onRemove = { onRemove(item) },
                onCopy = { onCopy(item) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun VocabRow(
    item: VocabItem,
    dateLabel: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.meaning,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = stringResource(R.string.a11y_saved_word),
                tint = MaterialTheme.extendedColors.success,
            )
        }
        Box {
            var menuOpen by remember { mutableStateOf(false) }
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.action_more),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_copy)) },
                    onClick = {
                        menuOpen = false
                        onCopy()
                    },
                )
            }
        }
    }
}

@Composable
private fun VocabLoading() {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.vocab_loading),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VocabEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_star_border),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = stringResource(R.string.vocab_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = stringResource(R.string.vocab_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}