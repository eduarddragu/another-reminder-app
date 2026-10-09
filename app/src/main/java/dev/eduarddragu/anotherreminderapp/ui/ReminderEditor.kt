package dev.eduarddragu.anotherreminderapp.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import dev.eduarddragu.anotherreminderapp.R
import dev.eduarddragu.anotherreminderapp.domain.Countdown
import dev.eduarddragu.anotherreminderapp.theme.Motion
import dev.eduarddragu.anotherreminderapp.theme.NumeralsSmall
import dev.eduarddragu.anotherreminderapp.theme.fadeThrough
import dev.eduarddragu.anotherreminderapp.ui.components.ConfirmDelete
import dev.eduarddragu.anotherreminderapp.ui.components.ConfirmDiscard
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.selection.selectableGroup
import dev.eduarddragu.anotherreminderapp.ui.components.LineIcon
import dev.eduarddragu.anotherreminderapp.ui.components.SaveButton
import dev.eduarddragu.anotherreminderapp.ui.components.pressScale
import dev.eduarddragu.anotherreminderapp.ui.components.rememberArrival
import dev.eduarddragu.anotherreminderapp.ui.components.rise
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.eduarddragu.anotherreminderapp.data.Store
import dev.eduarddragu.anotherreminderapp.domain.Data
import dev.eduarddragu.anotherreminderapp.domain.Reminder
import dev.eduarddragu.anotherreminderapp.domain.Copy
import dev.eduarddragu.anotherreminderapp.domain.Repeat
import dev.eduarddragu.anotherreminderapp.domain.RepeatUnit
import dev.eduarddragu.anotherreminderapp.domain.nextRepeat
import dev.eduarddragu.anotherreminderapp.triggers.Notifications
import dev.eduarddragu.anotherreminderapp.ui.components.Field
import dev.eduarddragu.anotherreminderapp.ui.components.ScreenTitle
import dev.eduarddragu.anotherreminderapp.ui.components.Screen
import dev.eduarddragu.anotherreminderapp.ui.components.SectionLabel
import dev.eduarddragu.anotherreminderapp.ui.components.TextAction
import dev.eduarddragu.anotherreminderapp.ui.components.cardOutline
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReminderEditor(data: Data, id: Int?, pickedPlace: Int?, onPickedConsumed: () -> Unit, onNewPlace: () -> Unit, onClose: () -> Unit) {
  val context = LocalContext.current
  val zone = ZoneId.systemDefault()
  val existing = id?.let(data::reminder)
  // Done from its notification while it's open here: nothing left to edit.
  if (id != null && existing == null) LaunchedEffect(Unit) { onClose() }
  var text by rememberSaveable { mutableStateOf(existing?.text.orEmpty()) }
  // The place is the point of this app, so a new reminder starts there.
  var atPlace by rememberSaveable { mutableStateOf(existing?.at == null) }
  var placeId by rememberSaveable { mutableStateOf(existing?.placeId ?: data.places.singleOrNull()?.id) }
  var at by rememberSaveable { mutableStateOf(existing?.at ?: LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).plusHours(2).atZone(zone).toInstant().toEpochMilli()) }
  var repeats by rememberSaveable { mutableStateOf(existing?.repeat != null) }
  var count by rememberSaveable { mutableStateOf(existing?.repeat?.count?.toString() ?: "1") }
  var unit by rememberSaveable { mutableStateOf(existing?.repeat?.unit ?: RepeatUnit.MONTHS) }
  var pickingDate by rememberSaveable { mutableStateOf(false) }
  var confirmDelete by rememberSaveable { mutableStateOf(false) }
  var pickingTime by rememberSaveable { mutableStateOf(false) }
  var confirmDiscard by rememberSaveable { mutableStateOf(false) }
  var saved by remember { mutableStateOf(false) }
  LaunchedEffect(pickedPlace) {
    if (pickedPlace != null) {
      placeId = pickedPlace
      atPlace = true
      onPickedConsumed()
    }
  }
  val dateTime = Instant.ofEpochMilli(at).atZone(zone).toLocalDateTime()
  // A reminder that already fired keeps its time when only the text changes.
  val unchangedTime = !atPlace && at == existing?.at
  val repeat = count.toIntOrNull()?.takeIf { repeats && it in 1..Repeat.MAX }?.let { Repeat(it, unit) }
  // A repeating date already gone means its next one.
  val past = !atPlace && !unchangedTime && !repeats && at <= System.currentTimeMillis()
  val valid = text.isNotBlank() && if (atPlace) data.place(placeId) != null else !past && (!repeats || repeat != null)

  // What would be saved, compared with what's there: back asks before throwing a change away.
  val dirty =
    if (existing == null) text.isNotBlank()
    else text.trim() != existing.text || atPlace != (existing.at == null) || (atPlace && placeId != existing.placeId) || (!atPlace && (at != existing.at || repeat != existing.repeat))
  BackHandler(dirty && !saved) { confirmDiscard = true }
  // A repeating date already gone this time round: when it really comes next.
  val effectiveAt = if (!atPlace && repeat != null && !unchangedTime) nextRepeat(at, repeat, Instant.now(), zone) else at
  val haptics = LocalHapticFeedback.current
  fun tick() = haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
  val arrival = rememberArrival(5)
  val place = data.place(placeId)

  Screen(
    bottomBar = {
      if (existing != null) {
        TextAction("Delete", { confirmDelete = true }, color = MaterialTheme.colorScheme.onSurfaceVariant, icon = R.drawable.ic_trash)
      }
      SaveButton(
        enabled = valid,
        onClick = {
          // Once: the screen is still tappable while it slides away, and a second tap would save a twin.
          if (saved) return@SaveButton
          saved = true
          Store.update(context) { d -> d.upsert(Reminder(id ?: d.newId(), text.trim(), placeId.takeIf { atPlace }, effectiveAt.takeIf { !atPlace }, fired = unchangedTime && existing.fired, repeat = repeat.takeIf { !atPlace }, lastNudge = existing?.lastNudge.takeIf { unchangedTime })) }
          onClose()
        },
        modifier = Modifier.weight(1f),
      )
    }
  ) {
    item {
      Column(Modifier.padding(bottom = 24.dp).rise(arrival[0], 16.dp)) {
        SectionLabel(if (existing == null) "New reminder" else "Reminder")
        Spacer(Modifier.height(8.dp))
        AnimatedContent(atPlace, transitionSpec = { fadeThrough() }, label = "title") { place ->
          Text(if (place) "What, and where?" else "What, and when?", style = MaterialTheme.typography.displaySmall)
        }
      }
    }
    item { Field(text, { text = it }, "Remember to…", Modifier.fillMaxWidth().rise(arrival[1]), minLines = 2, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)) }
    item {
      Row(Modifier.padding(top = 24.dp).rise(arrival[2]).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KindTile(atPlace, R.drawable.ic_pin, "At a place", "Every visit", Modifier.weight(1f)) { tick(); atPlace = true }
        KindTile(!atPlace, R.drawable.ic_clock, "At a time", "Once or repeating", Modifier.weight(1f)) { tick(); atPlace = false }
      }
    }
    item {
      AnimatedContent(
        atPlace,
        transitionSpec = { fadeThrough() using SizeTransform(clip = false) { _, _ -> tween(Motion.LIST, easing = Motion.EaseUi) } },
        modifier = Modifier.rise(arrival[3]),
        label = "kind",
      ) { isPlace ->
        Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          if (isPlace) {
            SectionLabel("Where")
            if (data.places.isEmpty()) Text("No places yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              data.places.forEach { p -> FilterChip(p.id == placeId, { tick(); placeId = p.id }, { Text(p.name) }, shape = MaterialTheme.shapes.medium) }
            }
            if (place == null && data.places.isNotEmpty()) Text("Pick one.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            TextAction("New place", onNewPlace, icon = R.drawable.ic_plus)
          } else {
            SectionLabel("When")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              // The year only when it isn't this one: "Tue 1 Jun 2027".
              ValueTile("Date", dateTime.format(DateTimeFormatter.ofPattern(if (dateTime.year == LocalDate.now().year) "EEE d MMM" else "EEE d MMM yyyy", Locale.ENGLISH)), NumeralsSmall, Modifier.weight(1.4f)) { pickingDate = true }
              ValueTile("Time", dateTime.format(DateTimeFormatter.ofPattern("HH:mm")), NumeralsSmall, Modifier.weight(1f)) { pickingTime = true }
            }
            val next = Instant.ofEpochMilli(effectiveAt).atZone(zone).toLocalDateTime()
            val countdown = Countdown.of(LocalDateTime.now(), next)
            Text(
              when {
                past -> "That's in the past."
                countdown == null -> "Now."
                // Set in the past but repeating: say when it really comes.
                next != dateTime -> "Next: ${next.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))}, in ${countdown.value} ${countdown.unit}."
                else -> "In ${countdown.value} ${countdown.unit}."
              },
              style = MaterialTheme.typography.bodyMedium,
              color = if (past) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            SectionLabel("Repeat")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilterChip(!repeats, { tick(); repeats = false }, { Text("Once") }, shape = MaterialTheme.shapes.medium)
              FilterChip(repeats, { tick(); repeats = true }, { Text("Every…") }, shape = MaterialTheme.shapes.medium)
            }
            AnimatedVisibility(
              repeats,
              enter = fadeIn(tween(Motion.LIST, easing = Motion.EaseUi)) + expandVertically(tween(Motion.LIST, easing = Motion.EaseUi)),
              exit = fadeOut(tween(Motion.FADE_OUT)) + shrinkVertically(tween(Motion.EXIT, easing = Motion.EaseUi)),
            ) {
              // "Every [2] [weeks]" on one line, read as it's said.
              Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(
                  count,
                  { count = it.filter(Char::isDigit).take(3) },
                  "Every",
                  Modifier.width(96.dp),
                  singleLine = true,
                  isError = repeat == null,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  supportingText = if (repeat == null) ({ Text("1 to ${Repeat.MAX}") }) else null,
                )
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  RepeatUnit.entries.forEach { u ->
                    val word = u.name.lowercase().let { if (count == "1") it.removeSuffix("s") else it }
                    FilterChip(u == unit, { tick(); unit = u }, { Text(word) }, shape = MaterialTheme.shapes.medium)
                  }
                }
              }
            }
          }
        }
      }
    }
    item {
      // What the phone will say, live: the copy is the best part of the app.
      // Seeded as the real notification is, so this is what the phone would say today.
      val title = if (atPlace) Copy.placeTitle(place?.name ?: "the shop", Copy.placeSeed(place?.id ?: 0)) else Copy.timeTitle(Copy.timeSeed(id ?: data.newId()), repeat)
      Card(Modifier.fillMaxWidth().padding(top = 24.dp).rise(arrival[4]), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, contentColor = MaterialTheme.colorScheme.onSurface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          SectionLabel("Preview")
          // Wraps: the whole line is the point, and it's longer than one row on a phone.
          Row(verticalAlignment = Alignment.Top) {
            LineIcon(if (atPlace) R.drawable.ic_pin else R.drawable.ic_clock, Modifier.padding(top = 3.dp), size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
          }
          Text(text.ifBlank { "Remember to…" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }

  if (confirmDelete && existing != null) {
    ConfirmDelete(
      title = "Delete this reminder?",
      text = "\u201c${existing.text}\u201d goes for good. No undo.",
      onYes = {
        confirmDelete = false
        Notifications.cancel(context, existing.id)
        Store.update(context) { it.removeReminder(existing.id) }
        onClose()
      },
      onNo = { confirmDelete = false },
    )
  }
  if (confirmDiscard) ConfirmDiscard(onDiscard = { confirmDiscard = false; onClose() }, onKeep = { confirmDiscard = false })
  if (pickingDate) {
    // The date picker speaks UTC midnight.
    val state = rememberDatePickerState(initialSelectedDateMillis = dateTime.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
      onDismissRequest = { pickingDate = false },
      confirmButton = {
        TextButton({
          state.selectedDateMillis?.let { at = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().atTime(dateTime.toLocalTime()).atZone(zone).toInstant().toEpochMilli() }
          pickingDate = false
        }) { Text("OK") }
      },
      dismissButton = { TextButton({ pickingDate = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
      shape = MaterialTheme.shapes.extraLarge,
    ) { DatePicker(state) }
  }
  if (pickingTime) {
    val state = rememberTimePickerState(dateTime.hour, dateTime.minute, is24Hour = true)
    AlertDialog(
      onDismissRequest = { pickingTime = false },
      confirmButton = {
        TextButton({
          at = dateTime.toLocalDate().atTime(LocalTime.of(state.hour, state.minute)).atZone(zone).toInstant().toEpochMilli()
          pickingTime = false
        }) { Text("OK") }
      },
      dismissButton = { TextButton({ pickingTime = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
      text = { TimePicker(state) },
      shape = MaterialTheme.shapes.extraLarge,
    )
  }
}

/** One of the two kinds of reminder: a tile with its icon, warm with an accent edge when chosen. */
@Composable
private fun KindTile(selected: Boolean, icon: Int, title: String, caption: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  val interaction = remember { MutableInteractionSource() }
  val press = pressScale(interaction)
  val fill by animateColorAsState(if (selected) colors.primaryContainer else colors.background, tween(Motion.SHORT, easing = Motion.EaseUi), label = "tile")
  val edge by animateColorAsState(if (selected) colors.primary else colors.outline, tween(Motion.SHORT, easing = Motion.EaseUi), label = "tile edge")
  Column(
    modifier
      .graphicsLayer {
        scaleX = press.value
        scaleY = press.value
      }
      .clip(MaterialTheme.shapes.medium)
      .background(fill)
      .border(1.dp, edge, MaterialTheme.shapes.medium)
      .selectable(selected, interaction, indication = null, role = Role.RadioButton, onClick = onClick)
      .padding(16.dp)
  ) {
    LineIcon(icon, size = 24.dp, tint = if (selected) colors.primary else colors.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(caption.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
  }
}

/** A value you tap to change (the date, the time): a mono caption over it. */
@Composable
private fun ValueTile(caption: String, value: String, style: TextStyle, modifier: Modifier = Modifier, onClick: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  val press = pressScale(interaction)
  Column(
    modifier
      .graphicsLayer {
        scaleX = press.value
        scaleY = press.value
      }
      .clip(MaterialTheme.shapes.medium)
      .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
      .clickable(interaction, indication = null, onClickLabel = "Change ${caption.lowercase()}", role = Role.Button, onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 12.dp)
  ) {
    Text(caption.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    Text(value, style = style, maxLines = 2, overflow = TextOverflow.Ellipsis)
  }
}
