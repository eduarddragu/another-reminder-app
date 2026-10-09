package dev.eduarddragu.anotherreminderapp

import android.app.Application
import dev.eduarddragu.anotherreminderapp.data.Store
import dev.eduarddragu.anotherreminderapp.triggers.Alarms
import dev.eduarddragu.anotherreminderapp.triggers.Notifications

class ReminderApp : Application() {
  override fun onCreate() {
    super.onCreate()
    // Before any receiver runs: they all read the store.
    Store.init(this)
    Notifications.createChannel(this)
    Alarms.scheduleResync(this)
  }
}
