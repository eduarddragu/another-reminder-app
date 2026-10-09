package dev.eduarddragu.anotherreminderapp.domain

/**
 * Reads a spot from what Google Maps lets you copy: "45.4812, 9.1825" from a dropped pin, or a full
 * maps URL with "@45.4812,9.1825,17z" in it. Short share links (maps.app.goo.gl) carry no coordinates.
 */
object Coordinates {
  private val PAIR = Regex("""(?<![\d.])(-?\d{1,2}\.\d+)\s*,\s*(-?\d{1,3}\.\d+)""")

  // In a place URL, "@lat,lng" is the map's centre and "!3d<lat>!4d<lng>" the place itself.
  private val PIN = Regex("""!3d(-?\d{1,2}\.\d+)!4d(-?\d{1,3}\.\d+)""")

  fun parse(text: String): Spot? =
    (PIN.findAll(text) + PAIR.findAll(text)).firstNotNullOfOrNull { match ->
      val lat = match.groupValues[1].toDouble()
      val lng = match.groupValues[2].toDouble()
      if (lat in -90.0..90.0 && lng in -180.0..180.0) Spot(lat, lng) else null
    }

  fun format(spot: Spot): String = "%.5f, %.5f".format(java.util.Locale.ROOT, spot.lat, spot.lng)
}
