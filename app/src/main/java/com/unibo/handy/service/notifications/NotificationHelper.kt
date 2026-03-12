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

/**
 * Manager centralizzato per le notifiche di sistema ad alta priorità.
 * Gestisce l'interruzione dell'utente in caso di Match positivo sulla rete SamaritanCloud.
 */
@Singleton
class NotificationHelper @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val channelId = "handy_match_channel"

    init {
        createNotificationChannel()
    }

    /**
     * Inizializza il canale di comunicazione.
     * I canali permettono all'utente di personalizzare suoni e vibrazioni dalle impostazioni di sistema.
     */
    private fun createNotificationChannel() {
        val name = "Emergenze Handy"
        val descriptionText = "Notifiche per richieste di aiuto vicine"
        // IMPORTANCE_HIGH è cruciale per forzare il popup a comparsa (Heads-up)
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Lancia una notifica "Heads-up" che interrompe l'utente per avvisarlo di un match.
     */
    fun showMatchNotification(isHelper: Boolean) {
        // Intent esplicito per risvegliare l'app o portarla in primo piano
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        // FLAG_IMMUTABLE garantisce la sicurezza contro l'Intent Hijacking
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        // --- TESTI DINAMICI (Context-Awareness) ---
        val title = if (isHelper) {
            "Richiesta di Aiuto Vicina!"
        } else {
            "Aiuto in arrivo!"
        }

        val messageText = if (isHelper) {
            "Un utente ha bisogno della tua competenza. Tocca per aprire."
        } else {
            "Un lavoratore ha accettato la tua richiesta. Apri l'app per chattare!"
        }

        // Costruzione grafica della notifica
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.handy_icon)
            .setContentTitle(title)
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(1000, 1000, 1000)) // Pattern di vibrazione (Richiede permesso VIBRATE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // ID 1001 fisso: se arrivano più match, la notifica si aggiorna invece di spammare l'utente
        notificationManager.notify(1001, builder.build())
    }
}