package dev.eduarddragu.anotherreminderapp.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DomainTest {
  @Test
  fun coordinatesFromPinAndMapsUrl() {
    assertEquals(Spot(45.4812, 9.1825), Coordinates.parse("45.4812, 9.1825"))
    assertEquals(Spot(45.481, 9.182), Coordinates.parse("https://www.google.com/maps/place/Esselunga/@45.481,9.182,17z/data=!3m1"))
    assertEquals(Spot(-33.86, 151.2), Coordinates.parse("-33.86,151.2"))
    assertNull(Coordinates.parse("https://maps.app.goo.gl/AbC123"))
    assertNull(Coordinates.parse("123.45, 9.1")) // not a latitude, and no slice of it either
    assertNull(Coordinates.parse("45, 9"))
    // The place's own pin wins over the map's centre.
    assertEquals(Spot(45.4812, 9.2051), Coordinates.parse("https://www.google.com/maps/place/Esselunga/@45.47,9.19,17z/data=!3m1!4b1!4m6!3m5!1s0x0:0x0!8m2!3d45.4812!4d9.2051"))
  }

  @Test
  fun mapsShares() {
    val place = MapsShare.parse("Esselunga\nViale Piave, 38, 20129 Milano MI\nhttps://maps.app.goo.gl/AbC123")
    assertEquals("Esselunga", place.name)
    assertNull(place.spot)
    assertEquals("https://maps.app.goo.gl/AbC123", place.link)
    assertEquals("Esselunga Viale Piave, 38, 20129 Milano MI", place.query)
    val pin = MapsShare.parse("Dropped pin\nhttps://maps.google.com/?q=45.4812,9.2051")
    assertNull(pin.name)
    assertEquals(Spot(45.4812, 9.2051), pin.spot)
  }

  @Test
  fun repeatingRemindersComeBack() {
    val zone = ZoneId.of("Europe/Rome")
    fun at(y: Int, m: Int, d: Int) = LocalDateTime.of(y, m, d, 9, 0).atZone(zone).toInstant()
    val yearly = Repeat(1, RepeatUnit.YEARS)
    val data = Data(reminders = listOf(Reminder(1, "bollo", at = at(2026, 6, 1).toEpochMilli(), fired = true, repeat = yearly), Reminder(2, "once", at = 0)))
    val done = data.complete(1, at(2026, 6, 3), zone).reminder(1)!!
    assertEquals(at(2027, 6, 1).toEpochMilli(), done.at)
    assertEquals(false, done.fired)
    // Done early (paid in May) still moves one step on.
    assertEquals(at(2027, 6, 1).toEpochMilli(), data.complete(1, at(2026, 5, 20), zone).reminder(1)!!.at)
    assertNull(data.complete(2, at(2026, 6, 3), zone).reminder(2))
    // 29 February: the 28th in other years, the 29th again in leap years.
    assertEquals(at(2027, 2, 28).toEpochMilli(), nextRepeat(at(2024, 2, 29).toEpochMilli(), yearly, at(2026, 10, 9), zone))
    assertEquals(at(2028, 2, 29).toEpochMilli(), nextRepeat(at(2024, 2, 29).toEpochMilli(), yearly, at(2027, 3, 1), zone))
    // A June date set in October means next June.
    assertEquals(at(2027, 6, 1).toEpochMilli(), nextRepeat(at(2026, 6, 1).toEpochMilli(), yearly, at(2026, 10, 9), zone))
    // Every 2 weeks stays on its grid even when done late; 09:00 stays 09:00 across the October clock change.
    assertEquals(at(2026, 10, 29).toEpochMilli(), nextRepeat(at(2026, 10, 1).toEpochMilli(), Repeat(2, RepeatUnit.WEEKS), at(2026, 10, 16), zone))
    // Every month from the 31st: the 30th in November, the 31st again in December.
    assertEquals(at(2026, 11, 30).toEpochMilli(), nextRepeat(at(2026, 10, 31).toEpochMilli(), Repeat(1, RepeatUnit.MONTHS), at(2026, 11, 1), zone))
    assertEquals(at(2026, 12, 31).toEpochMilli(), nextRepeat(at(2026, 10, 31).toEpochMilli(), Repeat(1, RepeatUnit.MONTHS), at(2026, 12, 1), zone))
    assertEquals("Every 5 days", Copy.repeatLabel(Repeat(5, RepeatUnit.DAYS)))
    assertEquals("Every year", Copy.repeatLabel(yearly))
  }

  @Test
  fun countdown() {
    val now = LocalDateTime.of(2026, 10, 9, 23, 50)
    assertEquals(Countdown(40, "min"), Countdown.of(now, LocalDateTime.of(2026, 10, 10, 0, 30)))
    assertEquals(Countdown(5, "hours"), Countdown.of(now, LocalDateTime.of(2026, 10, 10, 5, 0)))
    assertEquals(Countdown(235, "days"), Countdown.of(now, LocalDateTime.of(2027, 6, 1, 9, 0)))
    assertNull(Countdown.of(now, now))
  }

  @Test
  fun fencesOnlyForPlacesWithReminders() {
    val esselunga = Place(1, "Esselunga", listOf(Spot(45.0, 9.0), Spot(45.1, 9.1)), radius = 100)
    val gym = Place(2, "Gym", listOf(Spot(46.0, 9.0)))
    val data = Data(listOf(esselunga, gym), listOf(Reminder(3, "milk", placeId = 1), Reminder(4, "call mum", at = 0)))
    assertEquals(listOf("1:0", "1:1"), Fences.plan(data).map { it.requestId })
    assertEquals(100, Fences.plan(data).first().radius)
    assertEquals(1, Fences.placeId("1:1"))
  }

  @Test
  fun fencesCappedAtTheLimit() {
    val place = Place(1, "Everywhere", List(150) { Spot(45.0, it / 1000.0) })
    assertEquals(Fences.MAX, Fences.plan(Data(listOf(place), listOf(Reminder(2, "x", placeId = 1)))).size)
  }

  @Test
  fun removingAPlaceTakesItsRemindersAndIdsStayUnique() {
    val data = Data(listOf(Place(1, "A")), listOf(Reminder(2, "x", placeId = 1), Reminder(5, "y", at = 0)))
    assertEquals(6, data.newId())
    assertEquals(listOf(5), data.removePlace(1).reminders.map { it.id })
    assertEquals("z", data.upsert(Reminder(5, "z", at = 0)).reminder(5)?.text)
    assertEquals(2, data.upsert(Reminder(5, "z", at = 0)).reminders.size)
  }

  @Test
  fun idsAreNeverReused() {
    val added = Data().upsert(Reminder(Data().newId(), "a", at = 0))
    assertEquals(1, added.reminders.single().id)
    // Done on the newest one must not free its id for the next reminder (a stale Undo or Done would hit it).
    assertEquals(2, added.removeReminder(1).newId())
    // Files from before nextId existed still start past their highest id.
    assertEquals(8, Data(reminders = listOf(Reminder(7, "x", at = 0))).newId())
  }

  @Test
  fun homeLine() {
    assertEquals("Car tax. It's due.", Copy.homeLine(listOf("Car tax"), null, null, null))
    assertEquals("2 things are due.", Copy.homeLine(listOf("a", "b"), null, null, "Esselunga"))
    assertEquals("Car tax, in 3 days.", Copy.homeLine(emptyList(), "Car tax.", Countdown(3, "days"), "Esselunga"))
    assertEquals("Waiting for you at Esselunga.", Copy.homeLine(emptyList(), null, null, "Esselunga"))
    assertEquals("Nothing to remember. Suspicious.", Copy.homeLine(emptyList(), null, null, null))
  }

  @Test
  fun mapsLinksOnlyFollowGoogle() {
    listOf("maps.app.goo.gl", "goo.gl", "www.google.com", "maps.google.it", "consent.google.co.uk", "google.com.br").forEach { assertEquals(it, true, dev.eduarddragu.anotherreminderapp.data.MapsLinks.googleHost(it)) }
    listOf("google.evil.com", "maps.google.attacker.net", "evilgoogle.com", "goo.gl.evil.com").forEach { assertEquals(it, false, dev.eduarddragu.anotherreminderapp.data.MapsLinks.googleHost(it)) }
  }

  @Test
  fun mercatorFramesTheSpots() {
    // Milan's Duomo at zoom 16 sits in tile (34441, 23454), per the OSM wiki's asinh formula.
    assertEquals(34441, Mercator.x(9.1919, 16).toInt())
    assertEquals(23454, Mercator.y(45.4642, 16).toInt())
    val one = Mercator.fit(listOf(Spot(45.4642, 9.1919)), 300, 1.0, 1.0)
    val city = Mercator.fit(listOf(Spot(45.44, 9.15), Spot(45.50, 9.23)), 300, 1.0, 1.0)
    assert(city.zoom < one.zoom) { "two far spots zoom out: ${city.zoom} vs ${one.zoom}" }
    // Centred between them.
    assertEquals((Mercator.x(9.15, city.zoom) + Mercator.x(9.23, city.zoom)) / 2, city.x, 1e-9)
  }

  @Test
  fun nominatimResults() {
    // Shaped like a real response for "esselunga" in Milan: the shop and its building, a few metres apart.
    val json = """[
      {"lat":"45.4729","lon":"9.2072","name":"Esselunga","display_name":"Esselunga, 38, Viale Piave, Milano","address":{"road":"Viale Piave","house_number":"38","city":"Milano"}},
      {"lat":"45.4731","lon":"9.2075","name":"Esselunga","display_name":"Esselunga, 38, Viale Piave, Milano","address":{"road":"Viale Piave","house_number":"38","city":"Milano"}},
      {"lat":"45.4600","lon":"9.1900","name":"","display_name":"Via Dante, Milano","address":{"road":"Via Dante","city":"Milano"}}
    ]"""
    val found = dev.eduarddragu.anotherreminderapp.data.Search.parse(json)
    assertEquals(listOf("Esselunga", "Via Dante"), found.map { it.title })
    assertEquals("Viale Piave 38, Milano", found[0].address)
    assertEquals(Spot(45.4729, 9.2072), found[0].spot)
    // A reverse lookup with no name of its own is just its address; a shop keeps its name in front.
    assertEquals("Viale Piave 38, Milano", dev.eduarddragu.anotherreminderapp.data.Search.labelOf("""{"lat":"45.47","lon":"9.21","name":"","display_name":"38, Viale Piave, Milano","address":{"road":"Viale Piave","house_number":"38","city":"Milano"}}"""))
    assertEquals("Esselunga · Viale Piave 38, Milano", dev.eduarddragu.anotherreminderapp.data.Search.labelOf("""{"lat":"45.47","lon":"9.21","name":"Esselunga","display_name":"Esselunga, 38, Viale Piave, Milano","address":{"road":"Viale Piave","house_number":"38","city":"Milano"}}"""))
  }

  @Test
  fun placesReorderAndMapsLinksName() {
    val data = Data(listOf(Place(1, "Esselunga"), Place(2, "Home"), Place(3, "Gym")))
    assertEquals(listOf(2, 1, 3), data.reorderPlaces(listOf(2, 1, 3)).places.map { it.id })
    // A place missing from the dragged order keeps its spot at the end, never lost.
    assertEquals(listOf(3, 1, 2), data.reorderPlaces(listOf(3, 1)).places.map { it.id })
    // What a phone's maps.app.goo.gl link expands to today: an address, no coordinates.
    val expanded = "https://www.google.com/maps/place/Automobile+Club+Vicenza+ACI+Via+Tornieri,+Via+Arnaldo+Tornieri,+62,+36100+Vicenza+VI/data=!4m2!3m1!1s0x477f322085032267:0x971009d62c31f087?utm_source=mstt_1&entry=gps"
    assertEquals("Automobile Club Vicenza ACI Via Tornieri, Via Arnaldo Tornieri, 62, 36100 Vicenza VI", MapsShare.placeName(expanded))
    assertNull(Coordinates.parse(expanded))
    assertNull(MapsShare.placeName("https://maps.app.goo.gl/91jma2zHp9mWgG6M6"))
    assertEquals(listOf("Automobile Club Vicenza ACI Via Tornieri, Via Arnaldo Tornieri, 62, 36100 Vicenza VI", "Via Arnaldo Tornieri, 62, 36100 Vicenza VI"), MapsShare.searches(MapsShare.placeName(expanded)!!))
    assertEquals(listOf("Via Roma 1"), MapsShare.searches("Via Roma 1"))
  }

  @Test
  fun nudgesBackOffAndSleepAtNight() {
    val zone = ZoneId.of("Europe/Rome")
    fun t(m: Int, d: Int, h: Int, min: Int = 0) = LocalDateTime.of(2026, m, d, h, min).atZone(zone).toInstant().toEpochMilli()
    val due = t(6, 1, 9)
    val plan = Nudges.plan(due, zone)
    // A week before at 10:00, the evening before at 20:00, then the reminder.
    assertEquals(listOf(t(5, 25, 10), t(5, 31, 20), due), plan.take(3).map { it.at })
    assertEquals(listOf(Nudges.Kind.WEEK_BEFORE, Nudges.Kind.DAY_BEFORE, Nudges.Kind.DUE), plan.take(3).map { it.kind })
    // Then 1, 2, 4, 8 hours after; 16 hours after is 01:00, so it waits for 08:00, then once a day.
    val late = plan.filter { it.kind == Nudges.Kind.LATE }
    assertEquals(listOf(t(6, 1, 10), t(6, 1, 11), t(6, 1, 13), t(6, 1, 17), t(6, 2, 8), t(6, 2, 9)), late.take(6).map { it.at })
    assertEquals(listOf(0, 0, 0, 0, 1, 1), late.take(6).map { it.daysLate })
    assertEquals(t(6, 8, 9), late.last().at)

    val reminder = Reminder(1, "bollo", at = due)
    // Made four days before: the week-before heads-up has gone by and is skipped, the evening one is next.
    assertEquals(Nudges.Kind.DAY_BEFORE, Nudges.next(reminder, t(5, 28, 12), zone)?.kind)
    // The phone was off through the due time: the reminder itself still comes, once.
    assertEquals(due, Nudges.next(reminder, t(6, 1, 15), zone)?.at)
    // Fired at 15:00 (late): the nudges carry on from now, not from the ones missed.
    assertEquals(t(6, 1, 17), Nudges.next(reminder.copy(fired = true, lastNudge = t(6, 1, 15)), t(6, 1, 15), zone)?.at)
    // A week later, it stops.
    assertNull(Nudges.next(reminder.copy(fired = true, lastNudge = t(6, 8, 9)), t(6, 8, 9, 1), zone))
    // Due in the evening: no heads-up the evening before only three hours earlier on the same day.
    assertEquals(Nudges.Kind.DAY_BEFORE, Nudges.plan(t(6, 2, 19), zone)[1].kind)
    assert(Nudges.plan(t(6, 1, 22, 30), zone).none { it.kind == Nudges.Kind.DAY_BEFORE && it.at > t(6, 1, 19) })
  }

  @Test
  fun closingAnInlineEditNeverEatsTheItem() {
    assertEquals(Checklist.Edit.RENAME, Checklist.close(open = true, old = "zucca", typed = " Zucca "))
    assertEquals(Checklist.Edit.KEEP, Checklist.close(open = true, old = "zucca", typed = "zucca "))
    assertEquals(Checklist.Edit.REMOVE, Checklist.close(open = true, old = "zucca", typed = "  "))
    // The second close (keyboard gone after focus lost) finds the field emptied: it must not remove.
    assertEquals(Checklist.Edit.KEEP, Checklist.close(open = false, old = "zucca", typed = ""))
  }

  @Test
  fun dataSurvivesJson() {
    val data = Data(listOf(Place(1, "A", listOf(Spot(1.5, 2.5)))), listOf(Reminder(2, "x", placeId = 1), Reminder(3, "y", at = 99, fired = true)))
    assertEquals(data, Json.decodeFromString(Data.serializer(), Json.encodeToString(Data.serializer(), data)))
  }

  @Test
  fun labels() {
    val today = LocalDate.of(2026, 10, 9)
    assertEquals("TODAY · 18:30", Copy.whenLabel(LocalDateTime.of(2026, 10, 9, 18, 30), today))
    assertEquals("TOMORROW · 09:00", Copy.whenLabel(LocalDateTime.of(2026, 10, 10, 9, 0), today))
    assertEquals("MON 12 OCT · 09:00", Copy.whenLabel(LocalDateTime.of(2026, 10, 12, 9, 0), today))
    assertEquals("1 JUN 2027 · 09:00", Copy.whenLabel(LocalDateTime.of(2027, 6, 1, 9, 0), today))
    assertEquals("You're at Esselunga. Past you left you this:", Copy.placeTitle("Esselunga", 0))
    assertEquals("Hello from Esselunga. You wanted to remember:", Copy.placeTitle("Esselunga", 7))
    assertEquals("The shop, at last. Glad I caught you:", Copy.placeTitle("the shop", 5))
    assertEquals("It's that time of the year again:", Copy.timeTitle(0, Repeat(1, RepeatUnit.YEARS)))
    assertEquals("Back again, as agreed:", Copy.timeTitle(0, Repeat(2, RepeatUnit.WEEKS)))
    // Three reminders at one shop open the same way on a day.
    assertEquals(Copy.placeSeed(4, LocalDate.of(2026, 10, 9)), Copy.placeSeed(4, LocalDate.of(2026, 10, 9)))
    // Labels never make a fence change: they aren't part of where it is.
    val labelled = Data(listOf(Place(1, "A", listOf(Spot(45.0, 9.0, "Esselunga · Via Roma 1")))), listOf(Reminder(2, "x", placeId = 1)))
    assertEquals(Fences.plan(labelled), Fences.plan(labelled.upsert(Place(1, "A", listOf(Spot(45.0, 9.0))))))
  }
}
