package dev.eduarddragu.anotherreminderapp.ui

import android.Manifest
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.animation.animateContentSize
import dev.eduarddragu.anotherreminderapp.ui.components.ConfirmDiscard
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import dev.eduarddragu.anotherreminderapp.R
import dev.eduarddragu.anotherreminderapp.theme.Motion
import dev.eduarddragu.anotherreminderapp.theme.fadeThrough
import dev.eduarddragu.anotherreminderapp.ui.components.LineIcon
import dev.eduarddragu.anotherreminderapp.ui.components.PressCard
import dev.eduarddragu.anotherreminderapp.ui.components.SaveButton
import dev.eduarddragu.anotherreminderapp.ui.components.SpotMap
import dev.eduarddragu.anotherreminderapp.ui.components.lineStyle
import dev.eduarddragu.anotherreminderapp.ui.components.rememberArrival
import dev.eduarddragu.anotherreminderapp.ui.components.rememberReducedMotion
import dev.eduarddragu.anotherreminderapp.ui.components.rise
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dev.eduarddragu.anotherreminderapp.data.MapsLinks
import dev.eduarddragu.anotherreminderapp.data.Search
import dev.eduarddragu.anotherreminderapp.data.Store
import dev.eduarddragu.anotherreminderapp.domain.MapsShare
import dev.eduarddragu.anotherreminderapp.domain.Coordinates
import dev.eduarddragu.anotherreminderapp.domain.Data
import dev.eduarddragu.anotherreminderapp.domain.Place
import dev.eduarddragu.anotherreminderapp.domain.Spot
import dev.eduarddragu.anotherreminderapp.triggers.Notifications
import dev.eduarddragu.anotherreminderapp.ui.components.ButtonLabel
import dev.eduarddragu.anotherreminderapp.ui.components.CardLabel
import dev.eduarddragu.anotherreminderapp.ui.components.ConfirmDelete
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import dev.eduarddragu.anotherreminderapp.ui.components.Field
import dev.eduarddragu.anotherreminderapp.ui.components.ScreenTitle
import dev.eduarddragu.anotherreminderapp.ui.components.Screen
import dev.eduarddragu.anotherreminderapp.ui.components.SectionLabel
import dev.eduarddragu.anotherreminderapp.ui.components.TextAction
import dev.eduarddragu.anotherreminderapp.ui.components.cardOutline

