package dev.eduarddragu.anotherreminderapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.core.content.edit
import dev.eduarddragu.anotherreminderapp.data.Store
import dev.eduarddragu.anotherreminderapp.domain.MapsShare
import dev.eduarddragu.anotherreminderapp.theme.ReminderTheme
import dev.eduarddragu.anotherreminderapp.triggers.Notifications
import dev.eduarddragu.anotherreminderapp.triggers.PlaceWatch
import dev.eduarddragu.anotherreminderapp.ui.App
import dev.eduarddragu.anotherreminderapp.ui.Problem

class MainActivity : ComponentActivity() {
  private var notifications by mutableStateOf(true)
  private var foreground by mutableStateOf(true)
  private var background by mutableStateOf(true)
  private var share by mutableStateOf<MapsShare?>(null)

  private val prefs by lazy { getSharedPreferences("ui", MODE_PRIVATE) }
  private val ask = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    if (savedInstanceState == null) readShare(intent)
    refresh()
    // Ask once, on the first launch. After that only the Home card asks.
    if (!notifications && !prefs.getBoolean(KEY_ASKED, false)) {
      prefs.edit { putBoolean(KEY_ASKED, true) }
      ask.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
    }
    setContent {
      val data by Store.data.collectAsState()
      val watchProblem by PlaceWatch.problem.collectAsState()
      val problems = buildList {
        if (!notifications) add(Problem("Notifications are off. Reminders are shouting into the void.", "Turn on", ::enableNotifications))
        if (data.reminders.any { it.placeId != null }) {
          when {
            !foreground -> add(Problem("Place reminders need your location. Allow it.", "Allow") { askOrOpenSettings(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION) })
            // Android only grants this from its settings page, which this request opens.
            !background -> add(Problem("Place reminders fire with the app closed. In Settings, pick \u201cAllow all the time\u201d.", "Open Settings") { askOrOpenSettings(Manifest.permission.ACCESS_BACKGROUND_LOCATION) })
            // The location settings only help when location is what's off.
            watchProblem != null -> add(Problem(watchProblem!!, if (watchProblem!!.startsWith("Location")) "Location settings" else "OK") { if (watchProblem!!.startsWith("Location")) startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) else PlaceWatch.problem.value = null })
          }
        }
      }
      ReminderTheme {
        // The Surface sets the content color: without it, text with no color of its own is black even at night.
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { App(problems, share, onShareConsumed = { share = null }) }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    readShare(intent)
  }

  private fun readShare(intent: Intent) {
    // Reopened from Recents, the task replays the share that started it: that one was already handled.
    if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
    if (intent.action == Intent.ACTION_SEND) share = intent.getStringExtra(Intent.EXTRA_TEXT)?.let(MapsShare::parse)
  }

  override fun onResume() {
    super.onResume()
    val hadBackground = background
    refresh()
    // Only when something was wrong or was just fixed: re-registering restarts every dwell in progress,
    // so opening the app at the shop would otherwise cancel that visit's reminder.
    if (PlaceWatch.problem.value != null || PlaceWatch.needsResync(this) || (background && !hadBackground)) PlaceWatch.resync(this, Store.data.value)
  }

  private fun refresh() {
    notifications = Notifications.enabled(this)
    foreground = granted(Manifest.permission.ACCESS_FINE_LOCATION)
    background = granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
  }

  private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

  /** The permission dialog while Android still shows it; after two refusals it no longer does, so the app's settings. */
  private fun askOrOpenSettings(vararg permissions: String) {
    val key = "asked_${permissions.first()}"
    if (shouldShowRequestPermissionRationale(permissions.first()) || !prefs.getBoolean(key, false)) {
      prefs.edit { putBoolean(key, true) }
      ask.launch(arrayOf(*permissions))
    } else {
      startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }
  }

  /** The permission dialog while Android still shows it, then the app's notification settings. */
  private fun enableNotifications() {
    if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) || !prefs.getBoolean(KEY_ASKED, false)) {
      prefs.edit { putBoolean(KEY_ASKED, true) }
      ask.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
    } else {
      startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }
  }

  private companion object {
    const val KEY_ASKED = "asked_notifications"
  }
}
