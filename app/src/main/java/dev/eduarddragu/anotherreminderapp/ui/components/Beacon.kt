package dev.eduarddragu.anotherreminderapp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.eduarddragu.anotherreminderapp.theme.Motion
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * Home's mark, this app's counterpart of the habit tracker's planet (which stays the tracker's own): an
 * old compass drawn in outline. An accent rim sends out a slow pulse; inside, a sparse rose with an
 * italic N spins in on arrival, then swings gently as if finding north. When a time reminder is next,
 * the rim's accent arc is what's left before it (full when one is due), and the rose fades back behind
 * the number [content] shows in the middle ([busyCentre]). Everything animated is read at draw time, on
 * its own 30 fps layer; with animations off it all stands still.
 */
@Composable
fun Beacon(fraction: Float?, due: Boolean, busyCentre: Boolean, description: String, appear: () -> Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  val colors = MaterialTheme.colorScheme
  val reduced = rememberReducedMotion()
  val target = if (due) 1f else fraction ?: 0f
  val sweep = remember { Animatable(if (reduced) target else 0f) }
  val spin = remember { Animatable(if (reduced) 0f else -200f) }
  val first = remember { mutableFloatStateOf(1f) }
  LaunchedEffect(target) {
    when {
      reduced -> sweep.snapTo(target)
      // The first time, the arc draws itself after the face has arrived; then each change eases over.
      first.floatValue == 1f -> {
        first.floatValue = 0f
        delay(200)
        sweep.animateTo(target, tween(900, easing = Motion.EaseEntrance))
      }
      else -> sweep.animateTo(target, tween(Motion.LONG, easing = Motion.EaseUi))
    }
  }
  // The rose spins in once, settling on north like a needle let go.
  LaunchedEffect(Unit) { if (!reduced) spin.animateTo(0f, tween(1_600, delayMillis = 150, easing = Motion.EaseEntrance)) }
  val loop = rememberLoop(!reduced)
  val roseAlpha = if (busyCentre) 0.22f else 1f

  val measurer = rememberTextMeasurer()
  val north = MaterialTheme.typography.titleLarge.copy(fontStyle = FontStyle.Italic, fontSize = 15.sp, color = colors.primary)
  val ink = colors.onSurface
  val muted = colors.onSurfaceVariant
  val accent = colors.primary
  Box(modifier.size(168.dp).clearAndSetSemantics { contentDescription = description }, contentAlignment = Alignment.Center) {
    Spacer(
      Modifier.size(168.dp)
        .graphicsLayer { alpha = appear() }
        .preferredFrameRate(30f)
        .drawWithCache {
          val rim = 54.dp.toPx()
          val n = measurer.measure("N", north)
          onDrawBehind {
            val c = center
            // The pulse: one ring leaving the rim and fading, every few seconds.
            val p = loop.pulse.value
            if (p >= 0f) drawCircle(accent.copy(alpha = 0.45f * (1f - p) * (1f - p)), rim + p * 22.dp.toPx(), c, style = Stroke(1.5.dp.toPx() * (1f - p * 0.5f)))
            // The rim: the accent edge, faint where the countdown has drained, solid where it's left.
            drawCircle(accent.copy(alpha = 0.35f), rim, c, style = Stroke(1.5.dp.toPx()))
            val s = sweep.value
            if (s > 0f) {
              drawArc(accent, -90f, 360f * s, false, Offset(c.x - rim, c.y - rim), Size(rim * 2, rim * 2), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
              if (s < 1f) {
                val a = Math.toRadians(-90.0 + 360.0 * s)
                drawCircle(accent, 4.5.dp.toPx(), Offset(c.x + rim * cos(a).toFloat(), c.y + rim * sin(a).toFloat()))
              }
            }
            // A bezel of fine marks, longer every quarter.
            for (i in 0 until 48) {
              val long = i % 12 == 0
              val length = (if (long) 6 else if (i % 4 == 0) 4 else 2).dp.toPx()
              rotate(i * 7.5f, c) { drawLine(muted.copy(alpha = if (long) 0.7f else 0.35f), Offset(c.x, c.y - rim + 5.dp.toPx()), Offset(c.x, c.y - rim + 5.dp.toPx() + length), 1.dp.toPx()) }
            }
            drawCircle(muted.copy(alpha = 0.25f), 41.dp.toPx(), c, style = Stroke(0.75.dp.toPx()))
            rotate(spin.value + loop.swing.value, c) { rose(c, ink, muted, accent, roseAlpha) }
            // The N rides with the rose, upright on it.
            rotate(spin.value + loop.swing.value, c) {
              // Between the north tip and the inner circle.
              drawText(n, topLeft = Offset(c.x - n.size.width / 2f, c.y - 40.dp.toPx()), alpha = roseAlpha)
            }
          }
        }
    )
    content()
  }
}

/**
 * The rose, in outline: long cardinal points and short diagonals, each a slim diamond, the north point's
 * eastern half filled in the accent as old compasses shade one side of each point.
 */
private fun DrawScope.rose(c: Offset, ink: Color, muted: Color, accent: Color, alpha: Float) {
  val outline = Stroke(1.dp.toPx())
  fun point(angle: Float, length: Float, width: Float, color: Color, shade: Color?) {
    rotate(angle, c) {
      val tip = Offset(c.x, c.y - length)
      val left = Offset(c.x - width, c.y - width)
      val right = Offset(c.x + width, c.y - width)
      if (shade != null) drawPath(Path().apply { moveTo(c.x, c.y); lineTo(tip.x, tip.y); lineTo(right.x, right.y); close() }, shade, style = Fill)
      drawPath(Path().apply { moveTo(c.x, c.y); lineTo(left.x, left.y); lineTo(tip.x, tip.y); lineTo(right.x, right.y); close() }, color, style = outline)
    }
  }
  val diagonal = muted.copy(alpha = 0.55f * alpha)
  for (a in listOf(45f, 135f, 225f, 315f)) point(a, 16.dp.toPx(), 3.dp.toPx(), diagonal, null)
  for (a in listOf(90f, 180f, 270f)) point(a, 25.dp.toPx(), 4.dp.toPx(), ink.copy(alpha = 0.75f * alpha), null)
  point(0f, 25.dp.toPx(), 4.dp.toPx(), accent.copy(alpha = alpha), accent.copy(alpha = 0.85f * alpha))
  // The pivot.
  drawCircle(accent.copy(alpha = alpha), 2.5.dp.toPx(), c)
  drawCircle(ink.copy(alpha = 0.6f * alpha), 5.dp.toPx(), c, style = Stroke(1.dp.toPx()))
}

private class Loop(val pulse: State<Float>, val swing: State<Float>)

/** The pulse (0 to 1 every 2.8 s, -1 when off) and the rose's swing (±14°, slow). */
@Composable
private fun rememberLoop(enabled: Boolean): Loop {
  if (!enabled) return remember { Loop(mutableFloatStateOf(-1f), mutableFloatStateOf(0f)) }
  val loop = rememberInfiniteTransition(label = "beacon")
  val pulse = loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2_800, easing = LinearEasing), RepeatMode.Restart, StartOffset(1_400)), label = "pulse")
  val swing = loop.animateFloat(-14f, 14f, infiniteRepeatable(tween(5_200, easing = Motion.EaseLoop), RepeatMode.Reverse, StartOffset(1_750)), label = "swing")
  return remember(pulse, swing) { Loop(pulse, swing) }
}
