package dev.yxuan.commit.ui.components

import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.function.Consumer

/**
 * Whether the system can blur what's behind a window right now (Android 12+ cross-window blur).
 * Turns false when the device lacks it, in battery saver, or when blurs are disabled in settings;
 * callers then fall back to solid panels.
 */
@Composable
fun rememberCrossWindowBlur(): Boolean {
    // Emulators advertise blur but render it on a software GPU: a blurred window froze the
    // emulator's display (System UI not responding). Real devices that support it keep it.
    if (isEmulator) return false
    val context = LocalContext.current
    val wm = remember(context) { context.getSystemService(WindowManager::class.java) }
    var enabled by remember { mutableStateOf(wm?.isCrossWindowBlurEnabled == true) }
    DisposableEffect(wm) {
        val listener = Consumer<Boolean> { enabled = it }
        wm?.addCrossWindowBlurEnabledListener(listener)
        onDispose { wm?.removeCrossWindowBlurEnabledListener(listener) }
    }
    return enabled
}

/** Blurs everything behind [this] window by [radiusPx], or turns the blur off. */
fun Window.setBlurBehind(enabled: Boolean, radiusPx: Int) {
    if (enabled) {
        addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        attributes = attributes.apply { blurBehindRadius = radiusPx }
    } else {
        clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    }
}

/** Android emulator (goldfish/ranchu), where cross-window blur is unusably slow. */
private val isEmulator: Boolean =
    Build.HARDWARE in setOf("ranchu", "goldfish") || Build.PRODUCT.startsWith("sdk_gphone") || Build.FINGERPRINT.contains("emulator")
