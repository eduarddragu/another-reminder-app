package dev.eduarddragu.anotherreminderapp.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.eduarddragu.anotherreminderapp.ui.components.Field
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.eduarddragu.anotherreminderapp.R
import dev.eduarddragu.anotherreminderapp.data.Store
import dev.eduarddragu.anotherreminderapp.domain.Copy
import dev.eduarddragu.anotherreminderapp.domain.Countdown
import dev.eduarddragu.anotherreminderapp.domain.Data
import dev.eduarddragu.anotherreminderapp.domain.Reminder
import dev.eduarddragu.anotherreminderapp.theme.Motion
import dev.eduarddragu.anotherreminderapp.theme.NumeralsLarge
import dev.eduarddragu.anotherreminderapp.theme.fadeThrough
import dev.eduarddragu.anotherreminderapp.triggers.Notifications
import dev.eduarddragu.anotherreminderapp.ui.components.Beacon
import dev.eduarddragu.anotherreminderapp.ui.components.ButtonLabel
import dev.eduarddragu.anotherreminderapp.ui.components.CardLabel
import dev.eduarddragu.anotherreminderapp.ui.components.LineIcon
import dev.eduarddragu.anotherreminderapp.ui.components.NumberedSteps
import dev.eduarddragu.anotherreminderapp.ui.components.PressCard
import dev.eduarddragu.anotherreminderapp.ui.components.RollingNumber
import dev.eduarddragu.anotherreminderapp.ui.components.Screen
import dev.eduarddragu.anotherreminderapp.ui.components.SectionLabel
import dev.eduarddragu.anotherreminderapp.ui.components.TextAction
import dev.eduarddragu.anotherreminderapp.ui.components.cardOutline
import dev.eduarddragu.anotherreminderapp.ui.components.lineStyle
import dev.eduarddragu.anotherreminderapp.ui.components.rememberArrival
import dev.eduarddragu.anotherreminderapp.ui.components.rememberReducedMotion
import dev.eduarddragu.anotherreminderapp.ui.components.rise
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What stops reminders from reaching the phone, and the one tap that fixes it. */
data class Problem(val text: String, val action: String, val fix: () -> Unit)

/** "Due soon" on Home: a time reminder within this many hours. */
private const val SOON_HOURS = 72

/** Cards that get their own entrance step; any after them arrive with the last one. */
private const val STAGGERED_CARDS = 6

/** Home's arrival steps before the cards: the greeting, the line, the beacon, the name's reveal. */
private const val HERO_STEPS = 4

