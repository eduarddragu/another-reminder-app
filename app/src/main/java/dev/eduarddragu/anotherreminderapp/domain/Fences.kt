package dev.eduarddragu.anotherreminderapp.domain

/** Which geofences to register: every spot of every place that has a reminder waiting. */
object Fences {
  /** Play services' limit per app. */
  const val MAX = 100

  data class Fence(val requestId: String, val spot: Spot)

  // shortcut: past 100 spots the extra ones are dropped, upgrade to the 100 nearest the phone if that ever happens.
  fun plan(data: Data): List<Fence> {
    val waiting = data.reminders.mapNotNull { it.placeId }.toSet()
    return data.places.filter { it.id in waiting }.flatMap { place -> place.spots.mapIndexed { i, spot -> Fence("${place.id}:$i", spot.copy(label = "")) } }.take(MAX)
  }

  /** From [old]'s plan to [new]'s: the request ids to remove, and the fences to add (one with a kept id replaces it). */
  fun diff(old: Data, new: Data): Pair<List<String>, List<Fence>> {
    val before = plan(old).toSet()
    val after = plan(new).toSet()
    return (before - after).map { it.requestId } - after.map { it.requestId }.toSet() to (after - before).toList()
  }

  fun placeId(requestId: String): Int? = requestId.substringBefore(':').toIntOrNull()
}
