package com.pricetracker.app.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pricetracker.app.R
import com.pricetracker.app.data.TrackedItem
import com.pricetracker.app.ui.MainActivity

object Notifier {
    const val CHANNEL = "price_alerts"
    fun createChannel(c: Context) {
        val ch = NotificationChannel(CHANNEL, c.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_DEFAULT)
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun drop(c: Context, item: TrackedItem, old: Double, new: Double, cur: String?) =
        show(c, item, c.getString(R.string.notif_drop_title, item.name),
            c.getString(R.string.notif_drop_text, Money.format(old, cur), Money.format(new, cur)))

    fun targetHit(c: Context, item: TrackedItem, new: Double, cur: String?) =
        show(c, item, c.getString(R.string.notif_target_title, item.name),
            c.getString(R.string.notif_target_text, Money.format(new, cur)))

    private fun show(c: Context, item: TrackedItem, title: String, text: String) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val i = Intent(c, MainActivity::class.java).putExtra("itemId", item.id).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(c, item.id.toInt(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_tag).setContentTitle(title)
            .setContentText(text).setContentIntent(pi).setAutoCancel(true).build()
        NotificationManagerCompat.from(c).notify(item.id.toInt(), n)
    }
}