@Composable
fun HomeScreen(data: Data, problems: List<Problem>, onOpen: (Int?) -> Unit, onPlaces: () -> Unit, onOpenPlace: (Int) -> Unit) {
  val context = LocalContext.current
  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val now = rememberMinute()
  val zone = ZoneId.systemDefault()

  val due = data.reminders.filter { it.at != null && it.fired }.sortedBy { it.at }
  val upcoming = data.reminders.filter { it.at != null && !it.fired }.sortedBy { it.at }
  // In the order the places list has, which can be dragged.
  val atPlace = data.reminders.filter { it.placeId != null }.sortedBy { r -> data.places.indexOfFirst { it.id == r.placeId } }
  val next = upcoming.firstOrNull()
  val nextCountdown = next?.let { Countdown.of(now, local(it.at!!, zone)) }
  val cards = due.size + upcoming.size + atPlace.size
  val arrival =
    rememberArrival(
      HERO_STEPS + minOf(cards, STAGGERED_CARDS),
      // The greeting and the line first, then the beacon, the name sharpening over a second, and the cards.
      delayOf = { if (it < HERO_STEPS) longArrayOf(0, 70, 200, 260)[it] else 420L + Motion.STAGGER * (it - HERO_STEPS) },
      durationOf = { if (it == 3) 1000 else Motion.ENTRANCE },
    )
  fun cardStep(index: Int): Animatable<Float, AnimationVector1D>? = arrival.getOrNull(HERO_STEPS + minOf(index, STAGGERED_CARDS - 1))

  fun done(reminder: Reminder) {
    Notifications.cancel(context, reminder.id)
    // A place's notification lists everything there: it's out of date once one of them is done.
    reminder.placeId?.let { Notifications.cancel(context, it) }
    Store.update(context) { it.complete(reminder.id, Instant.now(), zone) }
    val back = Store.data.value.reminder(reminder.id)?.at
    val message = if (back != null) "Done. Back on ${local(back, zone).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))}." else "Done: ${reminder.text.trimEnd('.')}."
    scope.launch {
      snackbar.currentSnackbarData?.dismiss()
      if (snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
        // A place deleted meanwhile would leave the reminder unable to fire.
        Store.update(context) { d -> if (reminder.placeId == null || d.place(reminder.placeId) != null) d.upsert(reminder) else d }
      }
    }
  }

  fun doneAll(reminders: List<Reminder>) {
    reminders.mapNotNull { it.placeId }.distinct().forEach { Notifications.cancel(context, it) }
    Store.update(context) { d -> reminders.fold(d) { acc, r -> acc.complete(r.id, Instant.now(), zone) } }
    scope.launch {
      snackbar.currentSnackbarData?.dismiss()
      if (snackbar.showSnackbar("All done: ${Copy.placeItems(reminders.map { it.text })}", actionLabel = "Undo", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
        Store.update(context) { d -> reminders.filter { it.placeId == null || d.place(it.placeId) != null }.fold(d) { acc, r -> acc.upsert(r) } }
      }
    }
  }

  Box(Modifier.fillMaxSize()) {
    Screen(
      barWithKeyboard = false,
      bottomBar = {
        Button(onClick = { onOpen(null) }, shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { ButtonLabel(R.drawable.ic_plus, "New reminder") }
        OutlinedButton(onClick = onPlaces, shape = MaterialTheme.shapes.medium, border = cardOutline(), modifier = Modifier.heightIn(min = 48.dp)) { ButtonLabel(R.drawable.ic_pin, "Places") }
      }
    ) {
      item {
        Hero(
          now = now,
          // Only deadlines count up here: what's due, and what will be within three days. Places have none.
          tally = listOfNotNull(
            "${due.size} due".takeIf { due.isNotEmpty() },
            upcoming.count { ChronoUnit.HOURS.between(now, local(it.at!!, zone)) < SOON_HOURS }.takeIf { it > 0 }?.let { "$it due soon" },
          ).joinToString(" · ").ifEmpty { null },
          line = Copy.homeLine(due.map { it.text }, next?.text, nextCountdown, atPlace.firstNotNullOfOrNull { data.place(it.placeId)?.name }),
          arrival = arrival,
        )
      }
      item {
        val fraction = nextCountdown?.let { fractionLeft(now, local(next.at!!, zone)) }
        val description =
          when {
            due.isNotEmpty() -> "${due.size} due"
            next != null && nextCountdown != null -> "Next: ${next.text}, in ${nextCountdown.value} ${nextCountdown.unit}"
            atPlace.isNotEmpty() -> "Listening for places"
            else -> "Nothing waiting"
          }
        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
          Beacon(
            fraction = fraction,
            due = due.isNotEmpty(),
            busyCentre = due.isNotEmpty() || nextCountdown != null,
            description = description,
            appear = { arrival[2].value },
            modifier = (due.firstOrNull() ?: next)?.let { target -> Modifier.clickable(role = Role.Button, onClickLabel = "Open") { onOpen(target.id) } } ?: Modifier,
          ) {
            BeaconCenter(due.size, nextCountdown)
          }
        }
      }
      items(problems) { problem ->
        Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
          Column(Modifier.padding(20.dp)) {
            Text(problem.text, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            TextAction(problem.action, problem.fix, vertical = 0.dp)
          }
        }
      }
      if (data.reminders.isEmpty()) {
        item {
          PressCard(onClick = { onOpen(null) }, onClickLabel = "New reminder", modifier = Modifier.padding(top = 8.dp), appear = { arrival[1].value }) {
            SectionLabel("How it works", Modifier.padding(bottom = 12.dp))
            NumberedSteps(listOf("Tell it what to remember.", "Pick a place or a time.", "Forget about it."), startDelay = 520)
          }
        }
      }
      var index = 0
      section("Now", due, index) { reminder, i ->
        val since = local(reminder.at!!, zone)
        ReminderCard(
          reminder,
          icon = R.drawable.ic_clock,
          label = listOfNotNull(Copy.whenLabel(since, now.toLocalDate()), reminder.repeat?.let(Copy::repeatLabel)),
          due = true,
          trailing = { modifier -> Text("SINCE ${since.format(DateTimeFormatter.ofPattern(if (since.toLocalDate() == now.toLocalDate()) "HH:mm" else "d MMM", Locale.ENGLISH)).uppercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier) },
          appear = cardStep(i),
          onOpen = { onOpen(reminder.id) },
          onDone = { done(reminder) },
        )
      }
      index += due.size
      section("At a time", upcoming, index) { reminder, i ->
        val at = local(reminder.at!!, zone)
        ReminderCard(
          reminder,
          icon = R.drawable.ic_clock,
          label = listOfNotNull(Copy.whenLabel(at, now.toLocalDate()), reminder.repeat?.let(Copy::repeatLabel)),
          due = false,
          trailing = { modifier -> Countdown.of(now, at)?.let { CountdownValue(it, urgent = it.unit == "min", modifier) } },
          appear = cardStep(i),
          onOpen = { onOpen(reminder.id) },
          onDone = { done(reminder) },
        )
      }
      index += upcoming.size
      // One card per place, with everything to do there: the zucca and the sweetener are one trip.
      val groups = atPlace.groupBy { it.placeId!! }.toList()
      if (groups.isNotEmpty()) {
        item(key = "label-At a place") { SectionLabel("At a place", Modifier.padding(top = 24.dp, bottom = 8.dp).animateItem()) }
        itemsIndexed(groups, key = { _, (placeId, _) -> "place-$placeId" }) { i, (placeId, reminders) ->
          Box(Modifier.padding(bottom = 8.dp).animateItem(fadeInSpec = tween(Motion.SHORT, easing = Motion.EaseUi), placementSpec = tween(Motion.LIST, easing = Motion.EaseUi), fadeOutSpec = tween(Motion.FADE_OUT))) {
            PlaceCard(placeId, data.place(placeId)?.name.orEmpty(), reminders, cardStep(index + i), onDone = ::done, onAllDone = { doneAll(reminders) }, onOpenPlace = { onOpenPlace(placeId) })
          }
        }
      }
    }
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
  }
}

private fun local(millis: Long, zone: ZoneId) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()


/** How much of the countdown's own window is left: an hour, a day, or 30 days, matching its unit. */
private fun fractionLeft(now: LocalDateTime, at: LocalDateTime): Float {
  val minutes = ChronoUnit.MINUTES.between(now, at).toFloat()
  val window = when {
    minutes < 60 -> 60f
    minutes < 24 * 60 -> 24 * 60f
    else -> 30 * 24 * 60f
  }
  return (minutes / window).coerceIn(0.02f, 1f)
}

private fun LazyListScope.section(title: String, reminders: List<Reminder>, firstIndex: Int, card: @Composable (Reminder, Int) -> Unit) {
  if (reminders.isEmpty()) return
  item(key = "label-$title") { SectionLabel(title, Modifier.padding(top = 24.dp, bottom = 8.dp).animateItem()) }
  items(reminders, key = { it.id }) {
    Box(
      Modifier.padding(bottom = 8.dp)
        .animateItem(fadeInSpec = tween(Motion.SHORT, easing = Motion.EaseUi), placementSpec = tween(Motion.LIST, easing = Motion.EaseUi), fadeOutSpec = tween(Motion.FADE_OUT))
    ) {
      card(it, firstIndex + reminders.indexOf(it))
    }
  }
}

@Composable
private fun Hero(now: LocalDateTime, tally: String?, line: String, arrival: List<Animatable<Float, AnimationVector1D>>) {
  val name = stringResource(R.string.owner_name)
  Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    // Muted, so the name is the one warm thing at the top; only deadlines take the accent.
    CardLabel(lead = listOf(now.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)).uppercase()), state = tally?.uppercase(), modifier = Modifier.rise(arrival[0]))
    Spacer(Modifier.height(18.dp))
    val display = MaterialTheme.typography.displaySmall.copy(fontSize = 48.sp, lineHeight = 50.sp, letterSpacing = (-1).sp)
    Text("Don't forget,", style = display, textAlign = TextAlign.Center, modifier = Modifier.rise(arrival[0], 16.dp))
    RevealText(
      "$name.",
      style = display.copy(fontSize = 56.sp, lineHeight = 60.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium),
      color = MaterialTheme.colorScheme.primary,
      progress = { arrival[3].value },
    )
    Spacer(Modifier.height(16.dp))
    AnimatedContent(targetState = line, transitionSpec = { fadeThrough() }, modifier = Modifier.rise(arrival[1]), label = "line") { text ->
      Text(text, style = lineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 320.dp))
    }
  }
}

