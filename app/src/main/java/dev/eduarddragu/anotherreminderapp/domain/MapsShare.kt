package dev.eduarddragu.anotherreminderapp.domain

/**
 * What Google Maps' Share sends: usually the place's name on the first line, its address, and a link.
 * A dropped pin or a full URL carries coordinates; a short link (maps.app.goo.gl) has to be expanded first.
 */
data class MapsShare(val name: String?, val spot: Spot?, val link: String?, val address: String? = null) {
  /** What to search for when the share carries no position: the name and the address Maps wrote under it. */
  val query: String? get() = listOfNotNull(name, address).joinToString(" ").ifBlank { null }


  companion object {
    private val URL = Regex("""https?://\S+""")
    private val PLACE = Regex("""/maps/place/([^/?]+)""")

    /**
     * The place's name and address from a maps URL ("/maps/place/Via+Tornieri,+62,+36100+Vicenza/..."):
     * what a phone's share link expands to now, with no coordinates in it, but enough to search for.
     */
    /**
     * What to search for a place Maps named: the whole of it, then just the address after the name, which
     * the search finds when "Shop name, street, number, city" as one string is too much for it.
     */
    fun searches(place: String): List<String> = listOf(place, place.substringAfter(", ", "")).filter { it.isNotBlank() }.distinct()

    fun placeName(url: String): String? =
      PLACE.find(url)?.groupValues?.get(1)?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() }?.trim()?.ifBlank { null }

    fun parse(text: String): MapsShare {
      val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() && URL.find(it) == null && Coordinates.parse(it) == null && it != "Dropped pin" }
      return MapsShare(lines.getOrNull(0), Coordinates.parse(text), URL.find(text)?.value, lines.getOrNull(1))
    }
  }
}
