package ngo.xnet.aiope.feature.chat.ui

/**
 * Slash commands typed straight into the composer.
 *
 * These are UI shortcuts, not tools: the model never sees them. Each one either rewrites the
 * outgoing text into an explicit instruction or triggers a local action, so the user can drive
 * common flows without reaching for a menu.
 */
enum class SlashKind {
  /** Replaces the typed text with [SlashCommand.expansion] and sends it. */
  PROMPT,

  /** Runs a local action in the app; nothing is sent to the model. */
  ACTION,
}

data class SlashCommand(
  val name: String,
  val hint: String,
  val kind: SlashKind,
  /** For PROMPT commands: the instruction actually sent. `%s` is replaced by any trailing text. */
  val expansion: String = "",
)

val SLASH_COMMANDS = listOf(
  SlashCommand(
    "compact",
    "Summarise the conversation so far to free context",
    SlashKind.ACTION,
  ),
  SlashCommand(
    "clear",
    "Start a new chat",
    SlashKind.ACTION,
  ),
  SlashCommand(
    "tools",
    "List the tools currently enabled",
    SlashKind.PROMPT,
    "List every tool you can currently call, grouped by purpose, one line each. Do not call any.",
  ),
  SlashCommand(
    "goals",
    "Show persistent goals",
    SlashKind.PROMPT,
    "Use goal_list and show my persistent goals with their progress.",
  ),
  SlashCommand(
    "memory",
    "Show what you remember",
    SlashKind.PROMPT,
    "Summarise what you currently hold in persistent memory about me and this device.",
  ),
  SlashCommand(
    "skills",
    "List installed skill playbooks",
    SlashKind.PROMPT,
    "Use skill_list and show the skill playbooks installed on this device.",
  ),
  SlashCommand(
    "search",
    "Search past conversations",
    SlashKind.PROMPT,
    "Use search_messages to find %s in my past conversations and summarise what you find.",
  ),
  SlashCommand(
    "plan",
    "Plan a task before doing it",
    SlashKind.PROMPT,
    "Plan this before acting — list the steps, then wait for my go-ahead: %s",
  ),
  SlashCommand(
    "device",
    "Report device status",
    SlashKind.PROMPT,
    "Use device_info and datetime_now, then report this device's state in a short table.",
  ),
  SlashCommand(
    "schedule",
    "Open the timers panel",
    SlashKind.ACTION,
  ),
)

/**
 * Commands matching what the user has typed, or null when the text isn't a slash query at all.
 *
 * Null and empty are kept distinct so a caller can tell "not typing a command" from "typing one that
 * matches nothing"; the current sheet hides on both. Only a leading slash on the first word counts —
 * a slash mid-message is a path or a date, not a command.
 */
fun matchSlash(text: String): List<SlashCommand>? {
  if (!text.startsWith("/")) return null
  val (name, arg) = splitSlash(text)
  // Once an argument has been typed the user has moved on; keep the exact match so the hint stays
  // visible, but stop offering alternatives.
  if (arg.isNotEmpty()) {
    return SLASH_COMMANDS.filter { it.name.equals(name, ignoreCase = true) }
  }
  return SLASH_COMMANDS.filter { it.name.startsWith(name, ignoreCase = true) }
}

/**
 * Split "/name argument" into its two halves.
 *
 * Any whitespace run separates the two, not just a space: a pasted or soft-wrapped message can put
 * a newline or tab after the command name, and treating that as "no argument yet" would both keep
 * the autocomplete sheet up over a finished message and silently drop the argument on send.
 */
fun splitSlash(text: String): Pair<String, String> {
  val body = text.removePrefix("/")
  val parts = body.split(Regex("\\s+"), limit = 2)
  return parts[0] to parts.getOrElse(1) { "" }.trim()
}

/** The text to actually send for [cmd], given the full composer contents. */
fun expandSlash(cmd: SlashCommand, typed: String): String {
  val trailing = splitSlash(typed).second
  return when {
    cmd.expansion.contains("%s") -> cmd.expansion.replace("%s", trailing.ifBlank { "the above" })
    trailing.isBlank() -> cmd.expansion
    else -> "${cmd.expansion}\n\n$trailing"
  }
}
