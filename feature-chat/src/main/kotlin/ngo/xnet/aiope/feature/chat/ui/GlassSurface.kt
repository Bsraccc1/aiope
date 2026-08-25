package ngo.xnet.aiope.feature.chat.ui

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * Frosted-glass surfaces for the chat shell (top bar, composer, drawer, floating sheets).
 *
 * Compose has no backdrop-blur primitive: `Modifier.blur` blurs a composable's own content, not
 * what sits behind it. On Android 12+ the platform can blur behind a *window*, which is what
 * [glassBlurBehind] uses for dialogs; for in-layout chrome the effect has to be faked, and what
 * actually reads as glass is a low-opacity tint plus a top-lit sheen and a bright hairline edge.
 *
 * The tint used to sit at 0.72 alpha, which is closer to "slightly see-through card" than glass —
 * the background was barely present. It now defaults to [GLASS_TINT] with the sheen and border
 * carrying the shape definition instead of the fill. Two consequences worth knowing:
 *
 * - Legibility comes from the border and sheen, not opacity, so don't drop the border.
 * - Text placed *directly* on a glass pane over a photo background is the contrast trap the UI
 *   guidelines warn about. Chrome (icons, short labels) is fine; message bubbles are not.
 */
@Composable
fun GlassSurface(
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(CuORadius.xl),
  tintAlpha: Float = GLASS_TINT,
  borderAlpha: Float = 0.5f,
  content: @Composable BoxScope.() -> Unit,
) {
  val cs = MaterialTheme.colorScheme
  val isLight = cs.background.luminance() > 0.5f
  // Sheen: a light top edge fading out, which is what sells "pane of glass". At low fill opacity it
  // does more work than the tint, so it is stronger here than the fill alpha would suggest.
  val sheen = if (isLight) Color.White else Color.White
  Box(
    modifier
      .clip(shape)
      .background(cs.surfaceContainer.copy(alpha = tintAlpha))
      .background(
        Brush.verticalGradient(
          0f to sheen.copy(alpha = if (isLight) 0.42f else 0.10f),
          0.5f to Color.Transparent,
          1f to if (isLight) Color.Transparent else Color.Black.copy(alpha = 0.05f),
        ),
      )
      .glassBorder(shape, cs.outlineVariant.copy(alpha = borderAlpha)),
    content = content,
  )
}

/**
 * Default fill opacity. Low enough that the theme background reads through the pane, high enough
 * that a 20sp icon on top still clears contrast on a busy wallpaper.
 */
const val GLASS_TINT: Float = 0.44f

/** Slightly more opaque variant for panes that carry body text (menus, autocomplete sheets). */
const val GLASS_TINT_READABLE: Float = 0.62f

/**
 * True when the platform can blur behind a window, so dialog-hosted glass can be real rather than
 * simulated. Android 12 (S) added `blurBehindRadius`; below that the flag is ignored.
 */
val glassBlurBehindSupported: Boolean
  get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Hairline border drawn on top of the fill, so the gradient doesn't wash it out. */
private fun Modifier.glassBorder(shape: Shape, color: Color): Modifier = this.border(BorderStroke(0.8.dp, color), shape)
