package dev.eduarddragu.anotherreminderapp

import android.app.Application
import dev.eduarddragu.anotherreminderapp.data.Store
import dev.eduarddragu.anotherreminderapp.domain.Data
import dev.eduarddragu.anotherreminderapp.triggers.Alarms
import dev.eduarddragu.anotherreminderapp.triggers.Notifications
import dev.eduarddragu.anotherreminderapp.triggers.PlaceWatch

class ReminderApp : Application() {
  override fun onCreate() {
    super.onCreate()
    // Before any receiver runs: they all read the store.
    Store.init(this)
    Notifications.createChannel(this)
    // A force stop or a device transfer leaves no alarms and nothing else puts them back: set them, and resync fences at the next check.
    if (Alarms.scheduleResync(this)) {
      Alarms.sync(this, Data(), Store.data.value)
      PlaceWatch.markNeeded(this)
    }
  }
}
