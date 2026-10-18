package io.github.timmylich.wellness.nativebridge

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.app.Notification

/**
 * Local notifications without Firebase or Play services: shown directly, or scheduled with an
 * inexact AlarmManager alarm (no exact-alarm permission needed; Android may delay it a little).
 */
object Reminders {
  private const val CHANNEL_ID = "wellness-reminders"
  private const val EXTRA_ID = "id"
  private const val EXTRA_TITLE = "title"
  private const val EXTRA_BODY = "body"
  private const val EXTRA_URL = "url"

  fun show(c: Context, id: Int, title: String, body: String, url: String?) {
    val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT))
    }
    val open = if (url != null) {
      Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
    } else {
      c.packageManager.getLaunchIntentForPackage(c.packageName)
    }
    val pi = open?.let { PendingIntent.getActivity(c, id, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
    @Suppress("DEPRECATION")
    val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(c, CHANNEL_ID) else Notification.Builder(c)
    val n = b.setSmallIcon(R.drawable.wellness_notification)
      .setContentTitle(title)
      .setContentText(body)
      .setStyle(Notification.BigTextStyle().bigText(body))
      .setAutoCancel(true)
      .setContentIntent(pi)
      .build()
    try {
      nm.notify(id, n)
    } catch (e: SecurityException) {
      Log.w("WellnessReminders", "notification permission missing", e)
    }
  }

  private fun pending(c: Context, id: Int, title: String?, body: String?, url: String?, create: Boolean): PendingIntent? {
    val i = Intent(c, ReminderReceiver::class.java).putExtra(EXTRA_ID, id)
    if (title != null) i.putExtra(EXTRA_TITLE, title).putExtra(EXTRA_BODY, body).putExtra(EXTRA_URL, url)
    val flags = PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
    return PendingIntent.getBroadcast(c, id, i, flags)
  }

  fun schedule(c: Context, id: Int, atMs: Long, title: String, body: String) {
    val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pending(c, id, title, body, null, true)!!)
  }

  fun cancel(c: Context, id: Int) {
    val pi = pending(c, id, null, null, null, false) ?: return
    (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)
    pi.cancel()
  }

  internal fun fire(c: Context, i: Intent) {
    show(c, i.getIntExtra(EXTRA_ID, 0), i.getStringExtra(EXTRA_TITLE) ?: return, i.getStringExtra(EXTRA_BODY) ?: "", i.getStringExtra(EXTRA_URL))
  }
}

class ReminderReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) = Reminders.fire(context, intent)
}