@Composable
fun PlacesScreen(data: Data, onOpen: (Int?) -> Unit) {
  val context = LocalContext.current
  val haptics = LocalHapticFeedback.current
  val arrival = rememberArrival(2 + minOf(data.places.size, 6), delayOf = { if (it < 2) 70L * it else 280L + Motion.STAGGER * (it - 2) }, durationOf = { Motion.ENTRANCE })
  val list = rememberLazyListState()
  // Hold a card, then drag it: the order lives here while dragging and is saved when it's let go.
  var order by remember(data.places) { mutableStateOf(data.places.map { it.id }) }
  var dragging by remember { mutableStateOf<Int?>(null) }
  var offset by remember { mutableFloatStateOf(0f) }
  fun drop() {
    // Against the store, not [data]: the gesture's callbacks outlive the composition that made them.
    if (order != Store.data.value.places.map { it.id }) Store.update(context) { it.reorderPlaces(order) }
    dragging = null
    offset = 0f
  }
  /** Swaps the dragged card with a neighbour once its middle has crossed the neighbour's. */
  fun follow(id: Int) {
    val items = list.layoutInfo.visibleItemsInfo
    val me = items.firstOrNull { it.key == id } ?: return
    val centre = me.offset + offset + me.size / 2f
    val index = order.indexOf(id)
    val below = order.getOrNull(index + 1)?.let { k -> items.firstOrNull { it.key == k } }
    val above = order.getOrNull(index - 1)?.let { k -> items.firstOrNull { it.key == k } }
    when {
      below != null && centre > below.offset + below.size / 2f -> {
        order = order.toMutableList().apply { add(index + 1, removeAt(index)) }
        offset -= (below.offset - me.offset).toFloat()
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
      }
      above != null && centre < above.offset + above.size / 2f -> {
        order = order.toMutableList().apply { add(index - 1, removeAt(index)) }
        offset += (me.offset - above.offset).toFloat()
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
      }
    }
  }

  Screen(bottomBar = { Button({ onOpen(null) }, shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { ButtonLabel(R.drawable.ic_plus, "New place") } }, state = list) {
    item { ScreenTitle("Places", "Where it counts.", Modifier.padding(bottom = 24.dp).rise(arrival[0], 16.dp)) }
    if (data.places.isEmpty()) item { Text("No places yet. Add the supermarket first.", style = lineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.rise(arrival[1])) }
    itemsIndexed(order.mapNotNull(data::place), key = { _, place -> place.id }) { index, place ->
      val waiting = data.reminders.count { it.placeId == place.id }
      val step = arrival[2 + minOf(index, 5)]
      val lifted = dragging == place.id
      val lift by animateFloatAsState(if (lifted) 1f else 0f, tween(Motion.SHORT, easing = Motion.EaseUi), label = "lift")
      Box(
        Modifier.padding(bottom = 8.dp)
          // The others glide out of the way; the one in hand follows the finger instead.
          .then(if (lifted) Modifier else Modifier.animateItem(placementSpec = tween(Motion.LIST, easing = Motion.EaseUi)))
          .zIndex(if (lifted) 1f else 0f)
          .graphicsLayer {
            translationY = if (lifted) offset else 0f
            scaleX = 1f + 0.03f * lift
            scaleY = 1f + 0.03f * lift
            shadowElevation = 12.dp.toPx() * lift
            shape = RoundedCornerShape(24.dp)
            clip = false
          }
          .pointerInput(place.id) {
            detectDragGesturesAfterLongPress(
              onDragStart = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                dragging = place.id
                offset = 0f
              },
              onDrag = { change, amount ->
                change.consume()
                offset += amount.y
                follow(place.id)
              },
              onDragEnd = { drop() },
              onDragCancel = { drop() },
            )
          }
      ) {
        PressCard(onClick = { onOpen(place.id) }, onClickLabel = "Edit", appear = { step.value }) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                LineIcon(R.drawable.ic_pin)
                Spacer(Modifier.width(10.dp))
                CardLabel(lead = listOf(spots(place.spots.size).uppercase(), "${place.radius} M"), state = if (waiting > 0) (if (waiting == 1) "1 REMINDER" else "$waiting REMINDERS") else null, maxLines = 1)
              }
              Spacer(Modifier.height(10.dp))
              Text(place.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            SpotMap(place.spots, place.radius, Modifier.size(72.dp).clip(MaterialTheme.shapes.medium))
          }
        }
      }
    }
    if (data.places.isNotEmpty()) {
      item {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          if (data.places.size > 1) Text("Hold a card to move it. Home lists place reminders in this order.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          OsmCredit()
        }
      }
    }
  }
}

/** OpenStreetMap's licence asks for this wherever its tiles show. */
@Composable
private fun OsmCredit(modifier: Modifier = Modifier) =
  Text(
    "MAPS © OPENSTREETMAP CONTRIBUTORS",
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    // On the map it sits on a scrap of the card, so the streets don't run through it.
    modifier = modifier.clip(MaterialTheme.shapes.extraSmall).background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f)).padding(horizontal = 6.dp, vertical = 2.dp),
  )

private fun spots(n: Int) = if (n == 1) "1 spot" else "$n spots"

// Spots as "lat|lng|label" strings: a label can hold any character but a newline-free "|"-split keeps the first two.
private val SpotsSaver =
  listSaver<List<Spot>, String>(
    save = { it.map { s -> "${s.lat}|${s.lng}|${s.label}" } },
    restore = { it.map { line -> line.split("|", limit = 3).let { (lat, lng, label) -> Spot(lat.toDouble(), lng.toDouble(), label) } } },
  )

