package dev.eduarddragu.anotherreminderapp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.eduarddragu.anotherreminderapp.data.Tiles
import dev.eduarddragu.anotherreminderapp.domain.Mercator
import dev.eduarddragu.anotherreminderapp.domain.Spot
import dev.eduarddragu.anotherreminderapp.theme.Motion
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A place on the map: OpenStreetMap tiles in grey, toned to the palette (inverted at night), with each
 * spot as an accent dot inside its radius, framed to fit them all. A radius change eases the rings over.
 */
@Composable
fun SpotMap(spots: List<Spot>, radius: Int, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val colors = MaterialTheme.colorScheme
  val dark = isSystemInDarkTheme()
  val metres by animateFloatAsState(radius.toFloat(), tween(Motion.LONG, easing = Motion.EaseUi), label = "radius")
  // OSM's tiles are drawn for about 1x screens: a bit over half the density keeps the street names readable.
  val tilePx = 256f * LocalDensity.current.density * 0.6f
  val filter = remember(dark) { ColorFilter.colorMatrix(if (dark) INVERTED_GREY else GREY) }
  // Clipped: tiles cover the whole visible range and would otherwise spill past the box.
  BoxWithConstraints(modifier.clipToBounds()) {
    if (spots.isEmpty()) return@BoxWithConstraints
    val width = constraints.maxWidth.toFloat()
    val height = constraints.maxHeight.toFloat()
    // The frame is fitted to the largest radius, so switching between them doesn't jump the zoom.
    val frame = remember(spots, width, height) { Mercator.fit(spots, 300, width / tilePx.toDouble(), height / tilePx.toDouble()) }
    val first = IntOffset(floor(frame.x - width / tilePx / 2).toInt(), floor(frame.y - height / tilePx / 2).toInt())
    val last = IntOffset(floor(frame.x + width / tilePx / 2).toInt(), floor(frame.y + height / tilePx / 2).toInt())
    // Keyed by zoom too: tiles of the previous zoom would sit in the wrong place until the new ones arrive.
    val loadedAt by produceState(frame.zoom to emptyMap<IntOffset, ImageBitmap>(), frame.zoom, first, last) {
      val loaded = (if (value.first == frame.zoom) value.second else emptyMap()).toMutableMap()
      value = frame.zoom to loaded.toMap()
      val count = 1 shl frame.zoom
      for (ty in first.y..last.y) for (tx in first.x..last.x) {
        if (ty !in 0 until count || IntOffset(tx, ty) in loaded) continue
        Tiles.load(context, frame.zoom, Math.floorMod(tx, count), ty)?.let {
          loaded[IntOffset(tx, ty)] = it
          value = frame.zoom to loaded.toMap()
        }
      }
    }
    val tiles = if (loadedAt.first == frame.zoom) loadedAt.second else emptyMap()
    Canvas(Modifier.fillMaxSize()) {
      val origin = Offset((size.width / 2 - frame.x * tilePx).toFloat(), (size.height / 2 - frame.y * tilePx).toFloat())
      val side = tilePx.roundToInt()
      tiles.forEach { (tile, image) ->
        drawImage(image, dstOffset = IntOffset((origin.x + tile.x * tilePx).roundToInt(), (origin.y + tile.y * tilePx).roundToInt()), dstSize = IntSize(side, side), colorFilter = filter, filterQuality = FilterQuality.Medium)
      }
      // A wash of the card's color, so the map sits behind the spots instead of competing with them.
      drawRect(colors.surfaceContainerLow.copy(alpha = if (tiles.isEmpty()) 1f else 0.35f))
      val ring = Stroke(1.5.dp.toPx())
      spots.forEach { spot ->
        val c = origin + Offset((Mercator.x(spot.lng, frame.zoom) * tilePx).toFloat(), (Mercator.y(spot.lat, frame.zoom) * tilePx).toFloat())
        val r = (metres / Mercator.metresPerTile(spot.lat, frame.zoom) * tilePx).toFloat()
        drawCircle(colors.primary.copy(alpha = 0.16f), r, c)
        drawCircle(colors.primary, r, c, style = ring)
        drawCircle(colors.primary, 3.5.dp.toPx(), c)
      }
    }
  }
}

// Luminance only, slightly lifted, so the map reads as paper.
private val GREY =
  ColorMatrix(
    floatArrayOf(
      0.25f, 0.5f, 0.1f, 0f, 30f,
      0.25f, 0.5f, 0.1f, 0f, 28f,
      0.25f, 0.5f, 0.1f, 0f, 24f,
      0f, 0f, 0f, 1f, 0f,
    )
  )

// Inverted for the night, compressed so OSM's light ground lands near the dark card (#2A2420) instead of
// black, and its dark labels come out as soft light grey rather than glaring white.
private val INVERTED_GREY =
  ColorMatrix(
    floatArrayOf(
      -0.153f, -0.301f, -0.058f, 0f, 174f,
      -0.153f, -0.301f, -0.058f, 0f, 168f,
      -0.153f, -0.301f, -0.058f, 0f, 163f,
      0f, 0f, 0f, 1f, 0f,
    )
  )