@Composable
private fun BeaconCenter(due: Int, countdown: Countdown?) {
  val colors = MaterialTheme.colorScheme
  AnimatedContent(targetState = Triple(due > 0, countdown != null, countdown?.unit), transitionSpec = { fadeThrough() }, label = "beacon centre") { (isDue, hasCountdown, _) ->
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      when {
        isDue -> {
          Text(due.toString(), style = NumeralsLarge, color = colors.primary)
          Text("DUE", style = MaterialTheme.typography.labelSmall, color = colors.primary)
        }
        hasCountdown && countdown != null -> {
          RollingNumber(countdown.value, NumeralsLarge, colors.onSurface)
          Text(countdown.unit.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        // Idle: nothing over the rose, the compass is the picture.
        else -> Unit
      }
    }
  }
}

/**
 * Everything to do at one place: the place's label, then its checklist, written and edited right here.
 * A tap anywhere else on the card (the label, the margins) opens the place's page; the chevron says so.
 */
@Composable
private fun PlaceCard(placeId: Int, place: String, reminders: List<Reminder>, appear: Animatable<Float, AnimationVector1D>?, onDone: (Reminder) -> Unit, onAllDone: () -> Unit, onOpenPlace: () -> Unit) {
  val focus = LocalFocusManager.current
  var fieldOpen by remember { mutableStateOf(false) }
  // While a line is being typed, a tap on the card only closes it (and keeps what was typed).
  PressCard(onClick = { if (fieldOpen) focus.clearFocus() else onOpenPlace() }, onClickLabel = "Open $place", appear = { appear?.value ?: 1f }) {
    Column(Modifier.animateContentSize(tween(Motion.LIST, easing = Motion.EaseUi))) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        LineIcon(R.drawable.ic_pin)
        Spacer(Modifier.width(10.dp))
        CardLabel(lead = listOf(place.uppercase()), trail = listOfNotNull("${reminders.size} THINGS".takeIf { reminders.size > 1 }), maxLines = 1, modifier = Modifier.weight(1f))
        LineIcon(R.drawable.ic_chevron, size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Spacer(Modifier.height(8.dp))
      PlaceChecklist(placeId, place, reminders, onDone, onAllDone, onFieldOpen = { fieldOpen = it })
    }
  }
}

/** The countdown on a card: the number rolls as it changes, the unit under the baseline in mono. */
@Composable
private fun CountdownValue(countdown: Countdown, urgent: Boolean, modifier: Modifier = Modifier) {
  val color = if (urgent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
  Row(modifier.clearAndSetSemantics { contentDescription = "In ${countdown.value} ${countdown.unit}" }) {
    RollingNumber(countdown.value, NumeralsLarge, color, Modifier.alignByBaseline())
    Spacer(Modifier.width(4.dp))
    Text(countdown.unit.uppercase(), style = MaterialTheme.typography.labelSmall, color = if (urgent) color else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alignByBaseline())
  }
}

/**
 * A reminder: icon and label, the text in the serif, then Done and what's left. Due, the card is warm.
 * Done plays first (the card warms with the Confirm haptic), then the change is saved: a one-off card
 * leaves, a repeating one cools back down while its countdown rolls to the next date.
 */
@Composable
private fun ReminderCard(reminder: Reminder, icon: Int, label: List<String>, due: Boolean, trailing: @Composable (Modifier) -> Unit, appear: Animatable<Float, AnimationVector1D>?, onOpen: () -> Unit, onDone: () -> Unit) {
  val haptics = LocalHapticFeedback.current
  val reduced = rememberReducedMotion()
  val scope = rememberCoroutineScope()
  val warmth = remember { Animatable(if (due) 1f else 0f) }
  // One Done per tap: a second tap during the animation would skip a repeating reminder's next date.
  var busy by remember { mutableStateOf(false) }
  LaunchedEffect(due) { warmth.animateTo(if (due) 1f else 0f, tween(480, easing = Motion.EaseUi)) }
  PressCard(onClick = onOpen, onClickLabel = "Edit", warmth = { warmth.value }, appear = { appear?.value ?: 1f }) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      LineIcon(icon)
      Spacer(Modifier.width(10.dp))
      // DUE first when it's there: at a large font size the end of the line is what gets cut.
      if (due) CardLabel(state = "DUE", trail = label.map { it.uppercase() }, maxLines = 1, modifier = Modifier.weight(1f))
      else CardLabel(lead = label.map { it.uppercase() }, maxLines = 1, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(14.dp))
    Text(reminder.text, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(12.dp))
    Row {
      TextAction(
        "Done",
        {
          if (busy) return@TextAction
          busy = true
          haptics.performHapticFeedback(HapticFeedbackType.Confirm)
          scope.launch {
            if (!reduced) warmth.animateTo(1f, tween(360, easing = Motion.EaseEntrance))
            onDone()
            // Still here: it repeats, and cools down while its countdown rolls to the next date.
            warmth.animateTo(0f, tween(480, easing = Motion.EaseUi))
            busy = false
          }
        },
        Modifier.alignByBaseline(),
        vertical = 0.dp,
        icon = R.drawable.ic_check,
        onClickLabel = "Done: ${reminder.text}",
      )
      Spacer(Modifier.weight(1f))
      trailing(Modifier.alignByBaseline())
    }
  }
}

/**
 * Text revealed by a soft edge sweeping left to right, while it rises and goes from a blur to sharp, as
 * the habit tracker's greeting (home/HomeScreen.kt). The padding leaves room for italic overhangs and
 * the blur, which the layer would otherwise clip.
 */
@Composable
private fun RevealText(text: String, style: TextStyle, color: Color, progress: () -> Float) {
  Text(
    text,
    style = style,
    color = color,
    textAlign = TextAlign.Center,
    modifier =
      Modifier.layout { measurable, constraints ->
          val placeable = measurable.measure(constraints)
          val room = BLUR_ROOM.roundToPx()
          layout(placeable.width, placeable.height - 2 * room) { placeable.place(0, -room) }
        }
        .graphicsLayer {
          val p = progress()
          compositingStrategy = if (p < 1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
          translationY = (1f - p) * 8.dp.toPx()
          val blur = ((1f - p) * 8.dp.toPx()).roundToInt().toFloat()
          renderEffect = if (blur >= 1f) BlurEffect(blur, blur, TileMode.Decal) else null
        }
        .drawWithContent {
          val p = progress()
          drawContent()
          if (p < 1f) {
            val feather = size.width * 0.35f
            val start = p * (size.width + feather) - feather
            drawRect(Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = start, endX = start + feather), blendMode = BlendMode.DstIn)
          }
        }
        .padding(horizontal = 12.dp, vertical = BLUR_ROOM),
  )
}

private val BLUR_ROOM = 10.dp

/**
 * The time, to the minute, while Home is on screen: countdowns and "today" follow the clock. Only
 * while the app is visible; coming back reads the clock at once.
 */
@Composable
private fun rememberMinute(): LocalDateTime {
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  val now by produceState(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)) {
    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
      while (true) {
        value = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
        delay(60_000 - System.currentTimeMillis() % 60_000 + 50)
      }
    }
  }
  return now
}
