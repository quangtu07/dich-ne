package vn.bubbledict.ui.result

import android.content.Context
import android.content.Intent
import vn.bubbledict.domain.model.TextSource

/**
 * Hợp đồng intent của [ResultActivity] (BE-11).
 *
 * F1: app khác gửi `ACTION_PROCESS_TEXT` + `EXTRA_PROCESS_TEXT` (không cần extra riêng).
 * F2/F3: OCR/TTS trong app mở thẳng bằng [newIntent] kèm [EXTRA_TEXT].
 */
object ResultActivityContract {

    /** Text do chính app đưa vào (OCR); khác `Intent.EXTRA_PROCESS_TEXT` của app ngoài. */
    const val EXTRA_TEXT = "vn.bubbledict.extra.TEXT"

    /** "SELECTION" hoặc "OCR" — mặc định suy ra từ nguồn intent. */
    const val EXTRA_SOURCE = "vn.bubbledict.extra.SOURCE"

    /** Mở lại một dòng lịch sử đã lưu (không gọi mạng lại) — FE-06. */
    const val EXTRA_HISTORY_ID = "vn.bubbledict.extra.HISTORY_ID"

    /** Kết quả đã có sẵn (chế độ xem từ Từ vựng) — FE-07. */
    const val EXTRA_RESULT_TEXT = "vn.bubbledict.extra.RESULT_TEXT"

    fun newIntent(
        context: Context,
        text: String,
        source: TextSource = TextSource.OCR,
    ): Intent = Intent(context, ResultActivity::class.java)
        .putExtra(EXTRA_TEXT, text)
        .putExtra(EXTRA_SOURCE, source.name)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Mở lại kết quả đã lưu trong lịch sử, không tra lại mạng. */
    fun newHistoryIntent(context: Context, historyId: Long): Intent =
        Intent(context, ResultActivity::class.java)
            .putExtra(EXTRA_HISTORY_ID, historyId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Xem từ đã lưu trong Từ vựng (chế độ "Đã lưu"). */
    fun newSavedIntent(context: Context, text: String, meaning: String): Intent =
        Intent(context, ResultActivity::class.java)
            .putExtra(EXTRA_TEXT, text)
            .putExtra(EXTRA_RESULT_TEXT, meaning)
            .putExtra(EXTRA_SOURCE, TextSource.SELECTION.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}