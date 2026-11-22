package dev.yxuan.commit.ui.components

import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import android.view.WindowManager
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import dev.yxuan.commit.ui.theme.Commit

/** Dialog panel: `surface`, the theme's panel radius, 20 dp sides, 24 dp top, 20 dp between groups. */
@Composable
fun CommitDialog(
    title: String,
    onDismiss: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Always dim what's behind (also over the quick-add popup, whose window turns dimming off),
        // and frost it when the system can blur; then the panel is glass too.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val blur = rememberCrossWindowBlur()
        val radius = with(LocalDensity.current) { 24.dp.roundToPx() }
        SideEffect {
            window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window?.setDimAmount(if (blur) 0.2f else 0.45f)
            window?.setBlurBehind(blur, radius)
        }
        DialogPanel(title = title, actions = actions, modifier = Modifier.padding(horizontal = 16.dp), glass = if (blur) Glass.Frosted else Glass.Clear, content = content)
    }
}

/** How see-through a panel is. */
enum class Glass {
    /** Solid `surface`. */
    None,
    /** No blur available: only a hint of what is behind (90 %), so text behind never competes with the panel. */
    Clear,
    /** Over a blurred backdrop: more see-through, like frosted glass. */
    Frosted,
}

/**
 * The panel itself, also used by the quick-add activity. [glass]: the surface turns see-through and
 * gets a thin light edge, like glass; more so when the window blurs what's behind it.
 */
@Composable
fun DialogPanel(
    title: String,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    glass: Glass = Glass.None,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Commit.colors
    val shape = RoundedCornerShape(Commit.shapes.panel)
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (glass == Glass.None) Modifier.clip(shape).background(c.surface)
                else Modifier
                    .clip(shape)
                    .background(c.surface.copy(alpha = if (glass == Glass.Frosted) 0.55f else 0.9f))
                    .border(1.dp, Color.White.copy(alpha = if (c.isDark) 0.16f else 0.65f), shape)
            )
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(title, style = Commit.type.titleDialog, color = c.ink, modifier = Modifier.semantics { heading() })
        content()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            actions()
        }
    }
}
