package com.lovelyreader

import android.graphics.BitmapFactory
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.lovelyreader.ui.LovelyReaderApp
import com.lovelyreader.ui.theme.LovelyReaderTheme
import com.lovelyreader.ui.video.videoFullscreenBehavior
import com.lovelyreader.video.VideoCastController
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : ComponentActivity() {
    private val crashFile by lazy { java.io.File(filesDir, "last-startup-crash.txt") }
    private var splashScreen: androidx.core.splashscreen.SplashScreen? = null
    private var fullscreenView: View? = null
    private var fullscreenCallback: android.webkit.WebChromeClient.CustomViewCallback? = null

    fun enterVideoFullscreen(
        view: View,
        callback: android.webkit.WebChromeClient.CustomViewCallback? = null
    ) {
        if (fullscreenView != null) exitVideoFullscreen()
        val behavior = videoFullscreenBehavior()
        fullscreenView = view
        fullscreenCallback = callback
        requestedOrientation = if (behavior.preferLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setVideoSystemBars(hidden = behavior.hideSystemBars)
        addContentView(view, android.view.ViewGroup.LayoutParams(-1, -1))
    }

    fun enterVideoImmersive() {
        val behavior = videoFullscreenBehavior()
        requestedOrientation = if (behavior.preferLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setVideoSystemBars(hidden = behavior.hideSystemBars)
    }

    fun exitVideoImmersive() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setVideoSystemBars(hidden = false)
    }

    fun exitVideoFullscreen(notifyPage: Boolean = true) {
        fullscreenView?.let { view ->
            (view.parent as? android.view.ViewGroup)?.removeView(view)
        }
        fullscreenView = null
        val callback = fullscreenCallback
        fullscreenCallback = null
        exitVideoImmersive()
        if (notifyPage) callback?.onCustomViewHidden()
    }

    @Suppress("DEPRECATION")
    private fun setVideoSystemBars(hidden: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (hidden) window.insetsController?.hide(WindowInsets.Type.systemBars())
            else window.insetsController?.show(WindowInsets.Type.systemBars())
        } else {
            window.decorView.systemUiVisibility = if (hidden) {
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            } else {
                View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    override fun onBackPressed() {
        if (fullscreenView != null) exitVideoFullscreen() else super.onBackPressed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        installCrashRecorder()
        VideoCastController.initialize(applicationContext)
        if (crashFile.exists()) {
            showStartupCrashScreen(crashFile.readText())
            return
        }

        // Do not gate Android 12's system splash on Compose state. On some
        // emulator/Android 12 combinations that gate can leave the activity
        // showing a permanent blank system-splash surface. RandomSplash below
        // owns the app-level transition instead.
        splashScreen?.setKeepOnScreenCondition { false }

        val splashBitmap = decodeSplashBitmap()

        runCatching {
            setContent {
                LovelyReaderTheme {
                    LovelyReaderApp(
                        splashBitmap = splashBitmap,
                        onSplashReady = {}
                    )
                }
            }
        }.onFailure { error ->
            writeCrash(error)
            showStartupCrashScreen(error.stackTraceToString())
        }
    }

    private fun decodeSplashBitmap(): android.graphics.Bitmap? {
        // Pick a random image from the splash assets each launch so the startup
        // screen feels fresh. The compressed assets are already normalized for
        // orientation and size, so decoding here is fast.
        return runCatching {
            val files = assets.list("splash")?.filter {
                it.endsWith(".png", ignoreCase = true) ||
                    it.endsWith(".jpg", ignoreCase = true) ||
                    it.endsWith(".jpeg", ignoreCase = true)
            } ?: emptyList()
            val path = files.randomOrNull()?.let { "splash/$it" } ?: return@runCatching null
            assets.open(path).use { stream -> BitmapFactory.decodeStream(stream) }
        }.getOrNull()
            ?: runCatching {
                BitmapFactory.decodeResource(resources, R.drawable.splash_window_bg)
            }.getOrNull()
    }

    private fun installCrashRecorder() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeCrash(throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrash(throwable: Throwable) {
        runCatching {
            val writer = StringWriter()
            throwable.printStackTrace(PrintWriter(writer))
            crashFile.writeText(writer.toString())
        }
    }

    private fun showStartupCrashScreen(trace: String) {
        // 立即移除启动图，避免背景干扰错误文字。
        splashScreen?.setKeepOnScreenCondition { false }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 36, 36, 36)
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        val title = TextView(this).apply {
            text = "启动失败记录"
            textSize = 22f
            setTextColor(android.graphics.Color.WHITE)
        }
        val message = TextView(this).apply {
            text = "上次启动时发生崩溃。请拍下这页给我，我会按堆栈修。点下面按钮可以清除记录并重试启动。"
            textSize = 16f
            setTextColor(android.graphics.Color.LTGRAY)
        }
        val retry = Button(this).apply {
            text = "清除记录并重试"
            setOnClickListener {
                crashFile.delete()
                recreate()
            }
        }
        val traceView = TextView(this).apply {
            text = trace
            textSize = 12f
            setTextColor(android.graphics.Color.GREEN)
            setPadding(0, 24, 0, 0)
        }
        content.addView(title)
        content.addView(message)
        content.addView(retry)
        content.addView(traceView)
        setContentView(ScrollView(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            addView(content)
        })
    }
}
