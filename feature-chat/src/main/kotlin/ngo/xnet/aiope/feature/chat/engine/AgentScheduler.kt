package ngo.xnet.aiope.feature.chat.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import ngo.xnet.aiope.feature.chat.db.ScheduledTaskEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Exact-alarm scheduler for scheduled agent tasks.
 *
 * Each enabled task owns one AlarmManager alarm (PendingIntent -> AgentAlarmReceiver)
 * keyed by task id. The alarm fires at [ScheduledTaskEntity.nextRun]; the receiver
 * enqueues [AgentRunWorker], which runs the agent and re-arms the next alarm
 * (or disables the task when the max-runs cap is reached).
 *
 * Alarms are lost on reboot / force-stop, so [AgentRescheduleWorker] re-arms all
 * enabled tasks on app start and after BOOT_COMPLETED.
 */
object AgentScheduler {

  const val EXTRA_TASK_ID = "ngo.xnet.aiope.extra.TASK_ID"
  const val ACTION_AGENT_ALARM = "ngo.xnet.aiope.action.AGENT_ALARM"

  /** Whether exact alarms are available (Android 12+ needs SCHEDULE_EXACT_ALARM). */
  fun canScheduleExact(context: Context): Boolean {
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
  }

  /** Next fire time for [task], or null when the schedule is done (once, or cap reached). */
  fun computeNextRun(task: ScheduledTaskEntity, fromMillis: Long = System.currentTimeMillis()): Long? {
    if (task.maxRuns > 0 && task.runsCompleted >= task.maxRuns) return null
    return when (task.scheduleType) {
      "interval" -> fromMillis + task.intervalValue.coerceAtLeast(1) * unitMillis(task.intervalUnit)
      "daily" -> nextWallClock(task.timeHour, task.timeMinute, null, fromMillis)
      "weekly" -> nextWallClock(task.timeHour, task.timeMinute, task.daysOfWeek, fromMillis)
      "monthly" -> nextMonthly(task, fromMillis)
      "once" -> fromMillis + 60_000L
      else -> null
    }
  }

  /**
   * Decide the next fire time for [task] without touching AlarmManager.
   *
   * Returns the entity with [ScheduledTaskEntity.nextRun] set, or a disabled copy when the schedule
   * is complete (max-runs cap reached / non-recurring). Callers persist the result and then call
   * [arm]; see [arm] for why the write has to come first.
   */
  fun plan(task: ScheduledTaskEntity): ScheduledTaskEntity {
    val next = computeNextRun(task) ?: return task.copy(enabled = false, nextRun = null, status = "finished")
    return task.copy(nextRun = next)
  }

  /**
   * Re-arm after a run was skipped without executing (Wi-Fi-only task on mobile data).
   *
   * [computeNextRun] is wrong for this case: `"once"` would be pushed 60s out forever, and an
   * interval task whose value is minutes would retry in a tight loop while the user stays on data.
   * Retry no sooner than [SKIP_RETRY_FLOOR_MS], and never later than the schedule's own next slot.
   */
  fun rescheduleSkipped(task: ScheduledTaskEntity): ScheduledTaskEntity {
    val next = nextRunAfterSkip(task, System.currentTimeMillis())
      ?: return task.copy(enabled = false, nextRun = null, status = "finished")
    return task.copy(nextRun = next)
  }

  /**
   * Arm the alarm for a task whose [ScheduledTaskEntity.nextRun] is already decided and persisted.
   *
   * Split from the compute step so the caller can write the row first: if the process dies between
   * the two, a pending alarm with no matching row is worse than a row with no alarm, because
   * [AgentRescheduleWorker] re-arms from the DB on next start but nothing reconciles the reverse.
   */
  fun arm(context: Context, task: ScheduledTaskEntity) {
    val next = task.nextRun ?: return
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val pi = pendingIntent(context, task.id)
    if (canScheduleExact(context)) {
      am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
    } else {
      am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
    }
  }

  /** Minimum wait before retrying a Wi-Fi-gated run, so a metered stretch can't spin the CPU. */
  private const val SKIP_RETRY_FLOOR_MS = 15 * 60 * 1000L

