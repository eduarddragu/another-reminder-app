package dev.eduarddragu.anotherreminderapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.annotation.DrawableRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import dev.eduarddragu.anotherreminderapp.R
import dev.eduarddragu.anotherreminderapp.theme.danger
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.eduarddragu.anotherreminderapp.theme.Motion
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.VerticalAlignmentLine
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/** The heading of a section on every screen: mono capitals in the accent. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) =
  Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = modifier)

/**
 * Every text field: Material's outlined field on the controls' radius (12dp; Material would round it
 * to 4), with a plain text label.
 */
@Composable
fun Field(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
  singleLine: Boolean = false,
  minLines: Int = 1,
  isError: Boolean = false,
  suffix: (@Composable () -> Unit)? = null,
  supportingText: (@Composable () -> Unit)? = null,
) =
  OutlinedTextField(
    value,
    onValueChange,
    modifier,
    label = { Text(label) },
    suffix = suffix,
    supportingText = supportingText,
    isError = isError,
    keyboardOptions = keyboardOptions,
    keyboardActions = keyboardActions,
    singleLine = singleLine,
    minLines = minLines,
    shape = MaterialTheme.shapes.medium,
  )

/**
 * The outline for outlined buttons: one step stronger than Material's default, which nearly vanishes
 * on a tonal card.
 */
@Composable
fun cardOutline(): BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)

/**
 * A text action at the edge of a row or on its own line: accent text with no button box, so it sits
 * exactly on the gutter (a TextButton centres short labels in a 58dp minimum width) and doesn't make
 * its line taller than the text beside it. [vertical] padding is part of its layout; the touch area
 * grows to 48dp either way without moving anything (see [touchTarget]). [color] is the accent; a way
 * out that shouldn't invite a tap can be muted.
 */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, vertical: Dp = 12.dp, color: Color = MaterialTheme.colorScheme.primary, @DrawableRes icon: Int? = null, onClickLabel: String? = null) {
  val shown = if (enabled) color else color.copy(alpha = 0.38f)
  Row(
    modifier.touchTarget(Modifier.clickable(enabled = enabled, onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)).padding(vertical = vertical),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) {
      LineIcon(icon, size = 18.dp, tint = shown)
      Spacer(Modifier.width(6.dp))
    }
    Text(text, style = MaterialTheme.typography.labelLarge, color = shown)
  }
}

/** A button's content: its line icon, then the label. */
@Composable
fun ButtonLabel(@DrawableRes icon: Int, text: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    LineIcon(icon, size = 18.dp, tint = LocalContentColor.current)
    Spacer(Modifier.width(8.dp))
    Text(text)
  }
}

/** Asks before deleting: what goes, then "No" (muted, the safe way out) and "Yes, delete" in red. */
@Composable
fun ConfirmDelete(title: String, text: String, onYes: () -> Unit, onNo: () -> Unit) =
  Confirm(R.drawable.ic_trash, title, text, yes = "Yes, delete", no = "No", onYes = onYes, onNo = onNo)

/** Back out of an editor with unsaved changes. */
@Composable
fun ConfirmDiscard(onDiscard: () -> Unit, onKeep: () -> Unit) =
  Confirm(R.drawable.ic_trash, "Discard changes?", "What you changed here goes. Nothing else does.", yes = "Discard", no = "Keep editing", onYes = onDiscard, onNo = onKeep)

@Composable
private fun Confirm(@DrawableRes icon: Int, title: String, text: String, yes: String, no: String, onYes: () -> Unit, onNo: () -> Unit) {
  AlertDialog(
    onDismissRequest = onNo,
    icon = { LineIcon(icon, size = 24.dp, tint = danger) },
    title = { Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center) },
    text = { Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
    confirmButton = { TextButton(onYes) { Text(yes, color = danger) } },
    dismissButton = { TextButton(onNo) { Text(no, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
    shape = MaterialTheme.shapes.extraLarge,
  )
}

/**
 * Applies [clickable] (a click, a selection) on an area at least [size] each way, while the layout
 * keeps the content's own size: baselines, flush edges and line heights stay where they were, and the
 * extra area overlaps the neighbours. Unlike Compose's own touch slop, the grown area wins over a
 * clickable parent (a tap just beside "Keep going today" doesn't open the row). Intrinsic sizes are the
 * content's, so rows measured by intrinsics don't grow either.
 */
fun Modifier.touchTarget(clickable: Modifier, size: Dp = 48.dp): Modifier = this then TouchArea(size, grow = false) then clickable then TouchArea(size, grow = true)

// Where the content sits inside the grown area: the inner half reports it, the outer half reads it
// (alignment lines travel with each measurement, so nothing is shared between passes).
private val ContentLeft = VerticalAlignmentLine(::min)
private val ContentRight = VerticalAlignmentLine(::max)
private val ContentTop = HorizontalAlignmentLine(::min)
private val ContentBottom = HorizontalAlignmentLine(::max)

private data class TouchArea(val size: Dp, val grow: Boolean) : ModifierNodeElement<TouchAreaNode>() {
  override fun create() = TouchAreaNode(size, grow)

  override fun update(node: TouchAreaNode) {
    node.size = size
    node.grow = grow
  }
}

/**
 * [grow]: the inner half, which centres the content in at least [size] each way. Otherwise the outer
 * half, which lays out only the content's bounds and lets the rest overlap.
 */
private class TouchAreaNode(var size: Dp, var grow: Boolean) : Modifier.Node(), LayoutModifierNode {
  override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
    val target = size.roundToPx()
    // Room for the inner half to grow past the outer half's constraints.
    val slack = 2 * target
    if (grow) {
      // The outer half's own constraints again: the content measures as if nothing were around it.
      val own =
        Constraints(
          minWidth = constraints.minWidth,
          maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth - slack else Constraints.Infinity,
          minHeight = constraints.minHeight,
          maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight - slack else Constraints.Infinity,
        )
      val content = measurable.measure(own)
      val width = maxOf(content.width, target)
      val height = maxOf(content.height, target)
      val x = (width - content.width) / 2
      val y = (height - content.height) / 2
      val lines = mapOf(ContentLeft to x, ContentRight to x + content.width, ContentTop to y, ContentBottom to y + content.height)
      return layout(width, height, lines) { content.place(x, y) }
    }
    val loose =
      constraints.copy(
        maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + slack else Constraints.Infinity,
        maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + slack else Constraints.Infinity,
      )
    val grown = measurable.measure(loose)
    val left = grown[ContentLeft]
    val top = grown[ContentTop]
    if (left == AlignmentLine.Unspecified || top == AlignmentLine.Unspecified) return layout(grown.width, grown.height) { grown.place(0, 0) }
    return layout(grown[ContentRight] - left, grown[ContentBottom] - top) { grown.place(-left, -top) }
  }

  override fun IntrinsicMeasureScope.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int) = measurable.minIntrinsicWidth(height)

  override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int) = measurable.maxIntrinsicWidth(height)

  override fun IntrinsicMeasureScope.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int) = measurable.minIntrinsicHeight(width)

  override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int) = measurable.maxIntrinsicHeight(width)
}

