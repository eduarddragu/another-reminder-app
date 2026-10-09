package dev.eduarddragu.anotherreminderapp.data

import android.util.Log
import dev.eduarddragu.anotherreminderapp.domain.Spot
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Finds a shop or an address by name with OpenStreetMap's Nominatim, which knows shops by name (Android's
 * geocoder only knows addresses: "Esselunga viale Piave" came back as the street). Results lean towards
 * [near]. When the whole query finds nothing ("esselunga lorenteggio"), the last words are tried as the area
 * and the first as the name inside it. Nominatim's policy: an identifying User-Agent and one request a
 * second, which [gate] keeps.
 */
object Search {
  data class Found(val title: String, val address: String, val spot: Spot)

  /** Null when the search itself failed (no network); empty when nothing matched. */
  suspend fun find(query: String, near: Spot?): List<Found>? {
    val whole = request(query, near, SPAN, bounded = false, limit = 8) ?: return null
    if (whole.isNotEmpty()) return nearestFirst(whole, near)
    // At most two splits (four more requests at one a second): "esselunga lorenteggio", "esselunga viale piave".
    val words = query.trim().split(Regex("\\s+"))
    for (split in (1 until words.size).take(MAX_SPLITS)) {
      val area = request(words.drop(split).joinToString(" "), near, SPAN, bounded = false, limit = 1) ?: return null
      val centre = area.firstOrNull()?.spot ?: continue
      val inside = request(words.take(split).joinToString(" "), centre, AREA_SPAN, bounded = true, limit = 8) ?: return null
      if (inside.isNotEmpty()) return nearestFirst(inside, near ?: centre)
    }
    return emptyList()
  }

  /** Nominatim's viewbox only leans results towards an area; it doesn't sort them. */
  private fun nearestFirst(found: List<Found>, near: Spot?): List<Found> =
    if (near == null) found else found.sortedBy { (it.spot.lat - near.lat).let { d -> d * d } + (it.spot.lng - near.lng).let { d -> d * d } }

  /** What's at a spot, for one saved as bare coordinates: "Esselunga · Viale Piave 38, Milano", or just the address. */
  suspend fun label(spot: Spot): String? =
    gate.withLock {
      delay((last + 1_100 - System.currentTimeMillis()).coerceAtLeast(0))
      last = System.currentTimeMillis()
      withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL("https://nominatim.openstreetmap.org/reverse?format=jsonv2&addressdetails=1&zoom=18&lat=${spot.lat}&lon=${spot.lng}").openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", Tiles.USER_AGENT)
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            try {
              connection.inputStream.use { labelOf(it.readBytes().decodeToString()) }
            } finally {
              connection.disconnect()
            }
          }
          .onFailure { Log.w("Search", "reverse failed", it) }
          .getOrNull()
      }
    }

  /** A reverse result as a spot's label: the name only when the place has one of its own. */
  fun labelOf(json: String): String? =
    parse("[$json]").firstOrNull()?.let { f -> if (f.address.startsWith(f.title)) f.address else "${f.title} · ${f.address}" }?.ifBlank { null }

  private val gate = Mutex()
  private var last = 0L

  private suspend fun request(query: String, near: Spot?, span: Double, bounded: Boolean, limit: Int): List<Found>? =
    gate.withLock {
      delay((last + 1_100 - System.currentTimeMillis()).coerceAtLeast(0))
      last = System.currentTimeMillis()
      withContext(Dispatchers.IO) {
        val box = near?.let { "&viewbox=${it.lng - span},${it.lat + span},${it.lng + span},${it.lat - span}" + if (bounded) "&bounded=1" else "" }.orEmpty()
        val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&limit=$limit$box&q=${URLEncoder.encode(query, "UTF-8")}"
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", Tiles.USER_AGENT)
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            try {
              connection.inputStream.use { parse(it.readBytes().decodeToString()) }
            } finally {
              connection.disconnect()
            }
          }
          .onFailure { Log.w("Search", "search failed", it) }
          .getOrNull()
      }
    }

  /** Nominatim's results as spots, a shop's building and its shop node (a few metres apart) kept once. */
  fun parse(json: String): List<Found> {
    val found =
      Json.parseToJsonElement(json).jsonArray.map { it.jsonObject }.map { r ->
        val address = r["address"] as? JsonObject
        fun part(key: String) = address?.get(key)?.jsonPrimitive?.content
        val street = listOfNotNull(part("road"), part("house_number")).joinToString(" ").ifBlank { null }
        val town = part("city") ?: part("town") ?: part("village") ?: part("suburb")
        val display = r["display_name"]?.jsonPrimitive?.content.orEmpty()
        val name = r["name"]?.jsonPrimitive?.content?.ifBlank { null }
        Found(
          title = name ?: street ?: display.substringBefore(","),
          address = listOfNotNull(street, town).joinToString(", ").ifBlank { display },
          spot = Spot(r["lat"]!!.jsonPrimitive.content.toDouble(), r["lon"]!!.jsonPrimitive.content.toDouble()),
        )
      }
    return found.filterIndexed { i, f -> found.take(i).none { it.title == f.title && close(it.spot, f.spot) } }
  }

  private fun close(a: Spot, b: Spot) = Math.abs(a.lat - b.lat) < 0.001 && Math.abs(a.lng - b.lng) < 0.001

  private const val SPAN = 0.25
  private const val MAX_SPLITS = 2
  private const val AREA_SPAN = 0.06
}
