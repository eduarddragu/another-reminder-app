package dev.eduarddragu.anotherreminderapp.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.eduarddragu.anotherreminderapp.R
import dev.eduarddragu.anotherreminderapp.data.Store
import dev.eduarddragu.anotherreminderapp.domain.Checklist
import dev.eduarddragu.anotherreminderapp.domain.Reminder
import dev.eduarddragu.anotherreminderapp.triggers.Notifications
import dev.eduarddragu.anotherreminderapp.ui.components.TextAction
import dev.eduarddragu.anotherreminderapp.ui.components.touchTarget

/**
 * What to do at one place, written and edited where it's shown (Home's card, the place's page), never in
 * a separate editor: each thing with a round check; a tap on its text makes it editable in place (Enter
 * saves, emptied it goes); "Add" opens a line that adds one thing per Enter. A field closes when it loses
 * focus (a tap elsewhere) or the keyboard goes down, keeping what was typed.
 */
@Composable
fun PlaceChecklist(placeId: Int, placeName: String, reminders: List<Reminder>, onDone: (Reminder) -> Unit, onAllDone: (() -> Unit)?, onFieldOpen: (Boolean) -> Unit = {}) {
  val context = LocalContext.current
  val haptics = LocalHapticFeedback.current
  val colors = MaterialTheme.colorScheme
  var adding by rememberSaveable { mutableStateOf(false) }
  var editing by rememberSaveable { mutableStateOf<Int?>(null) }
  var draft by rememberSaveable { mutableStateOf("") }
  // Told to the card, so a tap on it while a field is open only closes the field.
  LaunchedEffect(adding, editing) { onFieldOpen(adding || editing != null) }

  fun add() {
    val text = draft.trim()
    if (text.isEmpty()) {
      adding = false
      return
    }
    Store.update(context) { d -> d.upsert(Reminder(d.newId(), text, placeId = placeId)) }
    draft = ""
  }
  fun save(reminder: Reminder) {
    val edit = Checklist.close(open = editing == reminder.id, old = reminder.text, typed = draft)
    val text = draft.trim()
    if (editing == reminder.id) {
      editing = null
      draft = ""
    }
    when (edit) {
      Checklist.Edit.KEEP -> Unit
      // Emptied: it goes, as a check would.
      Checklist.Edit.REMOVE -> onDone(reminder)
      Checklist.Edit.RENAME -> {
        // Its notification would show the old text.
        Notifications.cancel(context, placeId)
        Store.update(context) { it.upsert(reminder.copy(text = text)) }
      }
    }
  }

  Column {
    reminders.forEach { reminder ->
      // Circle and text share the header's columns (the 22dp pin, then 10dp), centred on the first line.
      // The whole line edits the thing (only the circle checks it): no aiming at the words.
      Row(
        Modifier.fillMaxWidth()
          .clip(MaterialTheme.shapes.small)
          .clickable(enabled = editing != reminder.id, onClickLabel = "Edit ${reminder.text}", role = Role.Button) {
            // Whatever was being typed elsewhere in the list is kept, not dropped.
            editing?.let { open -> reminders.firstOrNull { it.id == open }?.let(::save) }
            if (adding) {
              add()
              adding = false
            }
            editing = reminder.id
            draft = reminder.text
          }
          .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
      ) {
        Box(Modifier.width(22.dp).padding(top = 2.dp), contentAlignment = Alignment.Center) {
          Box(
            Modifier.size(20.dp)
              .touchTarget(
                Modifier.clickable(onClickLabel = "Done: ${reminder.text}", role = Role.Checkbox) {
                  haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                  onDone(reminder)
                }
              )
              .border(1.5.dp, colors.primary, CircleShape)
          )
        }
        Spacer(Modifier.width(10.dp))
        if (editing == reminder.id) {
          InlineField(draft, { draft = it }, "Emptied, it goes", onSubmit = { save(reminder) }, onClose = { save(reminder) }, Modifier.weight(1f))
        } else {
          Text(
            reminder.text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
    if (adding) {
      Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        // Where the next check will be.
        Box(Modifier.width(22.dp).padding(top = 2.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(20.dp).border(1.5.dp, colors.outline, CircleShape)) }
        Spacer(Modifier.width(10.dp))
        InlineField(draft, { draft = it }, "Another thing", onSubmit = ::add, onClose = { add(); adding = false }, Modifier.weight(1f))
      }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth()) {
      if (!adding) {
        TextAction("Add", {
          editing?.let { open -> reminders.firstOrNull { it.id == open }?.let(::save) }
          draft = ""
          adding = true
        }, Modifier.alignByBaseline(), vertical = 0.dp, icon = R.drawable.ic_plus, onClickLabel = "Add something at $placeName")
      }
      Spacer(Modifier.weight(1f))
      if (onAllDone != null && reminders.size > 1 && !adding && editing == null) {
        TextAction("All done", {
          haptics.performHapticFeedback(HapticFeedbackType.Confirm)
          onAllDone()
        }, Modifier.alignByBaseline(), vertical = 0.dp, icon = R.drawable.ic_check)
      }
    }
  }
}

/**
 * Text typed straight into the list: the list's own text style, an accent cursor and a hairline under it,
 * focused as soon as it appears. [onClose] runs once it loses focus or the keyboard is put away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineField(value: String, onValueChange: (String) -> Unit, placeholder: String, onSubmit: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  val focus = remember { FocusRequester() }
  var focused by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { focus.requestFocus() }
  // Back with the keyboard up only hides the keyboard; the field shouldn't linger after it.
  val ime = WindowInsets.isImeVisible
  var imeSeen by remember { mutableStateOf(false) }
  // Into view with the card's footer under it, not flush against the keyboard.
  val view = remember { BringIntoViewRequester() }
  var height by remember { mutableStateOf(0) }
  val room = with(LocalDensity.current) { 72.dp.toPx() }
  LaunchedEffect(ime) {
    if (ime) {
      imeSeen = true
      delay(120)
      view.bringIntoView(Rect(0f, 0f, 1f, height + room))
    } else if (imeSeen) onClose()
  }
  val style = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface)
  BasicTextField(
    value,
    onValueChange,
    modifier
      .bringIntoViewRequester(view)
      .onSizeChanged { height = it.height }
      .focusRequester(focus)
      .onFocusChanged {
        if (it.isFocused) focused = true else if (focused) {
          focused = false
          onClose()
        }
      }
      // No padding of its own, so a row doesn't jump when it turns into a field; the hairline sits just below.
      .drawBehind { drawLine(colors.primary, Offset(0f, size.height + 2.dp.toPx()), Offset(size.width, size.height + 2.dp.toPx()), 1.dp.toPx()) },
    textStyle = style,
    singleLine = true,
    cursorBrush = SolidColor(colors.primary),
    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
    decorationBox = { inner ->
      Box {
        if (value.isEmpty()) Text(placeholder, style = style.copy(color = colors.onSurfaceVariant.copy(alpha = 0.7f)))
        inner()
      }
    },
  )
}
