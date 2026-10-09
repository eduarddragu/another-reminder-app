package dev.eduarddragu.anotherreminderapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.eduarddragu.anotherreminderapp.data.Store
import dev.eduarddragu.anotherreminderapp.domain.MapsShare
import dev.eduarddragu.anotherreminderapp.theme.Motion

sealed interface Route {
  data object Home : Route

  data class EditReminder(val id: Int?) : Route

  data object Places : Route

  data class EditPlace(val id: Int?) : Route

  companion object {
    /** Routes as strings, so the back stack survives the activity being recreated or the process dying. */
    val StackSaver =
      listSaver<List<Route>, String>(
        save = { stack ->
          stack.map {
            when (it) {
              Home -> "home"
              Places -> "places"
              is EditReminder -> "reminder:${it.id ?: ""}"
              is EditPlace -> "place:${it.id ?: ""}"
            }
          }
        },
        restore = { saved ->
          saved.map {
            val id = it.substringAfter(':', "").toIntOrNull()
            when (it.substringBefore(':')) {
              "reminder" -> EditReminder(id)
              "place" -> EditPlace(id)
              "places" -> Places
              else -> Home
            }
          }
        },
      )
  }
}

/** Four screens on a plain back stack: not enough to need a navigation library. */
@Composable
fun App(problems: List<Problem>, share: MapsShare?, onShareConsumed: () -> Unit) {
  val data by Store.data.collectAsStateWithLifecycle()
  var stack by rememberSaveable(stateSaver = Route.StackSaver) { mutableStateOf(listOf<Route>(Route.Home)) }
  var forward by remember { mutableStateOf(true) }
  // A place created from the reminder editor, for the editor to select when it's back on top.
  var pickedPlace by rememberSaveable { mutableStateOf<Int?>(null) }
  val saved = rememberSaveableStateHolder()

  fun push(route: Route) {
    forward = true
    stack = stack + route
  }
  fun pop() {
    if (stack.size < 2) return
    forward = false
    saved.removeState(stack.last().toString())
    stack = stack.dropLast(1)
  }
  BackHandler(stack.size > 1, ::pop)
  // A spot shared from Google Maps goes to the place being edited, or starts a new one.
  LaunchedEffect(share) { if (share != null && stack.last() !is Route.EditPlace) push(Route.EditPlace(null)) }

  AnimatedContent(
    stack.last(),
    transitionSpec = {
      // As the habit tracker: the push takes 520 ms to match the slow Home, the way back is quicker.
      val spec = tween<IntOffset>(if (forward) Motion.ENTRANCE else 340, easing = Motion.EaseScreen)
      (if (forward) slideInHorizontally(spec) { it } togetherWith slideOutHorizontally(spec) { -it / 5 }
      else slideInHorizontally(spec) { -it / 5 } togetherWith slideOutHorizontally(spec) { it })
        .apply { targetContentZIndex = if (forward) 1f else -1f }
    },
    label = "screens",
  ) { route ->
    saved.SaveableStateProvider(route.toString()) {
      when (route) {
        Route.Home -> HomeScreen(data, problems, onOpen = { push(Route.EditReminder(it)) }, onPlaces = { push(Route.Places) }, onOpenPlace = { push(Route.EditPlace(it)) })
        is Route.EditReminder -> ReminderEditor(data, route.id, pickedPlace, onPickedConsumed = { pickedPlace = null }, onNewPlace = { push(Route.EditPlace(null)) }, onClose = ::pop)
        Route.Places -> PlacesScreen(data, onOpen = { push(Route.EditPlace(it)) })
        is Route.EditPlace ->
          PlaceEditor(
            data,
            route.id,
            incoming = share,
            onIncomingConsumed = onShareConsumed,
            onSaved = { id ->
              val below = stack.dropLast(1)
              if (route.id == null && below == listOf(Route.Home)) {
                // A new place straight from Home (a Maps share): stay on it, now saved, where its list is written.
                saved.removeState(route.toString())
                stack = below + Route.EditPlace(id)
              } else {
                pop()
                if (stack.last() is Route.EditReminder) pickedPlace = id
              }
            },
            onClose = ::pop,
          )
      }
    }
  }
}
