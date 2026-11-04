package vn.bubbledict.data.db

import androidx.room.TypeConverter
import vn.bubbledict.domain.model.Engine
import vn.bubbledict.domain.model.ResultState
import vn.bubbledict.domain.model.TextKind
import vn.bubbledict.domain.model.TextSource

/**
 * Lưu enum dạng tên. Đọc giá trị lạ không làm crash app cũ khi DB được ghi bởi bản mới hơn:
 * kind -> SENTENCE, source -> SELECTION, state -> ERROR, engine -> null.
 */
class Converters {
    @TypeConverter
    fun fromTextKind(value: TextKind?): String? = value?.name

    @TypeConverter
    fun toTextKind(value: String?): TextKind? = value?.let {
        runCatching { TextKind.valueOf(it) }.getOrDefault(TextKind.SENTENCE)
    }

    @TypeConverter
    fun fromTextSource(value: TextSource?): String? = value?.name

    @TypeConverter
    fun toTextSource(value: String?): TextSource? = value?.let {
        runCatching { TextSource.valueOf(it) }.getOrDefault(TextSource.SELECTION)
    }

    @TypeConverter
    fun fromState(value: ResultState?): String? = value?.name

    @TypeConverter
    fun toState(value: String?): ResultState? = value?.let {
        runCatching { ResultState.valueOf(it) }.getOrDefault(ResultState.ERROR)
    }

    @TypeConverter
    fun fromEngine(value: Engine?): String? = value?.name

    @TypeConverter
    fun toEngine(value: String?): Engine? = value?.let {
        runCatching { Engine.valueOf(it) }.getOrNull()
    }
}