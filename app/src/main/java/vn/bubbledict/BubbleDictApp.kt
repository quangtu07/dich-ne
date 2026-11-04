package vn.bubbledict

import android.app.Application
import kotlinx.coroutines.launch
import vn.bubbledict.di.AppContainer

/**
 * Application dựng [AppContainer] một lần cho cả app (UI đọc `container`).
 */
class BubbleDictApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // BE-17: dọn cache quá TTL + history quá hạn khi mở app, không chặn UI.
        container.appScope.launch { container.maintenance.runOnAppOpen() }
    }
}