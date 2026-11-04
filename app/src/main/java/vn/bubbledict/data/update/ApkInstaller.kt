package vn.bubbledict.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * F12 — mở trình cài APK đã tải (side-load), kèm kiểm quyền cài từ nguồn không xác định.
 *
 * Android 8+ bắt buộc dùng content:// URI, nên APK phải nằm trong thư mục được khai báo ở
 * `res/xml/file_paths.xml` và chia sẻ qua [FileProvider].
 */
class ApkInstaller(private val context: Context) {

    private val authority: String get() = "${context.packageName}.fileprovider"

    /** User đã bật "Cài từ nguồn không xác định" cho app này chưa (API 26+). */
    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Mở màn cài đặt hệ thống để user cấp quyền nguồn không xác định cho app. */
    fun unknownSourcesIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Intent mở trình cài cho file APK (null nếu file không tồn tại). */
    fun installIntent(apkFile: File): Intent? {
        if (!apkFile.exists() || apkFile.length() <= 0) return null
        val uri = FileProvider.getUriForFile(context, authority, apkFile)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
    }
}