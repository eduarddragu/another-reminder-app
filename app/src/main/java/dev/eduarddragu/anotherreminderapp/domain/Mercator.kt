package dev.eduarddragu.anotherreminderapp.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

/** Web Mercator in tile units (OpenStreetMap's tiles): where a spot sits at a zoom, and which zoom fits. */
object Mercator {
  private const val EARTH_CIRCUMFERENCE = 40_075_016.686

  fun x(lng: Double, zoom: Int): Double = (lng + 180) / 360 * (1 shl zoom)

  fun y(lat: Double, zoom: Int): Double {
    val r = Math.toRadians(lat)
    return (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * (1 shl zoom)
  }

  fun metresPerTile(lat: Double, zoom: Int): Double = EARTH_CIRCUMFERENCE * cos(Math.toRadians(lat)) / (1 shl zoom)

  /** The frame for some spots and their radius: zoom and centre (in tiles). */
  data class Frame(val zoom: Int, val x: Double, val y: Double)

  /**
   * The closest zoom (up to 17) at which every spot with its radius fits in [width] by [height] tiles,
   * with a margin, centred on them.
   */
  fun fit(spots: List<Spot>, radius: Int, width: Double, height: Double): Frame {
    for (zoom in 17 downTo 2) {
      val r = radius / metresPerTile(spots.map { it.lat }.average(), zoom)
      val xs = spots.map { x(it.lng, zoom) }
      val ys = spots.map { y(it.lat, zoom) }
      val w = xs.max() - xs.min() + 2 * r
      val h = ys.max() - ys.min() + 2 * r
      if (w <= width * 0.7 && h <= height * 0.7 || zoom == 2) return Frame(zoom, (xs.max() + xs.min()) / 2, (ys.max() + ys.min()) / 2)
    }
    error("unreachable")
  }
}
