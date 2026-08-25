package ngo.xnet.aiope.feature.chat.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Context-window indicator: a ring that fills as the conversation consumes the model's window,
 * plus the remaining percentage.
 *
 * Why a ring and not a number alone: the number only matters near the limit, and a bare
 * "62,000 / 128,000" invites mental arithmetic mid-conversation. The ring is glanceable, and the
 * colour crosses to the theme's error tone at the same 95% point where auto-compact kicks in, so
 * the warning and the actual behaviour agree.
 */
@Composable
fun ContextIndicator(used: Int, limit: Int, modifier: Modifier = Modifier) {
  if (limit <= 0) return
  val fraction = (used.toFloat() / limit).coerceIn(0f, 1f)
  val animated by animateFloatAsState(targetValue = fraction, label = "ctx")
  val cs = MaterialTheme.colorScheme
  // 0.95 mirrors maybeAutoCompact's threshold; 0.75 is the "start paying attention" step.
  val tint = when {
    fraction >= 0.95f -> cs.error
    fraction >= 0.75f -> cs.tertiary
    else -> cs.primary
  }

  Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(16.dp)) {
      Canvas(Modifier.size(16.dp)) {
        val stroke = 2.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(
          color = tint.copy(alpha = 0.22f),
          startAngle = 0f,
          sweepAngle = 360f,
          useCenter = false,
          topLeft = Offset(inset, inset),
          size = arcSize,
          style = Stroke(width = stroke),
        )
        drawArc(
          color = tint,
          // -90 so the fill starts at the top, which reads as "filling up" rather than rotating.
          startAngle = -90f,
          sweepAngle = 360f * animated,
          useCenter = false,
          topLeft = Offset(inset, inset),
          size = arcSize,
          style = Stroke(width = stroke),
        )
      }
    }
    Spacer(Modifier.width(6.dp))
    Text(
      "${((1f - fraction) * 100).toInt()}%",
      style = MaterialTheme.typography.labelMedium,
      color = if (fraction >= 0.95f) cs.error else cs.onSurfaceVariant,
    )
  }
}

/** Full detail for the model picker / overflow: exact counts in the mono face. */
@Composable
fun ContextDetailRow(used: Int, limit: Int, modifier: Modifier = Modifier) {
  if (limit <= 0) return
  Row(modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    ContextIndicator(used, limit)
    Spacer(Modifier.width(8.dp))
    Text(
      "$used / $limit tokens",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
