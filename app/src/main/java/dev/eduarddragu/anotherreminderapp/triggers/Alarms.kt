package dev.eduarddragu.anotherreminderapp.triggers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.edit
import dev.eduarddragu.anotherreminderapp.data.Store
import dev.eduarddragu.anotherreminderapp.domain.Copy
import dev.eduarddragu.anotherreminderapp.domain.Data
import dev.eduarddragu.anotherreminderapp.domain.Nudges
import java.time.ZoneId

/** One exact alarm per time reminder that hasn't fired yet. A time in the past fires right away. */
object Alarms {
  /** One exact alarm per time reminder, for the next notification of its sequence ([Nudges]). */
  fun sync(context: Context, old: Data, new: Data) {
    val alarms = context.getSystemService(AlarmManager::class.java)
    val now = System.currentTimeMillis()
    old.reminders.filter { it.at != null }.forEach { alarms.cancel(intent(context, it.id, null)) }
    new.reminders.forEach { reminder ->
      val nudge = Nudges.next(reminder, now, ZoneId.systemDefault()) ?: return@forEach
      // USE_EXACT_ALARM is granted at install for a reminder app. A past time fires at once.
      alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, maxOf(nudge.at, now), intent(context, reminder.id, nudge to reminder.at))
    }
  }

  /**
   * Geofences go away when location is turned off and nothing tells the app when it comes back
   * (PROVIDERS_CHANGED isn't delivered to manifest receivers), so every hour it looks, and registers them
   * again only when location went from off to on.
   */
  fun scheduleResync(context: Context) {
    val resync = Intent(context, SystemReceiver::class.java).setAction(SystemReceiver.ACTION_RESYNC)
    // Set once: setting it again on every process start would push the first check back each time.
    if (PendingIntent.getBroadcast(context, 0, resync, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE) != null) return
    val intent = PendingIntent.getBroadcast(context, 0, resync, PendingIntent.FLAG_IMMUTABLE)
    context.getSystemService(AlarmManager::class.java).setInexactRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_HOUR, AlarmManager.INTERVAL_HOUR, intent)
  }

  /** [nudge] with the reminder's time it was planned for: an edit since then makes the alarm stale. */
  private fun intent(context: Context, id: Int, nudge: Pair<Nudges.Nudge, Long?>?): PendingIntent {
    val intent = Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_ID, id)
    nudge?.let { (n, forAt) -> intent.putExtra(EXTRA_KIND, n.kind.name).putExtra(EXTRA_DAYS_LATE, n.daysLate).putExtra(EXTRA_PLANNED, n.at).putExtra(EXTRA_FOR_AT, forAt ?: 0L) }
    return PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  }

  const val EXTRA_ID = "reminder_id"
  private const val EXTRA_KIND = "kind"
  private const val EXTRA_DAYS_LATE = "days_late"
  private const val EXTRA_PLANNED = "planned"
  private const val EXTRA_FOR_AT = "for_at"

  fun nudgeOf(intent: Intent): Pair<Nudges.Nudge, Long>? {
    val kind = intent.getStringExtra(EXTRA_KIND)?.let { runCatching { Nudges.Kind.valueOf(it) }.getOrNull() } ?: return null
    return Nudges.Nudge(intent.getLongExtra(EXTRA_PLANNED, 0), kind, intent.getIntExtra(EXTRA_DAYS_LATE, 0)) to intent.getLongExtra(EXTRA_FOR_AT, 0)
  }
}

class AlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val reminder = Store.data.value.reminder(intent.getIntExtra(Alarms.EXTRA_ID, 0)) ?: return
    val (nudge, forAt) = Alarms.nudgeOf(intent) ?: return
    // Planned for a time the reminder no longer has: the sync after that edit set the right alarm.
    if (reminder.at == null || reminder.at != forAt) return
    val title = Copy.nudgeTitle(nudge, Copy.timeSeed(reminder.id), reminder.repeat)
    Notifications.show(context, reminder, title, reminder.repeat?.let(Copy::repeatLabel))
    val fired = reminder.fired || nudge.kind == Nudges.Kind.DUE || nudge.kind == Nudges.Kind.LATE
    // Saving moves the alarm on to the next notification of the sequence.
    Store.update(context) { it.upsert(reminder.copy(fired = fired, lastNudge = System.currentTimeMillis())) }
  }
}

/** Boot and app updates clear alarms (and, with Play services, may drop geofences): set everything again. */
class SystemReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val data = Store.data.value
    val prefs = context.getSharedPreferences("system", Context.MODE_PRIVATE)
    val locationOn = context.getSystemService(LocationManager::class.java).isLocationEnabled
    val cameBack = locationOn && !prefs.getBoolean(KEY_LOCATION_ON, true)
    prefs.edit { putBoolean(KEY_LOCATION_ON, locationOn) }
    if (intent.action != ACTION_RESYNC) Alarms.sync(context, Data(), data)
    // A new clock or time zone: the nudges' evening and night hours are local, so only the alarms move.
    if (intent.action == Intent.ACTION_TIMEZONE_CHANGED || intent.action == Intent.ACTION_TIME_CHANGED) return
    if (intent.action == ACTION_RESYNC && !cameBack && !PlaceWatch.needsResync(context)) return
    // Held open until Play services answers: a cached process can be frozen before the callback runs.
    val pending = goAsync()
    PlaceWatch.resync(context, data) { pending.finish() }
  }

  companion object {
    const val ACTION_RESYNC = "dev.eduarddragu.anotherreminderapp.RESYNC"
    private const val KEY_LOCATION_ON = "location_on"
  }
}
