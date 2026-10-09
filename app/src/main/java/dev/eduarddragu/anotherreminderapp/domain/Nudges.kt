package dev.eduarddragu.anotherreminderapp.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Every notification a time reminder gets, not just the one at its time: a heads-up a week before (for
 * the far ones) and the evening before, the reminder itself, then, while it isn't done, nudges that back
 * off exponentially (1, 2, 4, 8 and 16 hours after) and then come once a day for a week, never at night.
 */
object Nudges {
  enum class Kind { WEEK_BEFORE, DAY_BEFORE, DUE, LATE }

  /** One notification: when, what kind, and for a late one how many days it has been waiting. */
  data class Nudge(val at: Long, val kind: Kind, val daysLate: Int = 0)

  private val HEADS_UP_DAY = LocalTime.of(20, 0)
  private val HEADS_UP_WEEK = LocalTime.of(10, 0)
  private val NIGHT_STARTS = LocalTime.of(22, 0)
  private val MORNING = LocalTime.of(8, 0)
  private val BACKOFF_HOURS = listOf(1L, 2L, 4L, 8L, 16L)
  private const val LATE_DAYS = 7

  /** The whole sequence for a reminder at [at], in order. */
  fun plan(at: Long, zone: ZoneId): List<Nudge> {
    val due = Instant.ofEpochMilli(at).atZone(zone)
    val before =
      listOfNotNull(
        Nudge(due.minusDays(7).with(HEADS_UP_WEEK).millis(), Kind.WEEK_BEFORE),
        // The evening before, unless the reminder is itself that evening or earlier.
        due.minusDays(1).with(HEADS_UP_DAY).takeIf { it.isBefore(due.minusHours(3)) }?.let { Nudge(it.millis(), Kind.DAY_BEFORE) },
      )
    val late =
      (BACKOFF_HOURS.map { due.plusHours(it) } + (1..LATE_DAYS).map { due.plusDays(it.toLong()) })
        .map(::awake)
        .distinct()
        .map { Nudge(it.millis(), Kind.LATE, ChronoUnit.DAYS.between(due.toLocalDate(), it.toLocalDate()).toInt()) }
    return (before + Nudge(at, Kind.DUE) + late).filter { it.kind == Kind.DUE || it.at != at }.sortedBy { it.at }.distinctBy { it.at }
  }

  /**
   * The next notification after the last one sent. Heads-ups and late nudges whose time has gone by are
   * skipped (after a phone was off, one catch-up is enough); the reminder itself is never skipped.
   */
  fun next(reminder: Reminder, now: Long, zone: ZoneId): Nudge? {
    val at = reminder.at ?: return null
    val after = reminder.lastNudge ?: Long.MIN_VALUE
    return plan(at, zone).firstOrNull { it.at > after && (it.at > now || (it.kind == Kind.DUE && !reminder.fired)) }
  }

  /** Nothing between 22:00 and 08:00: a nudge that would land there waits for the morning. */
  private fun awake(t: ZonedDateTime): ZonedDateTime {
    val time = t.toLocalTime()
    return when {
      time >= NIGHT_STARTS -> t.plusDays(1).with(MORNING)
      time < MORNING -> t.with(MORNING)
      else -> t
    }
  }

  private fun ZonedDateTime.millis() = toInstant().toEpochMilli()
}
