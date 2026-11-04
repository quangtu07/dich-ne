package vn.bubbledict.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 1.26/1.28: bảng mã riêng từng provider (CR: gom một chỗ, không rải if trong client). */
class ProviderLanguagesTest {

    @Test
    fun myMemory_acceptsIsoDirectly() {
        assertEquals("zh", ProviderLanguages.myMemory("zh"))
        assertEquals("ja", ProviderLanguages.myMemory("JA"))
        assertEquals("ko", ProviderLanguages.myMemory("ko"))
        assertEquals("vi", ProviderLanguages.myMemory("vi"))
    }

    @Test
    fun mozhi_mapsChineseToZhCn() {
        assertEquals("zh-CN", ProviderLanguages.mozhi("zh"))
        assertEquals("zh-CN", ProviderLanguages.mozhi("ZH"))
    }

    @Test
    fun mozhi_keepsOtherLanguages() {
        assertEquals("ja", ProviderLanguages.mozhi("ja"))
        assertEquals("ko", ProviderLanguages.mozhi("ko"))
        assertEquals("en", ProviderLanguages.mozhi("en"))
        assertEquals("vi", ProviderLanguages.mozhi("vi"))
    }
}
