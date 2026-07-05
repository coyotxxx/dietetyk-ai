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

/** Kanał + wysyłka powiadomień Dietetyka; każde ląduje też w historii (dzwonek). */
object Notifications {
    const val CHANNEL = "coach_proactive"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = ctx.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Dietetyk", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Proaktywne wiadomości i wizyty kontrolne"
                    }
                )
            }
        }
    }

    fun hasPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Zapisz do historii i (jeśli można) pokaż powiadomienie systemowe. */
    suspend fun postProactive(app: DietetykApp, title: String, body: String) {
        val id = app.database.notificationDao().insert(
            NotificationEntity(timeMs = System.currentTimeMillis(), title = title, body = body, read = false)
        )
        ensureChannel(app)
        if (!hasPermission(app)) return
        val intent = Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            app, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(app, CHANNEL)
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