@Composable
fun PlaceEditor(data: Data, id: Int?, incoming: MapsShare?, onIncomingConsumed: () -> Unit, onSaved: (Int) -> Unit, onClose: () -> Unit) {
  val context = LocalContext.current
  val existing = id?.let(data::place)
  var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
  var spots by rememberSaveable(stateSaver = SpotsSaver) { mutableStateOf(existing?.spots.orEmpty()) }
  var radius by rememberSaveable { mutableStateOf(existing?.radius ?: Place.DEFAULT_RADIUS) }
  // The search follows the name until it's edited: "Esselunga Lorenteggio" is typed once.
  var query by rememberSaveable { mutableStateOf("") }
  var queryEdited by rememberSaveable { mutableStateOf(false) }
  val shownQuery = if (queryEdited) query else name
  var results by remember { mutableStateOf(emptyList<Search.Found>()) }
  var searching by remember { mutableStateOf(false) }
  var locating by remember { mutableStateOf(false) }
  var note by rememberSaveable { mutableStateOf<String?>(null) }
  var confirmDelete by rememberSaveable { mutableStateOf(false) }
  var confirmDiscard by rememberSaveable { mutableStateOf(false) }
  var saved by remember { mutableStateOf(false) }
  val waiting = existing?.let { p -> data.reminders.count { it.placeId == p.id } } ?: 0
  // Positions, not labels: a label filled in on its own isn't a change to confirm.
  val dirty = name != existing?.name.orEmpty() || spots.map { it.lat to it.lng } != existing?.spots.orEmpty().map { it.lat to it.lng } || radius != (existing?.radius ?: Place.DEFAULT_RADIUS)
  // On a place that has its spots, finding another stays folded behind "Add another spot" until wanted.
  var findOpen by rememberSaveable { mutableStateOf(existing == null || existing.spots.isEmpty()) }
  BackHandler(dirty && !saved) { confirmDiscard = true }

  val haptics = LocalHapticFeedback.current
  val labelScope = rememberCoroutineScope()
  fun add(spot: Spot) {
    if (spots.any { it.samePlace(spot) }) return
    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    spots = spots + spot
    // Bare coordinates: what's there is looked up and shown in their place.
    if (spot.label.isBlank()) labelScope.launch { Search.label(spot)?.let { label -> spots = spots.map { if (it.samePlace(spot) && it.label.isBlank()) it.copy(label = label) else it } } }
  }

  fun locate() {
    locating = true
    note = null
    currentSpot(context) { spot ->
      locating = false
      if (spot == null) note = "No position yet. Is location on?"
      else add(spot.copy(label = "Where I was, ${LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))}"))
    }
  }
  val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
    if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) locate() else note = "No location, no idea where you are. Allow it, or search instead."
  }
  val parsed = Coordinates.parse(shownQuery)
  val scope = rememberCoroutineScope()
  fun search(text: String = shownQuery, emptyNote: String = "Nothing called that. Add the street or the city.") {
    val q = text.trim()
    if (q.isEmpty() || searching) return
    searching = true
    note = null
    scope.launch {
      try {
        // Nearest first: around the phone's last position, else around this place's own spots.
        val found = Search.find(q, lastSpot(context) ?: spots.firstOrNull())
        results = found.orEmpty()
        note = when {
          found == null -> "Search didn't answer. Check the connection, or paste coordinates."
          found.isEmpty() -> emptyNote
          else -> null
        }
      } finally {
        searching = false
      }
    }
  }

  /**
   * A Google Maps place, shared to the app or pasted as a link: its spot when it carries one, else a search
   * for its name and address (a phone's share link expands to "/maps/place/<address>" with no coordinates),
   * so the right branch is picked from a list rather than guessed.
   */
  fun take(share: MapsShare) {
    findOpen = true
    if (name.isBlank()) share.name?.let { name = it }
    note = null
    scope.launch {
      searching = true
      val resolved =
        try {
          share.spot?.let { MapsLinks.Resolved(it, null) } ?: share.link?.let { MapsLinks.resolve(it) }
        } finally {
          searching = false
        }
      val place = resolved?.place
      if (name.isBlank()) place?.let { name = it.substringBefore(",") }
      val spot = resolved?.spot
      val query = share.query ?: place
      if (spot != null) {
        add(spot.copy(label = listOfNotNull(share.name, share.address).joinToString(" · ").ifBlank { place.orEmpty() }))
        note = "Added from Google Maps."
        return@launch
      }
      // The whole name and address first, then the address alone; the right branch is picked from the list.
      searching = true
      try {
        val near = lastSpot(context) ?: spots.firstOrNull()
        var found: List<Search.Found>? = emptyList()
        for (q in query?.let(MapsShare::searches).orEmpty()) {
          found = Search.find(q, near)
          if (found == null || found.isNotEmpty()) break
        }
        results = found.orEmpty()
        note = when {
          found == null -> "Search didn't answer. Check the connection, or paste coordinates."
          found.isEmpty() -> "Maps sent something I couldn't place. Search it here."
          else -> "Found it. Pick the right one."
        }
      } finally {
        searching = false
      }
    }
  }

  /** The search field's action: a pasted Google Maps link is a place, not words to look for. */
  fun submit() = if (shownQuery.contains("://")) take(MapsShare.parse(shownQuery)) else search()

  // Spots with no label (from before labels) get their address, looked up once when the page opens;
  // pasted coordinates get theirs as they're added (see add()).
  LaunchedEffect(Unit) {
    for (spot in spots.filter { it.label.isBlank() }) {
      val label = Search.label(spot) ?: continue
      fun named(list: List<Spot>) = list.map { if (it.samePlace(spot) && it.label.isBlank()) it.copy(label = label) else it }
      spots = named(spots)
      // Kept straight away on a saved place that has this spot: a label isn't an edit to confirm.
      val placeId = existing?.id ?: continue
      Store.update(context) { d -> d.place(placeId)?.let { p -> d.upsert(p.copy(spots = named(p.spots))) } ?: d }
    }
  }

  // Consuming the share changes this effect's key; the work runs in the screen's scope, so it isn't cancelled.
  LaunchedEffect(incoming) {
    val share = incoming ?: return@LaunchedEffect
    onIncomingConsumed()
    take(share)
  }

  val arrival = rememberArrival(4)
  val pulse = rememberLocatingPulse(locating || searching)

  @Composable
  fun FindCard(label: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
      SectionLabel(label)
      Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, contentColor = MaterialTheme.colorScheme.onSurface)) {
        Column(Modifier.padding(20.dp).animateContentSize(tween(Motion.LIST, easing = Motion.EaseUi)), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Field(
            shownQuery,
            {
              query = it
              queryEdited = true
              results = emptyList()
            },
            "Search a shop or an address",
            Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            supportingText = { Text(if (parsed != null) "That's a position. No search needed." else "Like \u201cEsselunga viale Piave\u201d. A Maps link or coordinates work too.") },
          )
          if (parsed != null) TextAction("Add these coordinates", { add(parsed); query = ""; queryEdited = true }, vertical = 0.dp, icon = R.drawable.ic_plus)
          else TextAction(
            if (searching) "Searching…" else "Search",
            { submit() },
            Modifier.graphicsLayer { alpha = if (searching) pulse.value else 1f },
            enabled = shownQuery.isNotBlank() && !searching,
            vertical = 0.dp,
            icon = R.drawable.ic_search,
          )
          // Right under what was just tapped, where the eyes are.
          note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
          // The list stays after a pick, so three branches are three taps. New results scroll into view:
          // under the keyboard and the bottom bar they'd go unseen.
          val showResults = remember { BringIntoViewRequester() }
          LaunchedEffect(results) { if (results.isNotEmpty()) showResults.bringIntoView() }
          if (results.isNotEmpty()) Spacer(Modifier.height(1.dp).bringIntoViewRequester(showResults))
          results.forEach { found ->
            val added = spots.any { it.samePlace(found.spot) }
            Row(
              Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(enabled = !added, onClickLabel = "Add ${found.title} as a spot") {
                add(found.spot.copy(label = listOf(found.title, found.address).distinct().joinToString(" · ")))
                if (name.isBlank()) name = found.title
              }.padding(vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              LineIcon(R.drawable.ic_pin, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
              Spacer(Modifier.width(12.dp))
              Column(Modifier.weight(1f)) {
                Text(found.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(found.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
              }
              Spacer(Modifier.width(8.dp))
              LineIcon(if (added) R.drawable.ic_check else R.drawable.ic_plus, size = 20.dp)
            }
          }
          if (results.isNotEmpty()) OsmCredit()
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextAction(
              if (locating) "Locating…" else "Where I am",
              { if (hasFine(context)) locate() else askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
              Modifier.graphicsLayer { alpha = if (locating) pulse.value else 1f },
              enabled = !locating,
              vertical = 0.dp,
              icon = R.drawable.ic_locate,
            )
            Spacer(Modifier.weight(1f))
            TextAction("Google Maps", { openInMaps(context, if (name.isBlank()) "geo:0,0" else "geo:0,0?q=${Uri.encode(name)}") }, vertical = 0.dp, icon = R.drawable.ic_map)
          }
          Text("In Google Maps: tap the shop, Share, then pick this app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }

  @Composable
  fun PlaceCard(modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
      SectionLabel(if (spots.size == 1) "1 spot" else "${spots.size} spots")
      Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, contentColor = MaterialTheme.colorScheme.onSurface)) {
        // The place as it sits on the ground: every spot inside its radius.
        Box(Modifier.fillMaxWidth().height(168.dp)) {
          SpotMap(spots, radius, Modifier.fillMaxSize())
          OsmCredit(Modifier.align(Alignment.BottomEnd).padding(horizontal = 16.dp, vertical = 10.dp))
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).animateContentSize(tween(Motion.LIST, easing = Motion.EaseUi))) {
          spots.forEachIndexed { index, spot ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
              Text("%02d".format(index + 1), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(32.dp))
              Column(
                Modifier.weight(1f).clickable(onClickLabel = "Open spot ${index + 1} in Maps") {
                  openInMaps(context, "geo:${spot.lat},${spot.lng}?q=${spot.lat},${spot.lng}(${Uri.encode(spot.label.ifBlank { name.ifBlank { "Spot" } })})")
                }
              ) {
                Text(spot.label.ifBlank { Coordinates.format(spot) }, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (spot.label.isNotBlank()) Text(Coordinates.format(spot), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              Spacer(Modifier.width(8.dp))
              TextAction("Remove", { spots = spots.filterNot { it.samePlace(spot) } }, color = MaterialTheme.colorScheme.onSurfaceVariant, onClickLabel = "Remove spot ${index + 1}")
            }
          }
          // Another one of these, right under the ones there are; the search opens only when asked for.
          if (existing != null && !findOpen) TextAction("Add another spot", { findOpen = true }, Modifier.padding(top = 4.dp, bottom = 8.dp), vertical = 0.dp, icon = R.drawable.ic_plus)
        }
      }
      SectionLabel("Radius", Modifier.padding(top = 16.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Place.RADII.forEach { r ->
          FilterChip(r == radius, { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); radius = r }, { Text("$r m") }, shape = MaterialTheme.shapes.medium)
        }
      }
      Text("300 m covers a mall and its car park. 100 m won't go off next door.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }

  Screen(
    bottomBar = {
      if (existing != null) TextAction("Delete", { confirmDelete = true }, color = MaterialTheme.colorScheme.onSurfaceVariant, icon = R.drawable.ic_trash)
      SaveButton(
        enabled = name.isNotBlank() && spots.isNotEmpty(),
        onClick = {
          // Once: the screen is still tappable while it slides away.
          if (saved) return@SaveButton
          saved = true
          val placeId = id ?: data.newId()
          Store.update(context) { it.upsert(Place(placeId, name.trim(), spots, radius)) }
          onSaved(placeId)
        },
        modifier = Modifier.weight(1f),
      )
    }
  ) {
    item { ScreenTitle(if (existing == null) "New place" else "Place", name.ifBlank { "Somewhere" }, Modifier.padding(bottom = 24.dp).rise(arrival[0], 16.dp)) }
    item { Field(name, { name = it }, "Name, like Esselunga", Modifier.fillMaxWidth().rise(arrival[1]), singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)) }
    // The place and what's waiting there are one thing: its list is here too, editable the same way as on Home.
    if (existing != null) {
      item(key = "todo") {
        Column(Modifier.padding(top = 24.dp).animateItem(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          SectionLabel("Things to do here")
          PressCard(onClick = null, onClickLabel = null) {
            Column(Modifier.animateContentSize(tween(Motion.LIST, easing = Motion.EaseUi))) {
              val waitingHere = data.reminders.filter { it.placeId == existing.id }
              if (waitingHere.isEmpty()) Text("Nothing yet. Add the first thing.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
              PlaceChecklist(existing.id, existing.name, waitingHere, onDone = { r ->
                Notifications.cancel(context, existing.id)
                Store.update(context) { it.complete(r.id, java.time.Instant.now(), java.time.ZoneId.systemDefault()) }
              }, onAllDone = null)
            }
          }
        }
      }
    }
    // A place being edited shows what it is first; a new one starts by finding it.
    if (existing != null && spots.isNotEmpty()) {
      item(key = "place") { PlaceCard(Modifier.padding(top = 24.dp).rise(arrival[2]).animateItem()) }
      if (findOpen) item(key = "find") { FindCard("Add another spot", Modifier.padding(top = 24.dp).rise(arrival[3]).animateItem()) }
    } else {
      item(key = "find") { FindCard("Find it", Modifier.padding(top = 24.dp).rise(arrival[2]).animateItem()) }
      if (spots.isNotEmpty()) item(key = "place") { PlaceCard(Modifier.padding(top = 24.dp).rise(arrival[3]).animateItem()) }
    }
  }

  if (confirmDelete && existing != null) {
    ConfirmDelete(
      title = "Delete ${existing.name}?",
      text = when (waiting) {
        0 -> "Its spots go with it."
        1 -> "Its spots go with it, and so does its reminder."
        else -> "Its spots go with it, and so do its $waiting reminders."
      },
      onYes = {
        confirmDelete = false
        deletePlace(context, data, existing.id)
        onClose()
      },
      onNo = { confirmDelete = false },
    )
  }
  if (confirmDiscard) ConfirmDiscard(onDiscard = { confirmDiscard = false; onClose() }, onKeep = { confirmDiscard = false })
}

/** The place and its reminders, with their notifications: a stale Done must not reach a later reminder. */
private fun deletePlace(context: Context, data: Data, id: Int) {
  data.reminders.filter { it.placeId == id }.forEach { Notifications.cancel(context, it.id) }
  Store.update(context) { it.removePlace(id) }
}

/** Google Maps if it's there, any map app otherwise. */
private fun openInMaps(context: Context, geo: String) {
  val intent = Intent(Intent.ACTION_VIEW, Uri.parse(geo))
  runCatching { context.startActivity(Intent(intent).setPackage("com.google.android.apps.maps")) }.recoverCatching { context.startActivity(intent) }
}

private fun hasFine(context: Context) = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** The phone's last known position, if location is allowed and it has one; never waits for a fix. */
@SuppressLint("MissingPermission") // checked first
private suspend fun lastSpot(context: Context): Spot? {
  if (!hasFine(context)) return null
  return suspendCancellableCoroutine { continuation ->
    LocationServices.getFusedLocationProviderClient(context).lastLocation
      .addOnSuccessListener { continuation.resume(it?.let { l -> Spot(l.latitude, l.longitude) }) }
      .addOnFailureListener { continuation.resume(null) }
  }
}

@SuppressLint("MissingPermission") // only called with the fine location permission
private fun currentSpot(context: Context, onResult: (Spot?) -> Unit) {
  LocationServices.getFusedLocationProviderClient(context)
    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
    .addOnSuccessListener { location -> onResult(location?.let { Spot(it.latitude, it.longitude) }) }
    .addOnFailureListener { onResult(null) }
}

/** "Locating…" breathes while the phone looks for a fix; still with animations off. */
@Composable
private fun rememberLocatingPulse(locating: Boolean): State<Float> {
  if (!locating || rememberReducedMotion()) return remember { mutableFloatStateOf(1f) }
  return rememberInfiniteTransition(label = "locating").animateFloat(1f, 0.55f, infiniteRepeatable(tween(900, easing = Motion.EaseLoop), RepeatMode.Reverse), label = "pulse")
}
