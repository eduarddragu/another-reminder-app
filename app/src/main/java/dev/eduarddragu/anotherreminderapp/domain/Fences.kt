package dev.eduarddragu.anotherreminderapp.domain

/** Which geofences to register: every spot of every place that has a reminder waiting. */
object Fences {
  /** Play services' limit per app. */
  const val MAX = 100

  data class Fence(val requestId: String, val spot: Spot, val radius: Int)

  // shortcut: past 100 spots the extra ones are dropped, upgrade to the 100 nearest the phone if that ever happens.
  fun plan(data: Data): List<Fence> {
    val waiting = data.reminders.mapNotNull { it.placeId }.toSet()
    return data.places.filter { it.id in waiting }.flatMap { place -> place.spots.mapIndexed { i, spot -> Fence("${place.id}:$i", spot.copy(label = ""), place.radius) } }.take(MAX)
  }

  fun placeId(requestId: String): Int? = requestId.substringBefore(':').toIntOrNull()
}
