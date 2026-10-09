package dev.eduarddragu.anotherreminderapp.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** What the notifications and labels say. */
object Copy {
  // Warm, never bossy: a note from past you, handed over at the right moment.
  private val AT_PLACE =
    listOf(
      "You're at %s. Past you left you this:",
      "Hello from %s. You wanted to remember:",
      "Nice timing. At %s you meant to:",
      "While you're at %s, a small favour to yourself:",
      "You made it to %s. Here's your note:",
      "%s, at last. Glad I caught you:",
    )
  private val AT_TIME =
    listOf(
      "Right on time. You asked me to say:",
      "A kind nudge from past you:",
      "Here's the reminder you set for yourself:",
      "Future you will thank you for this one:",
      "Gentle reminder, as promised:",
    )
  private val AGAIN = listOf("Back again, as agreed:", "Your regular reminder:", "Here it is again, right on cue:")
  private val YEARLY = listOf("It's that time of the year again:", "A whole year already. Your yearly note:")

  /**
   * [seed] picks the line: the reminder's id plus the day, so the same reminder opens differently from one
   * visit (or one year) to the next, but never changes between two notifications of the same day.
   */
  // Some lines open with the place: a sentence starts with a capital whatever the place is called.
  fun placeTitle(place: String, seed: Int): String = pick(AT_PLACE, seed).format(place).replaceFirstChar { it.uppercase() }

  fun timeTitle(seed: Int, repeat: Repeat? = null): String =
    when {
      repeat == null -> pick(AT_TIME, seed)
      repeat.unit == RepeatUnit.YEARS && repeat.count == 1 -> pick(YEARLY, seed)
      else -> pick(AGAIN, seed)
    }

  private val WEEK_BEFORE = listOf("One week to go, just so you know:", "Next week, this one. Plenty of time:")
  private val DAY_BEFORE = listOf("Tomorrow, this one. Get ready:", "Heads up for tomorrow:", "A little preview of tomorrow:")
  private val LATER_TODAY = listOf("Still on your list:", "A gentle second nudge:", "In case it slipped by:")
  private val YESTERDAY = listOf("This was for yesterday. Still on?", "Yesterday's, still waiting for you:")

  /** The title for each notification of a time reminder's sequence (see [Nudges]). */
  fun nudgeTitle(nudge: Nudges.Nudge, seed: Int, repeat: Repeat?): String =
    when (nudge.kind) {
      Nudges.Kind.WEEK_BEFORE -> pick(WEEK_BEFORE, seed)
      Nudges.Kind.DAY_BEFORE -> pick(DAY_BEFORE, seed)
      Nudges.Kind.DUE -> timeTitle(seed, repeat)
      Nudges.Kind.LATE ->
        when (nudge.daysLate) {
          0 -> pick(LATER_TODAY, seed)
          1 -> pick(YESTERDAY, seed)
          else -> "Waiting for you for ${nudge.daysLate} days now:"
        }
    }

  /** A place's notification text: the one thing, or all of them in a line. */
  fun placeItems(texts: List<String>): String = if (texts.size == 1) texts[0] else texts.joinToString(", ") { it.trimEnd('.') } + "."

  /** One line a day per place, so three reminders at the same shop open the same way together. */
  fun placeSeed(placeId: Int, day: LocalDate = LocalDate.now()): Int = placeId + day.toEpochDay().toInt()

  fun timeSeed(reminderId: Int, day: LocalDate = LocalDate.now()): Int = reminderId + day.toEpochDay().toInt()

  private fun pick(lines: List<String>, seed: Int) = lines[Math.floorMod(seed, lines.size)]

  /**
   * Home's line under the greeting: what's due, else the next time reminder, else the place that is
   * listening, else the empty state.
   */
  fun homeLine(due: List<String>, next: String?, countdown: Countdown?, listeningAt: String?): String =
    when {
      due.size == 1 -> "${due[0].trimEnd('.')}. It's due."
      due.size > 1 -> "${due.size} things are due."
      next != null && countdown != null -> "${next.trimEnd('.')}, in ${countdown.value} ${countdown.unit}."
      listeningAt != null -> "Waiting for you at $listeningAt."
      else -> "Nothing to remember. Suspicious."
    }

  private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
  private val DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
  private val DATE_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

  /** "Every day", "Every 2 weeks", "Every year". */
  fun repeatLabel(repeat: Repeat): String {
    val unit = repeat.unit.name.lowercase()
    return if (repeat.count == 1) "Every ${unit.removeSuffix("s")}" else "Every ${repeat.count} $unit"
  }

  /** "TODAY · 18:30", "TOMORROW · 09:00", "FRI 10 OCT · 18:30", "1 JUN 2027 · 09:00" (labels are mono capitals). */
  fun whenLabel(at: LocalDateTime, today: LocalDate): String {
    val day =
      when (at.toLocalDate()) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> at.format(if (at.year == today.year) DATE else DATE_YEAR)
      }
    return "$day · ${at.format(TIME)}".uppercase(Locale.ENGLISH)
  }
}

/** How long until a time reminder: hours or minutes within a day, then calendar days. Null once it's due. */
data class Countdown(val value: Long, val unit: String) {
  companion object {
    fun of(now: LocalDateTime, at: LocalDateTime): Countdown? {
      if (!at.isAfter(now)) return null
      val minutes = ChronoUnit.MINUTES.between(now, at)
      return when {
        minutes >= 24 * 60 -> ChronoUnit.DAYS.between(now.toLocalDate(), at.toLocalDate()).let { Countdown(it, if (it == 1L) "day" else "days") }
        minutes >= 60 -> (minutes / 60).let { Countdown(it, if (it == 1L) "hour" else "hours") }
        else -> Countdown(maxOf(minutes, 1), "min")
      }
    }
  }
}
