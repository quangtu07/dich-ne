package vn.bubbledict.ui.history

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.runBlocking
import vn.bubbledict.data.HistoryRepository
import vn.bubbledict.data.db.AppDatabase
import vn.bubbledict.data.db.entity.HistoryEntry
import vn.bubbledict.domain.model.Engine
import vn.bubbledict.domain.model.ResultState
import vn.bubbledict.domain.model.TextKind
import vn.bubbledict.domain.model.TextSource
import vn.bubbledict.ui.theme.BubbleDictTheme

/**
 * Màn dev: seed vài mục lịch sử (nếu DB rỗng) rồi render [HistoryScreen] để QA chụp ảnh.
 * Không phải UI sản phẩm; mở bằng:
 *
 *   adb shell am start -n vn.bubbledict/.ui.history.HistoryDevActivity
 */
class HistoryDevActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = HistoryRepository(AppDatabase.get(this))
        runBlocking {
            if (repository.count() == 0) seed(repository)
        }

        setContent {
            BubbleDictTheme {
                val snackbar = remember { SnackbarHostState() }
                Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
                    HistoryScreen(
                        viewModel = viewModel(),
                        snackbarHostState = snackbar,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }

    private suspend fun seed(repository: HistoryRepository) {
        val now = System.currentTimeMillis()
        val day = 24L * 60L * 60L * 1000L
        repository.record(
            HistoryEntry(
                rawText = "ubiquitous",
                normText = "ubiquitous",
                kind = TextKind.WORD,
                source = TextSource.SELECTION,
                resultText = "có mặt ở khắp nơi",
                engine = Engine.KEY,
                state = ResultState.OK,
                createdAt = now - 2 * 60 * 1000,
            ),
        )
        repository.record(
            HistoryEntry(
                rawText = "Reading English books is a great way to learn.",
                normText = "Reading English books is a great way to learn.",
                kind = TextKind.SENTENCE,
                source = TextSource.SELECTION,
                resultText = "Đọc sách tiếng Anh là một cách tuyệt vời để học.",
                engine = Engine.KEY,
                state = ResultState.OK,
                createdAt = now - 26 * 60 * 1000,
            ),
        )
        repository.record(
            HistoryEntry(
                rawText = "Reading English books is a great way to learn. It builds vocabulary naturally. You also get used to real sentence structures.",
                normText = "Reading English books is a great way to learn. It builds vocabulary naturally. You also get used to real sentence structures.",
                kind = TextKind.PARAGRAPH,
                source = TextSource.SELECTION,
                resultText = "Đọc sách tiếng Anh là một cách tuyệt vời để học. Việc này xây dựng vốn từ tự nhiên. Bạn cũng quen với cấu trúc câu thật.",
                engine = Engine.CACHE,
                state = ResultState.CACHED,
                createdAt = now - day - 60 * 60 * 1000,
            ),
        )
        repository.record(
            HistoryEntry(
                rawText = "The committee deferred the decision until next quarter.",
                normText = "The committee deferred the decision until next quarter.",
                kind = TextKind.SENTENCE,
                source = TextSource.SELECTION,
                resultText = null,
                engine = null,
                state = ResultState.ERROR,
                errorCode = "E4",
                createdAt = now - day - 3 * 60 * 60 * 1000,
            ),
        )
        repository.record(
            HistoryEntry(
                rawText = "comprehension",
                normText = "comprehension",
                kind = TextKind.WORD,
                source = TextSource.OCR,
                resultText = "sự hiểu, sự lĩnh hội",
                engine = Engine.MLKIT,
                state = ResultState.OK,
                createdAt = now - day - day - 30 * 60 * 1000,
            ),
        )
    }
}