  /**
   * Pure next-fire-time decision for a skipped run, split out from [rescheduleSkipped] so it can be
   * tested without an Android [Context].
   */
  fun nextRunAfterSkip(task: ScheduledTaskEntity, now: Long): Long? {
    val natural = computeNextRun(task, now) ?: return null
    val floor = now + SKIP_RETRY_FLOOR_MS
    return when (task.scheduleType) {
      // Wall-clock schedules keep their slot: a daily 09:00 task retries tomorrow at 09:00, not in
      // 15 minutes, because "daily at 9" is the user's intent and data may still be metered.
      "daily", "weekly", "monthly" -> natural

      // Fire-once and interval tasks retry on the floor so a metered stretch can't spin the CPU.
      else -> maxOf(floor, natural)
    }
  }

  fun cancel(context: Context, taskId: String) {
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    am.cancel(pendingIntent(context, taskId))
  }

  private fun unitMillis(unit: String): Long = when (unit) {
    "hour" -> 3_600_000L
    "day" -> 86_400_000L
    else -> 60_000L
  }

  private fun nextWallClock(hour: Int, minute: Int, daysOfWeek: String?, fromMillis: Long): Long {
    val allowed = daysOfWeek?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toSet()
    for (offset in 0..400) {
      val cal = Calendar.getInstance().apply {
        timeInMillis = fromMillis
        add(Calendar.DAY_OF_YEAR, offset)
        set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
        set(Calendar.MINUTE, minute.coerceIn(0, 59))
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }
      if (allowed != null) {
        val dow = cal.get(Calendar.DAY_OF_WEEK) // 1=Sun .. 7=Sat
        if (dow !in allowed) continue
      }
      if (cal.timeInMillis > fromMillis) return cal.timeInMillis
    }
    return fromMillis + 24 * 3_600_000L
  }

  private fun nextMonthly(task: ScheduledTaskEntity, fromMillis: Long): Long {
    val day = task.dayOfMonth.coerceIn(1, 28)
    for (offset in 0..24) {
      val cal = Calendar.getInstance().apply {
        timeInMillis = fromMillis
        if (offset > 0) add(Calendar.MONTH, offset)
        set(Calendar.DAY_OF_MONTH, day)
        set(Calendar.HOUR_OF_DAY, task.timeHour.coerceIn(0, 23))
        set(Calendar.MINUTE, task.timeMinute.coerceIn(0, 59))
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }
      if (cal.timeInMillis > fromMillis) return cal.timeInMillis
    }
    return fromMillis + 30L * 24 * 3_600_000L
  }

  private fun pendingIntent(context: Context, taskId: String): PendingIntent {
    val intent = Intent(context, AgentAlarmReceiver::class.java).apply {
      action = ACTION_AGENT_ALARM
      putExtra(EXTRA_TASK_ID, taskId)
    }
    return PendingIntent.getBroadcast(
      context,
      taskId.hashCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  /** Human-readable schedule description for the UI. */
  fun describe(task: ScheduledTaskEntity): String {
    val time = "${task.timeHour.toString().padStart(2, '0')}:${task.timeMinute.toString().padStart(2, '0')}"
    val base = when (task.scheduleType) {
      "once" -> "Once, 60s after save"

      "interval" -> "Every ${task.intervalValue} ${unitName(task.intervalUnit)}"

      "daily" -> "Daily at $time"

      "weekly" -> {
        val days = task.daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }.map { DAY_NAMES[it] ?: "?" }
        "Weekly ${days.joinToString(",")} at $time"
      }

      "monthly" -> "Monthly day ${task.dayOfMonth} at $time"

      else -> task.scheduleType
    }
    val cap = if (task.maxRuns > 0) " · ${task.runsCompleted}/${task.maxRuns}" else ""
    return base + cap
  }

  fun formatTime(millis: Long?): String = millis?.let { SimpleDateFormat("EEE MMM d HH:mm", Locale.getDefault()).format(Date(it)) } ?: "—"

  private fun unitName(unit: String): String = when (unit) {
    "hour" -> "h"
    "day" -> "d"
    else -> "min"
  }

  private val DAY_NAMES = mapOf(
    Calendar.SUNDAY to "Sun",
    Calendar.MONDAY to "Mon",
    Calendar.TUESDAY to "Tue",
    Calendar.WEDNESDAY to "Wed",
    Calendar.THURSDAY to "Thu",
    Calendar.FRIDAY to "Fri",
    Calendar.SATURDAY to "Sat",
  )
}
