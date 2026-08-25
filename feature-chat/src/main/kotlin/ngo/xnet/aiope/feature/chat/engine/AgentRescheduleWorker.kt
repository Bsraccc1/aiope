package ngo.xnet.aiope.feature.chat.engine

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Re-arms AlarmManager alarms for every enabled scheduled task.
 * Runs once on app start and after BOOT_COMPLETED (exact alarms do not survive reboot).
 */
class AgentRescheduleWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    val db = AgentDb.get(applicationContext)
    try {
      for (task in db.chatDao().getEnabledScheduledTasks()) {
        // A stored nextRun still in the future is the user's schedule; keep it and just re-arm the
        // alarm that was lost to the reboot. Recomputing would push a daily 09:00 task to tomorrow
        // whenever the phone restarted before it fired.
        val updated = if (task.nextRun != null && task.nextRun > System.currentTimeMillis()) {
          task
        } else {
          AgentScheduler.plan(task).also { planned ->
            if (planned.nextRun != task.nextRun) {
              db.chatDao().updateScheduledTaskRun(planned.id, planned.lastRun, planned.nextRun)
            }
          }
        }
        AgentScheduler.arm(applicationContext, updated)
      }
      Result.success()
    } catch (e: Exception) {
      if (runAttemptCount >= 3) Result.failure() else Result.retry()
    }
  }

  companion object {
    fun enqueue(context: Context) {
      OneTimeWorkRequestBuilder<AgentRescheduleWorker>().build().also {
        WorkManager.getInstance(context).enqueue(it)
      }
    }
  }
}
