package ngo.xnet.aiope.feature.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The composer calls [matchSlash] on every keystroke and [expandSlash] on send, so the contract
 * these tests pin down is the difference between "sheet hidden", "sheet empty" and "command fires".
 */
class SlashCommandsTest {

  @Test
  fun `plain text is not a slash query`() {
    assertNull(matchSlash(""))
    assertNull(matchSlash("hello"))
    assertNull(matchSlash("what about docs/plan.md"))
  }

  @Test
  fun `a bare slash offers every command`() {
    assertEquals(SLASH_COMMANDS.size, matchSlash("/")!!.size)
  }

  @Test
  fun `prefix filters by name and is case insensitive`() {
    assertEquals(listOf("compact", "clear"), matchSlash("/c")!!.map { it.name })
    assertEquals(listOf("compact"), matchSlash("/COMP")!!.map { it.name })
  }

  @Test
  fun `unknown command yields empty list not null`() {
    // Distinct from null: the caller can tell a mistyped command from ordinary prose.
    assertEquals(emptyList<SlashCommand>(), matchSlash("/zzz"))
  }

  @Test
  fun `once arguments start only the exact command stays matched`() {
    val matches = matchSlash("/search kotlin coroutines")!!
    assertEquals(listOf("search"), matches.map { it.name })
    // "/c something" is not a command: no name equals "c".
    assertTrue(matchSlash("/c something")!!.isEmpty())
  }

  @Test
  fun `argument placeholder is substituted with trailing text`() {
    val search = SLASH_COMMANDS.first { it.name == "search" }
    assertEquals(
      "Use search_messages to find room migrations in my past conversations and summarise what you find.",
      expandSlash(search, "/search room migrations"),
    )
  }

  @Test
  fun `placeholder falls back rather than sending an empty instruction`() {
    val plan = SLASH_COMMANDS.first { it.name == "plan" }
    assertTrue(expandSlash(plan, "/plan").endsWith("the above"))
  }

  @Test
  fun `non placeholder command appends trailing text as context`() {
    val tools = SLASH_COMMANDS.first { it.name == "tools" }
    assertEquals(tools.expansion, expandSlash(tools, "/tools"))
    assertEquals("${tools.expansion}\n\nonly the file ones", expandSlash(tools, "/tools only the file ones"))
  }

  @Test
  fun `action commands carry no expansion and prompt commands always do`() {
    SLASH_COMMANDS.forEach { cmd ->
      when (cmd.kind) {
        SlashKind.ACTION -> assertTrue("${cmd.name} should not expand", cmd.expansion.isEmpty())
        SlashKind.PROMPT -> assertTrue("${cmd.name} needs an expansion", cmd.expansion.isNotBlank())
      }
    }
  }

  @Test
  fun `command names are unique and slash free`() {
    assertEquals(SLASH_COMMANDS.size, SLASH_COMMANDS.map { it.name }.distinct().size)
    SLASH_COMMANDS.forEach { assertTrue(it.name.none { c -> c == '/' || c.isWhitespace() }) }
  }

  @Test
  fun `any whitespace separates the command from its argument`() {
    // A pasted or soft-wrapped message can put a newline after the name. Treating that as "no
    // argument yet" both kept the sheet up over a finished message and dropped the argument.
    val plan = SLASH_COMMANDS.first { it.name == "plan" }
    assertEquals(listOf("plan"), matchSlash("/plan\nship the migration")!!.map { it.name })
    assertTrue(expandSlash(plan, "/plan\nship the migration").endsWith("ship the migration"))
    assertTrue(expandSlash(plan, "/plan\tship the migration").endsWith("ship the migration"))
  }

  @Test
  fun `exact match with an argument is case insensitive too`() {
    // The prefix branch already ignored case; the argument branch used to be case-sensitive, so
    // "/COMPACT now" matched nothing and the command became unreachable mid-typing.
    assertEquals(listOf("compact"), matchSlash("/COMPACT now")!!.map { it.name })
  }

  @Test
  fun `splitSlash separates name from argument`() {
    assertEquals("search" to "room migrations", splitSlash("/search   room migrations"))
    assertEquals("tools" to "", splitSlash("/tools"))
    assertEquals("tools" to "", splitSlash("/tools   "))
  }
}
