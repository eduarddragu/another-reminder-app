package dev.eduarddragu.anotherreminderapp.triggers

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import dev.eduarddragu.anotherreminderapp.data.Store
import dev.eduarddragu.anotherreminderapp.domain.Copy
import dev.eduarddragu.anotherreminderapp.domain.Data
import dev.eduarddragu.anotherreminderapp.domain.Fences
import dev.eduarddragu.anotherreminderapp.domain.Place
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow

/** Keeps Play services' geofences in line with [Fences.plan]. */
object PlaceWatch {
  /** Why place reminders can't fire right now, for Home. Null when they can (or none are waiting). */
  val problem = MutableStateFlow<String?>(null)

  fun hasPermission(context: Context): Boolean =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION).all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

  /**
   * Whether Play services may be missing fences: one couldn't be added (no permission, location off) or
   * Play services dropped them all (GEOFENCE_NOT_AVAILABLE). Saved, so a new process still knows; only a
   * full [resync] that succeeds clears it, and while it's set the hourly check and opening the app resync.
   */
  fun needsResync(context: Context): Boolean = prefs(context).getBoolean(KEY_NEEDED, false)

  fun markNeeded(context: Context) = prefs(context).edit { putBoolean(KEY_NEEDED, true) }

  /**
   * Registers every fence again from scratch: after a reboot, an app update, or while [needsResync]. A
   * fence you're inside won't fire until you leave and come back, so it never runs just because the app opened. [done] runs once Play
   * services has answered, for a receiver holding its broadcast open.
   */
  fun resync(context: Context, data: Data, done: () -> Unit = {}) {
    val fences = Fences.plan(data)
    LocationServices.getGeofencingClient(context).removeGeofences(intent(context)).addOnCompleteListener {
      add(context, fences, full = true, done = done)
    }
  }

  /**
   * Moves the registered fences from [old]'s plan to [new]'s, touching only those that changed: a replaced
   * fence doesn't fire while you're inside it, so adding a reminder at the shop you're standing in must leave it alone.
   */
  fun update(context: Context, old: Data, new: Data) {
    val (gone, added) = Fences.diff(old, new)
    if (gone.isNotEmpty()) LocationServices.getGeofencingClient(context).removeGeofences(gone)
    add(context, added, full = false)
  }

  /** [full]: the whole plan after a remove-all, so success means Play services holds exactly the plan. */
  @SuppressLint("MissingPermission") // checked just below
  private fun add(context: Context, fences: List<Fences.Fence>, full: Boolean, done: () -> Unit = {}) {
    fun settled() {
      if (full) prefs(context).edit { putBoolean(KEY_NEEDED, false) }
      if (full || !needsResync(context)) problem.value = null
      done()
    }
    if (fences.isEmpty()) return settled()
    if (!hasPermission(context)) {
      markNeeded(context)
      problem.value = describe(GeofenceStatusCodes.GEOFENCE_INSUFFICIENT_LOCATION_PERMISSION)
      return done()
    }
    val request =
      GeofencingRequest.Builder()
        // No initial trigger: a resync must not fire again for a place you're already in.
        .setInitialTrigger(0)
        .addGeofences(
          fences.map {
            Geofence.Builder()
              .setRequestId(it.requestId)
              .setCircularRegion(it.spot.lat, it.spot.lng, Place.RADIUS.toFloat())
              .setExpirationDuration(Geofence.NEVER_EXPIRE)
              // ENTER, not DWELL: indoors Play services often has only cell fixes (±200 m), too coarse to see two minutes inside.
              .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
              .build()
          }
        )
        .build()
    LocationServices.getGeofencingClient(context)
      .addGeofences(request, intent(context))
      .addOnSuccessListener { settled() }
      .addOnFailureListener { error ->
        Log.w("PlaceWatch", "addGeofences failed", error)
        markNeeded(context)
        problem.value = describe((error as? ApiException)?.statusCode)
        done()
      }
  }

  private fun prefs(context: Context) = context.getSharedPreferences("system", Context.MODE_PRIVATE)

  private const val KEY_NEEDED = "fences_need_resync"

  fun describe(code: Int?): String =
    when (code) {
      GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE -> "Location is off, or Google Location Accuracy is off: place reminders can't fire."
      GeofenceStatusCodes.GEOFENCE_INSUFFICIENT_LOCATION_PERMISSION -> "Place reminders fire with the app closed. In Settings, pick \u201cAllow all the time\u201d."
      GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES -> "Too many spots: Android watches at most ${Fences.MAX}."
      else -> "Place reminders couldn't be set up (code $code)."
    }

  // Mutable: Play services fills in the triggering geofences.
  private fun intent(context: Context): PendingIntent =
    PendingIntent.getBroadcast(context, 0, Intent(context, GeofenceReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
}

class GeofenceReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val event = GeofencingEvent.fromIntent(intent) ?: return
    if (event.hasError()) {
      // Usually location turned off: Play services has dropped every fence, and the next check puts them back.
      PlaceWatch.markNeeded(context)
      PlaceWatch.problem.value = PlaceWatch.describe(event.errorCode)
      return
    }
    if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return
    val placeIds = event.triggeringGeofences.orEmpty().mapNotNull { Fences.placeId(it.requestId) }.toSet()
    val data = Store.data.value
    val prefs = context.getSharedPreferences("system", Context.MODE_PRIVATE)
    val now = System.currentTimeMillis()
    // One notification per place, with everything waiting there. Coarse fixes at the edge of a fence enter it
    // again and again: once an hour per place is enough.
    placeIds.mapNotNull(data::place).filter { now - prefs.getLong("notified_${it.id}", 0) >= QUIET_MS }.forEach { place ->
      val waiting = data.reminders.filter { it.placeId == place.id }
      if (waiting.isEmpty()) return@forEach
      Notifications.showPlace(context, place, waiting, Copy.placeTitle(place.name, Copy.placeSeed(place.id)))
      prefs.edit { putLong("notified_${place.id}", now) }
    }
  }

  private companion object {
    const val QUIET_MS = 60 * 60_000L
  }
}
