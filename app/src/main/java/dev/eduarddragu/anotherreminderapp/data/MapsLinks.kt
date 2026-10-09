package dev.eduarddragu.anotherreminderapp.data

import android.util.Log
import dev.eduarddragu.anotherreminderapp.domain.Coordinates
import dev.eduarddragu.anotherreminderapp.domain.MapsShare
import dev.eduarddragu.anotherreminderapp.domain.Spot
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Follows a Google Maps short link's redirects until one of them shows coordinates. Nothing is sent but
 * the link itself, and only to Google's own hosts.
 */
object MapsLinks {
  private const val MAX_HOPS = 5

  /** Where a maps link leads: its coordinates when a hop shows them, else the place's name to search for. */
  data class Resolved(val spot: Spot?, val place: String?)

  suspend fun resolve(link: String): Resolved =
    withContext(Dispatchers.IO) {
      var url = link
      var place: String? = MapsShare.placeName(link)
      Coordinates.parse(link)?.let { return@withContext Resolved(it, place) }
      repeat(MAX_HOPS) {
        val uri = runCatching { URI(url) }.getOrNull() ?: return@withContext Resolved(null, place)
        if (uri.scheme != "https" || !googleHost(uri.host.orEmpty())) return@withContext Resolved(null, place)
        val next =
          runCatching {
              val connection = uri.toURL().openConnection() as HttpURLConnection
              connection.instanceFollowRedirects = false
              connection.connectTimeout = 5_000
              connection.readTimeout = 5_000
              try {
                connection.responseCode
                connection.getHeaderField("Location")
              } finally {
                connection.disconnect()
              }
            }
            .onFailure { Log.w("MapsLinks", "lookup failed", it) }
            .getOrNull() ?: return@withContext Resolved(null, place)
        // A consent page in the EU carries the real URL encoded in its "continue" parameter.
        val decoded = runCatching { URLDecoder.decode(next, "UTF-8") }.getOrDefault(next)
        Coordinates.parse(decoded)?.let { return@withContext Resolved(it, place) }
        place = MapsShare.placeName(decoded) ?: place
        url = runCatching { uri.resolve(next).toString() }.getOrNull() ?: return@withContext Resolved(null, place)
      }
      Resolved(null, place)
    }

  // google.com, google.it, google.co.uk, google.com.br; never google.evil.com.
  private val GOOGLE = Regex("""^(.+\.)?google\.(com|[a-z]{2}|co\.[a-z]{2}|com\.[a-z]{2})$""")

  fun googleHost(host: String) = host == "goo.gl" || host.endsWith(".goo.gl") || GOOGLE.matches(host)
}
