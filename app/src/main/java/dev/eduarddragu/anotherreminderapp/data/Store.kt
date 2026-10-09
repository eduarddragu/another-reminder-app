package dev.eduarddragu.anotherreminderapp.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import dev.eduarddragu.anotherreminderapp.domain.Data
import dev.eduarddragu.anotherreminderapp.domain.Fences
import dev.eduarddragu.anotherreminderapp.triggers.Alarms
import dev.eduarddragu.anotherreminderapp.triggers.PlaceWatch
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json

/** Everything the app knows, as one small JSON file in the app's files. Loaded once per process. */
object Store {
  private val json = Json { ignoreUnknownKeys = true }
  private lateinit var file: AtomicFile
  private val state = MutableStateFlow(Data())
  val data: StateFlow<Data> = state

  fun init(context: Context) {
    val path = File(context.filesDir, "data.json")
    file = AtomicFile(path)
    if (!path.exists()) return
    state.value =
      runCatching { json.decodeFromString(Data.serializer(), file.readFully().decodeToString()) }
        .getOrElse { error ->
          // Keep the unreadable file instead of overwriting it with the next save.
          Log.e("Store", "data.json unreadable, starting empty", error)
          runCatching { path.copyTo(File(context.filesDir, "data.broken-${System.currentTimeMillis()}.json")) }.onFailure { Log.e("Store", "couldn't keep the unreadable file", it) }
          Data()
        }
  }

  /** Saves the change, then moves alarms and geofences to match it. */
  fun update(context: Context, change: (Data) -> Data) {
    val (old, new) =
      synchronized(this) {
        val old = state.value
        val new = change(old)
        if (new == old) return
        val out = file.startWrite()
        try {
          out.write(json.encodeToString(Data.serializer(), new).encodeToByteArray())
          file.finishWrite(out)
        } catch (e: Exception) {
          file.failWrite(out)
          throw e
        }
        state.value = new
        old to new
      }
    Alarms.sync(context, old, new)
    if (Fences.plan(old) != Fences.plan(new)) PlaceWatch.update(context, old, new)
  }
}
