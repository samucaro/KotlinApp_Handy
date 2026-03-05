package com.unibo.handy.service.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.unibo.handy.MainActivity
import com.unibo.handy.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val channelId = "handy_match_channel"

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        // A partire da Android 8 (Oreo) i canali di notifica sono obbligatori
        val name = "Emergenze Handy"
        val descriptionText = "Notifiche per richieste di aiuto vicine"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    fun showMatchNotification() {
        // Questo intent decide cosa succede quando l'utente tocca la notifica
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        // Costruzione grafica della notifica
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("Richiesta di Aiuto Vicina!")
            .setContentText("Un utente ha bisogno della tua competenza. Tocca per aprire.")
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Forza la comparsa a schermo (Heads-up)
            .setVibrate(longArrayOf(1000, 1000, 1000)) // Fa vibrare il telefono
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // Scompare quando viene toccata

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1001, builder.build())
    }
}