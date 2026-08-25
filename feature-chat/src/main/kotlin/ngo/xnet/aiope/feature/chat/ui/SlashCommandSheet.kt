package ngo.xnet.aiope.feature.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Autocomplete list shown above the composer while the user is typing a slash command.
 *
 * Sits in the same glass language as the composer and derives its radius from it: the composer
 * capsule is [CuORadius.xl] and this sheet floats 6dp above it, so `inner(xl, 6)` keeps the gap
 * between the two curves visually constant instead of stacking two identical 28dp corners.
 */
@Composable
fun SlashCommandSheet(
  matches: List<SlashCommand>,
  onPick: (SlashCommand) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (matches.isEmpty()) return
  val cs = MaterialTheme.colorScheme

  GlassSurface(
    modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
    shape = RoundedCornerShape(CuORadius.inner(CuORadius.xl, 6.dp)),
    tintAlpha = GLASS_TINT_READABLE,
  ) {
    // Cap the height so a broad match ("/" alone) can't push the composer off screen.
    LazyColumn(Modifier.heightIn(max = 220.dp).padding(vertical = 4.dp)) {
      items(matches, key = { it.name }) { cmd ->
        Row(
          Modifier
            .fillMaxWidth()
            .clickable { onPick(cmd) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            "/${cmd.name}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = cs.primary,
          )
          Spacer(Modifier.width(10.dp))
          Column(Modifier.weight(1f)) {
            Text(
              cmd.hint,
              style = MaterialTheme.typography.bodySmall,
              color = cs.onSurfaceVariant,
              maxLines = 1,
            )
          }
          if (cmd.kind == SlashKind.ACTION) {
            Text(
              "action",
              style = MaterialTheme.typography.labelSmall,
              color = cs.onSurfaceVariant.copy(alpha = 0.7f),
            )
          }
        }
      }
    }
  }
}
