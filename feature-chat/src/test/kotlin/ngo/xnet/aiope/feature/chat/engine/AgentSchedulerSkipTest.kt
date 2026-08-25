package ngo.xnet.aiope.feature.chat.engine

import ngo.xnet.aiope.feature.chat.db.ScheduledTaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * A Wi-Fi-only task that fires on mobile data must be re-armed rather than dropped, and the retry
 * time is where this goes wrong: [AgentScheduler.computeNextRun] pushes `"once"` 60s out and an
 * interval-in-minutes task a few minutes out, so reusing it verbatim would wake the device in a
 * tight loop for as long as the user stays on data.
 */
class AgentSchedulerSkipTest {

  private val floorMs = 15 * 60 * 1000L

  private fun task(
    type: String,
    intervalValue: Int = 0,
    intervalUnit: String = "min",
    hour: Int = 9,
    minute: Int = 0,
    maxRuns: Int = 0,
    runsCompleted: Int = 0,
  ) = ScheduledTaskEntity(
    prompt = "check the news",
    scheduleType = type,
    intervalValue = intervalValue,
    intervalUnit = intervalUnit,
    timeHour = hour,
    timeMinute = minute,
    maxRuns = maxRuns,
    runsCompleted = runsCompleted,
    wifiOnly = true,
  )

  private fun now() = Calendar.getInstance().apply {
    set(2026, Calendar.AUGUST, 25, 12, 0, 0)
    set(Calendar.MILLISECOND, 0)
  }.timeInMillis

  @Test
  fun onceRetriesOnTheFloorNotIn60Seconds() {
    val now = now()
    val next = AgentScheduler.nextRunAfterSkip(task("once"), now)!!
    assertEquals(now + floorMs, next)
  }

  @Test
  fun shortIntervalIsClampedToTheFloor() {
    val now = now()
    // Every 5 minutes: without the floor this retries 3x within the floor window.
    val next = AgentScheduler.nextRunAfterSkip(task("interval", intervalValue = 5), now)!!
    assertEquals(now + floorMs, next)
  }

  @Test
  fun longIntervalKeepsItsOwnCadence() {
    val now = now()
    val next = AgentScheduler.nextRunAfterSkip(task("interval", intervalValue = 6, intervalUnit = "hour"), now)!!
    assertEquals(now + 6 * 60 * 60 * 1000L, next)
    assertTrue(next > now + floorMs)
  }

  @Test
  fun dailyKeepsItsWallClockSlot() {
    val now = now() // 12:00
    // A 09:00 daily task skipped at noon should wait for tomorrow 09:00, not 12:15.
    val next = AgentScheduler.nextRunAfterSkip(task("daily", hour = 9), now)!!
    val cal = Calendar.getInstance().apply { timeInMillis = next }
    assertEquals(9, cal.get(Calendar.HOUR_OF_DAY))
    assertEquals(0, cal.get(Calendar.MINUTE))
    assertTrue("must be in the future", next > now)
    assertTrue("must not be pulled forward to the floor", next > now + floorMs)
  }

  @Test
  fun exhaustedTaskIsNotRearmed() {
    // Cap reached: computeNextRun returns null and the skip path must finish the task, not floor it.
    assertNull(AgentScheduler.nextRunAfterSkip(task("daily", maxRuns = 3, runsCompleted = 3), now()))
  }

  @Test
  fun unknownScheduleTypeIsFinishedNotRearmed() {
    // computeNextRun returns null for a type it doesn't know, and schedule() treats that as
    // "finished". The skip path must agree, or a corrupt row would be re-armed every 15 minutes.
    assertNull(AgentScheduler.nextRunAfterSkip(task("bogus"), now()))
  }
}
