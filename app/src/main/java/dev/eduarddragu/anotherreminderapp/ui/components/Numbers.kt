package dev.eduarddragu.anotherreminderapp.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import dev.eduarddragu.anotherreminderapp.theme.Motion

// Copied from the habit tracker (ui/components/WeekStrip.kt): keep the two in step.

/**
 * A number whose changed digits roll like an odometer: the old digit leaves upwards, the new one
 * comes in from below, right to left like a carry. Clipped, never faded, so it can't look doubled.
 */
@Composable
fun RollingNumber(value: Long, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
  val digits = value.toString()
  // One value to a screen reader ("12"), not one digit per box ("1, 2").
  Row(modifier.opticalStart(digits, style).clearAndSetSemantics { contentDescription = digits }) {
    digits.forEachIndexed { index, digit ->
      val fromRight = digits.length - index
      key(fromRight) {
        AnimatedContent(
          targetState = digit,
          transitionSpec = {
            val spec = tween<IntOffset>(320, delayMillis = 40 * (fromRight - 1), easing = Motion.EaseUi)
            slideInVertically(spec) { it } togetherWith slideOutVertically(spec) { -it } using SizeTransform(clip = true) { _, _ -> tween(320, easing = Motion.EaseUi) }
          },
          modifier = Modifier.clipToBounds(),
          label = "digit",
        ) { shown ->
          Text(shown.toString(), style = style, color = color)
        }
      }
    }
  }
}

/**
 * How far each DM Sans digit's visible left edge sits from the start of its box, in ems (read from the
 * font at the weight Numerals uses). The "1" counts its stem rather than the tip of its flag.
 */
private val DIGIT_START = floatArrayOf(0.046f, 0.12f, 0.054f, 0.049f, 0.043f, 0.065f, 0.051f, 0.028f, 0.061f, 0.060f)

/** Pulls a number left by its first digit's empty side, so a big number lines up with the text under it. */
@Composable
fun Modifier.opticalStart(text: String, style: TextStyle): Modifier {
  val first = text.firstOrNull()?.takeIf { it.isDigit() } ?: return this
  val shift = with(LocalDensity.current) { (style.fontSize * DIGIT_START[first - '0']).toDp() }
  return offset(x = -shift)
}
