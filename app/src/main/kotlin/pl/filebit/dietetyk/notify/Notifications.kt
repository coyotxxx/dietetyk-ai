package pl.filebit.dietetyk.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import pl.filebit.dietetyk.DietetykApp
import pl.filebit.dietetyk.MainActivity
import pl.filebit.dietetyk.data.db.NotificationEntity

/**
 * Kanały + wysyłka powiadomień Dietetyka; każde ląduje też w historii (dzwonek).
 *
 * DWA KANAŁY (werdykt ja+Fable): user może wyciszyć „Codzienne", a „Ważne" (wizyta, red-flag zdrowotny,
 * milestone) i tak dojdą. To właściwa polisa anty-spam — Android pozwala wyciszać per-kanał.
 */
object Notifications {
    const val CHANNEL_IMPORTANT = "coach_important"
    const val CHANNEL_DAILY = "coach_daily"

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = ctx.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_IMPORTANT) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL_IMPORTANT, "Ważne (wizyty, zdrowie)", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Wizyty kontrolne, sygnały zdrowotne, kamienie milowe. Zalecane, by zostawić włączone."
                    }
                )
            }
            if (mgr.getNotificationChannel(CHANNEL_DAILY) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL_DAILY, "Codzienny kontakt", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Poranny plan, przypomnienia o posiłkach, wieczorne podsumowanie."
                    }
                )
            }
        }
    }

    fun hasPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * Niskopoziomowy poster: zapisz do historii i (jeśli można) pokaż powiadomienie systemowe na
     * właściwym kanale. NIE wołaj tego wprost dla powiadomień proaktywnych — idź przez [NotificationPolicy],
     * który egzekwuje poziom/ciszę/sufit. Bezpośrednio tylko dla self-testu w Profilu.
     */
    suspend fun postProactive(app: DietetykApp, title: String, body: String, important: Boolean = true) {
        val id = app.database.notificationDao().insert(
            NotificationEntity(timeMs = System.currentTimeMillis(), title = title, body = body, read = false)
        )
        ensureChannels(app)
        if (!hasPermission(app)) return
        val channel = if (important) CHANNEL_IMPORTANT else CHANNEL_DAILY
        val intent = Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            app, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(app, channel)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(app).notify(id.toInt(), notif) }
    }
}
