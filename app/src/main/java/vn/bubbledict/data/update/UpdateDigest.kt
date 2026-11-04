package vn.bubbledict.data.update

import java.security.MessageDigest

/** F12: băm SHA-256 để xác thực file APK tải về trước khi mở trình cài. */
object UpdateDigest {

    fun sha256Hex(bytes: ByteArray): String = toHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    /**
     * Băm theo luồng để không nạp cả APK (~32MB) vào RAM.
     * @return hex thường (lowercase) 64 ký tự.
     */
    fun sha256Hex(read: (ByteArray) -> Int, onChunk: ((ByteArray, Int) -> Unit)? = null): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val n = read(buffer)
            if (n <= 0) break
            digest.update(buffer, 0, n)
            onChunk?.invoke(buffer, n)
        }
        return toHex(digest.digest())
    }

    /** So sánh không phân biệt hoa/thường; sai độ dài hoặc khác nội dung đều false. */
    fun matches(expectedHex: String, actualHex: String): Boolean =
        expectedHex.trim().equals(actualHex.trim(), ignoreCase = true)

    private fun toHex(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) out.append("%02x".format(b))
        return out.toString()
    }
}