/**
 * A card's mono label: muted parts joined by " · ", with only [state] (done, new, a review, now) in
 * the accent. The parts are given in place, so a habit named like a state word stays muted.
 */
@Composable
fun CardLabel(modifier: Modifier = Modifier, lead: List<String> = emptyList(), state: String? = null, trail: List<String> = emptyList(), maxLines: Int = Int.MAX_VALUE) {
  val colors = MaterialTheme.colorScheme
  Text(
    buildAnnotatedString {
      var first = true
      fun separate() {
        if (!first) append(" · ")
        first = false
      }
      lead.forEach {
        separate()
        append(it)
      }
      if (state != null) {
        separate()
        withStyle(SpanStyle(color = colors.primary)) { append(state) }
      }
      trail.forEach {
        separate()
        append(it)
      }
    },
    style = MaterialTheme.typography.labelMedium,
    color = colors.onSurfaceVariant,
    maxLines = maxLines,
    overflow = TextOverflow.Ellipsis,
    modifier = modifier,
  )
}


/** "01 ..." rows (how it works), arriving one at a time from [startDelay]. */
@Composable
fun NumberedSteps(items: List<String>, startDelay: Long) {
  val arrival = rememberArrival(items.size, delayOf = { startDelay + Motion.STAGGER * it.toLong() }, durationOf = { Motion.ENTRANCE })
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    items.forEachIndexed { index, item ->
      Row(Modifier.rise(arrival[index], 8.dp)) {
        Text("%02d".format(index + 1), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(32.dp).padding(top = 3.dp))
        Text(item, style = MaterialTheme.typography.bodyLarge)
      }
    }
  }
}

/** The serif italic line under a title (Home's "Car tax, in 3 days."), as the habit tracker's. */
@Composable
fun lineStyle(): TextStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium)

/** A line icon (res/drawable/ic_*.xml, 24dp, 1.75 stroke). */
@Composable
fun LineIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier, size: Dp = 22.dp, tint: Color = MaterialTheme.colorScheme.primary) =
  Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = modifier.size(size))

/**
 * Every card: a tonal surface that dips when pressed (settling back critically damped), can warm up
 * to the accent container ([warmth], 0 to 1), and arrives with [appear] (0 to 1, rising 24dp). All
 * three are read at draw time, so they never recompose the card.
 */
@Composable
fun PressCard(onClick: (() -> Unit)?, onClickLabel: String?, modifier: Modifier = Modifier, warmth: () -> Float = { 0f }, appear: () -> Float = { 1f }, content: @Composable ColumnScope.() -> Unit) {
  val colors = MaterialTheme.colorScheme
  val interaction = remember { MutableInteractionSource() }
  val press = pressScale(interaction)
  val open = colors.surfaceContainerLow
  val warm = colors.primaryContainer
  Column(
    modifier
      .fillMaxWidth()
      .graphicsLayer {
        val p = appear()
        alpha = p
        translationY = (1f - p) * 24.dp.toPx()
        scaleX = press.value
        scaleY = press.value
      }
      .clip(MaterialTheme.shapes.large)
      .drawBehind { drawRect(lerp(open, warm, warmth())) }
      // Without [onClick] it's the same card, only not pressable (a list that is edited in place).
      .then(if (onClick != null) Modifier.clickable(interaction, indication = null, onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else Modifier)
      .padding(20.dp),
    content = content,
  )
}

/**
 * Save at the bottom of an editor: stays warm while it can't save yet (only its label dims), as the
 * habit tracker's log form, so the bar never turns into a grey slab.
 */
@Composable
fun SaveButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, label: String = "Save") {
  val colors = MaterialTheme.colorScheme
  Button(
    onClick = onClick,
    enabled = enabled,
    shape = MaterialTheme.shapes.medium,
    colors = ButtonDefaults.buttonColors(disabledContainerColor = colors.primary, disabledContentColor = colors.onPrimary.copy(alpha = 0.5f)),
    modifier = modifier.heightIn(min = 48.dp),
  ) { ButtonLabel(R.drawable.ic_check, label) }
}
