package vn.bubbledict.ui.vocab

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
import vn.bubbledict.data.VocabRepository
import vn.bubbledict.data.db.AppDatabase
import vn.bubbledict.data.db.entity.VocabEntry
import vn.bubbledict.ui.theme.BubbleDictTheme

/**
 * Màn dev: seed vài từ đã lưu (nếu DB rỗng) rồi render [VocabScreen] để QA chụp ảnh.
 *
 *   adb shell am start -n vn.bubbledict/.ui.vocab.VocabDevActivity
 */
class VocabDevActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = VocabRepository(AppDatabase.get(this))
        runBlocking {
            if (repository.count() == 0) seed(repository)
        }

        setContent {
            BubbleDictTheme {
                val snackbar = remember { SnackbarHostState() }
                Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
                    VocabScreen(
                        viewModel = viewModel(),
                        snackbarHostState = snackbar,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }

    private suspend fun seed(repository: VocabRepository) {
        val now = System.currentTimeMillis()
        repository.save(
            VocabEntry(
                text = "ubiquitous",
                meaning = "có mặt ở khắp nơi",
                ipa = "/juːˈbɪk.wɪ.təs/",
                savedAt = now - 2 * 60 * 60 * 1000,
            ),
        )
        repository.save(
            VocabEntry(
                text = "comprehension",
                meaning = "sự hiểu, sự lĩnh hội",
                ipa = "/ˌkɒmprɪˈhenʃn/",
                savedAt = now - 26 * 60 * 60 * 1000,
            ),
        )
        repository.save(
            VocabEntry(
                text = "meticulous",
                meaning = "tỉ mỉ, kỹ lưỡng",
                ipa = "/məˈtɪkjələs/",
                savedAt = now - 3 * 24 * 60 * 60 * 1000,
            ),
        )
    }
}