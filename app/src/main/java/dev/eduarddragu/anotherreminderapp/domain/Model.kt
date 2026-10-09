package dev.eduarddragu.anotherreminderapp.domain

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.serialization.Serializable

/** A position, with what it is when known ("Esselunga · Viale Piave 38"), so five shops aren't five number pairs. */
@Serializable
data class Spot(val lat: Double, val lng: Double, val label: String = "") {
  /** The same position whatever it's called: two spots there are one. */
  fun samePlace(other: Spot) = lat == other.lat && lng == other.lng
}

/** A kind of place ("Esselunga") with every spot it can be: one geofence per spot. */
@Serializable
data class Place(val id: Int, val name: String, val spots: List<Spot> = emptyList(), val radius: Int = DEFAULT_RADIUS) {
  companion object {
    /** Google's advice: 100 m or more, for the accuracy of Wi-Fi positioning. */
    const val DEFAULT_RADIUS = 150
    val RADII = listOf(100, 150, 300)
  }
}

/**
 * Fires at a place ([placeId]) or at a time ([at], epoch millis), never both. A place reminder fires on every
 * visit until it's done; a time reminder fires once ([fired]) and stays on the list until it's done, when one
 * that [repeat]s moves on to its next date.
 */
@Serializable
data class Reminder(
  val id: Int,
  val text: String,
  val placeId: Int? = null,
  val at: Long? = null,
  val fired: Boolean = false,
  val repeat: Repeat? = null,
  /** When the last notification about it went out (heads-up, due or a late nudge): [Nudges.next] goes on from there. */
  val lastNudge: Long? = null,
)

/** Every [count] [unit]s: every 2 weeks, every 5 days, every year. */
@Serializable
data class Repeat(val count: Int, val unit: RepeatUnit) {
  init {
    require(count in 1..MAX) { "count out of range: $count" }
  }

  companion object {
    const val MAX = 999
  }
}

@Serializable
enum class RepeatUnit(val chrono: ChronoUnit) {
  DAYS(ChronoUnit.DAYS),
  WEEKS(ChronoUnit.WEEKS),
  MONTHS(ChronoUnit.MONTHS),
  YEARS(ChronoUnit.YEARS),
}

@Serializable
data class Data(val places: List<Place> = emptyList(), val reminders: List<Reminder> = emptyList(), val nextId: Int = 1) {
  fun place(id: Int?): Place? = places.firstOrNull { it.id == id }

  fun reminder(id: Int): Reminder? = reminders.firstOrNull { it.id == id }

  /**
   * Shared by places and reminders; a reminder's id is also its notification id and alarm request code.
   * Never reused ([nextId] only grows), so a stale Done or Undo can't reach a later reminder.
   */
  fun newId(): Int = maxOf(nextId, (places.map { it.id } + reminders.map { it.id }).maxOrNull()?.plus(1) ?: 1)

  fun upsert(reminder: Reminder) =
    copy(reminders = if (reminder(reminder.id) != null) reminders.map { if (it.id == reminder.id) reminder else it } else reminders + reminder, nextId = maxOf(nextId, reminder.id + 1))

  fun upsert(place: Place) = copy(places = if (place(place.id) != null) places.map { if (it.id == place.id) place else it } else places + place, nextId = maxOf(nextId, place.id + 1))

  /** Removes the place and the reminders that fire there: without it they could never fire. */
  fun removePlace(id: Int) = copy(places = places.filter { it.id != id }, reminders = reminders.filter { it.placeId != id })

  /** Places in the order of [ids] (the order dragged on the places list); any not named keep their place at the end. */
  fun reorderPlaces(ids: List<Int>) = copy(places = ids.mapNotNull(::place) + places.filter { it.id !in ids })

  fun removeReminder(id: Int) = copy(reminders = reminders.filter { it.id != id })

  /** Done: a repeating reminder moves to its first date after [now] (at least one step on), anything else goes. */
  fun complete(id: Int, now: Instant, zone: ZoneId): Data {
    val reminder = reminder(id) ?: return this
    if (reminder.repeat == null || reminder.at == null) return removeReminder(id)
    return upsert(reminder.copy(at = nextRepeat(reminder.at, reminder.repeat, maxOf(now, Instant.ofEpochMilli(reminder.at)), zone), fired = false, lastNudge = null))
  }
}

/**
 * The first of [at], [at] + 1 step, + 2 steps... that is after [after], in local time (09:00 stays 09:00
 * across daylight saving). Counted from [at] each time, so the 31st comes back on the 31st where a month
 * has one, and a 29 February on leap years, instead of drifting to the 28th.
 */
fun nextRepeat(at: Long, repeat: Repeat, after: Instant, zone: ZoneId): Long {
  val first = Instant.ofEpochMilli(at).atZone(zone)
  return generateSequence(0L) { it + repeat.count }.map { first.plus(it, repeat.unit.chrono).toInstant() }.first { it.isAfter(after) }.toEpochMilli()
}
