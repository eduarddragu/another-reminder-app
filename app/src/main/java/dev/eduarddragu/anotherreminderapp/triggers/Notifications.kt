package dev.eduarddragu.anotherreminderapp.triggers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.eduarddragu.anotherreminderapp.MainActivity
import dev.eduarddragu.anotherreminderapp.R
import dev.eduarddragu.anotherreminderapp.data.Store
import dev.eduarddragu.anotherreminderapp.domain.Copy
import dev.eduarddragu.anotherreminderapp.domain.Place
import dev.eduarddragu.anotherreminderapp.domain.Reminder
import kotlinx.serialization.builtins.ListSerializer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.json.Json

object Notifications {
  private const val CHANNEL = "reminders_v1"
  const val ACTION_DONE = "dev.eduarddragu.anotherreminderapp.DONE"
  const val ACTION_UNDO = "dev.eduarddragu.anotherreminderapp.UNDO"
  const val EXTRA_BEFORE = "before"
  const val EXTRA_IDS = "ids"

  fun createChannel(context: Context) =
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH))

  fun enabled(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED && NotificationManagerCompat.from(context).areNotificationsEnabled()

  /**
   * One notification per reminder (its id), with Done. Showing it again replaces it. The small icon says
   * which kind it is; the place's name or the repeat rides along as the sub text.
   */
  fun show(context: Context, reminder: Reminder, title: String, subText: String?) {
    if (!enabled(context)) return
    val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    val done = doneIntent(context, reminder.id, listOf(reminder.id))
    val notification =
      NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(if (reminder.placeId != null) R.drawable.ic_pin else R.drawable.ic_clock)
        .setColor(context.getColor(R.color.accent))
        .setSubText(subText)
        .apply { reminder.at?.let { setWhen(it).setShowWhen(true) } }
        .setContentTitle(title)
        .setContentText(reminder.text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.text))
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setContentIntent(open)
        .setAutoCancel(true)
        .addAction(0, "Done", done)
        .build()
    @Suppress("MissingPermission") // checked by enabled()
    NotificationManagerCompat.from(context).notify(reminder.id, notification)
  }

  /**
   * A place's notification: one for every reminder waiting there, all in one ([place]'s id, which no
   * reminder shares), the things as lines, and "All done".
   */
  fun showPlace(context: Context, place: Place, reminders: List<Reminder>, title: String) {
    if (!enabled(context) || reminders.isEmpty()) return
    val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    val text = Copy.placeItems(reminders.map { it.text })
    val notification =
      NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_pin)
        .setColor(context.getColor(R.color.accent))
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(if (reminders.size == 1) NotificationCompat.BigTextStyle().bigText(text) else NotificationCompat.InboxStyle().also { style -> reminders.forEach { style.addLine("\u2022 ${it.text}") } })
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setContentIntent(open)
        .setAutoCancel(true)
        .addAction(0, if (reminders.size == 1) "Done" else "All done", doneIntent(context, place.id, reminders.map { it.id }))
        .build()
    @Suppress("MissingPermission") // checked by enabled()
    NotificationManagerCompat.from(context).notify(place.id, notification)
  }

  /** Done for [ids], from the notification [notificationId]. */
  private fun doneIntent(context: Context, notificationId: Int, ids: List<Int>): PendingIntent =
    PendingIntent.getBroadcast(
      context,
      notificationId,
      Intent(context, DoneReceiver::class.java).setAction(ACTION_DONE).putExtra(Alarms.EXTRA_ID, notificationId).putExtra(EXTRA_IDS, ids.toIntArray()),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

  /**
   * After Done from a notification: a quiet "Done." in its place for 10 seconds, with Undo, which puts
   * [before] (the reminders as they were) back. A pocket tap shouldn't lose anything for good.
   */
  fun showDone(context: Context, notificationId: Int, before: List<Reminder>, back: Long?) {
    if (!enabled(context) || before.isEmpty()) return
    val undo =
      PendingIntent.getBroadcast(
        context,
        notificationId,
        Intent(context, DoneReceiver::class.java).setAction(ACTION_UNDO).putExtra(Alarms.EXTRA_ID, notificationId).putExtra(EXTRA_BEFORE, Json.encodeToString(ListSerializer(Reminder.serializer()), before)),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )
    val title = back?.let { "Done. Back on ${Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))}." } ?: if (before.size > 1) "All done. Nicely." else "Done."
    val notification =
      NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_check)
        .setColor(context.getColor(R.color.accent))
        .setContentTitle(title)
        .setContentText(Copy.placeItems(before.map { it.text }))
        .setSilent(true)
        .setOnlyAlertOnce(true)
        .setTimeoutAfter(10_000)
        .addAction(0, "Undo", undo)
        .build()
    @Suppress("MissingPermission") // checked by enabled()
    NotificationManagerCompat.from(context).notify(notificationId, notification)
  }

  fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)
}

class DoneReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val notificationId = intent.getIntExtra(Alarms.EXTRA_ID, 0)
    if (intent.action == Notifications.ACTION_UNDO) {
      Notifications.cancel(context, notificationId)
      val before = intent.getStringExtra(Notifications.EXTRA_BEFORE)?.let { runCatching { Json.decodeFromString(ListSerializer(Reminder.serializer()), it) }.getOrNull() } ?: return
      // Not into a place deleted since.
      Store.update(context) { d -> before.filter { it.placeId == null || d.place(it.placeId) != null }.fold(d) { acc, r -> acc.upsert(r) } }
      return
    }
    // Older notifications carry only their reminder's id.
    val ids = intent.getIntArrayExtra(Notifications.EXTRA_IDS)?.toList() ?: listOf(notificationId)
    val before = ids.mapNotNull(Store.data.value::reminder)
    if (before.isEmpty()) return Notifications.cancel(context, notificationId)
    Store.update(context) { d -> ids.fold(d) { acc, id -> acc.complete(id, Instant.now(), ZoneId.systemDefault()) } }
    Notifications.showDone(context, notificationId, before, before.singleOrNull()?.let { Store.data.value.reminder(it.id)?.at })
  }
}
