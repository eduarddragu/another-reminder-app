package dev.eduarddragu.anotherreminderapp.data

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * OpenStreetMap's standard tiles for the place previews, under its tile usage policy: an identifying
 * User-Agent, at most two connections, and a disk cache kept for 30 days so a place is fetched about
 * once. Offline, a cached tile is used however old, and no tile just means no map under the spots.
 */
object Tiles {
  const val USER_AGENT = "another-reminder-app/1 (personal Android app; https://eduarddragu.dev)"
  private const val MAX_AGE = 30L * 24 * 3600 * 1000
  private val memory = LruCache<String, ImageBitmap>(48)
  private val connections = Semaphore(2)

  suspend fun load(context: Context, zoom: Int, x: Int, y: Int): ImageBitmap? {
    val key = "$zoom/$x/$y"
    memory.get(key)?.let { return it }
    return withContext(Dispatchers.IO) {
      val file = File(context.cacheDir, "tiles/$zoom-$x-$y.png")
      val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < MAX_AGE
      val bytes =
        (if (fresh) null else connections.withPermit { fetch(key) })?.also {
          file.parentFile?.mkdirs()
          // Written aside, then renamed: a reader never sees half a tile.
          runCatching { File(file.path + ".part").apply { writeBytes(it) }.renameTo(file) }
        } ?: runCatching { file.readBytes() }.getOrNull()
      bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }?.also { memory.put(key, it) }
    }
  }

  private fun fetch(key: String): ByteArray? =
    runCatching {
        val connection = URL("https://tile.openstreetmap.org/$key.png").openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        try {
          if (connection.responseCode == 200) connection.inputStream.use { it.readBytes() } else null
        } finally {
          connection.disconnect()
        }
      }
      .onFailure { Log.w("Tiles", "tile $key failed", it) }
      .getOrNull()
}
