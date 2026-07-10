package pl.filebit.dietetyk.notify

import pl.filebit.dietetyk.DietetykApp
import pl.filebit.dietetyk.core.notify.NotifKind
import pl.filebit.dietetyk.core.notify.NotifLevel
import pl.filebit.dietetyk.core.notify.NotifPolicy
import java.time.LocalDate
import java.time.LocalTime

/**
 * Dispatcher powiadomień — JEDYNE miejsce w apce, które wolno użyć do proaktywnego pushu (poza self-testem).
 * Egzekwuje politykę [NotifPolicy] (poziom/cisza/sufit), routuje na właściwy kanał i księguje sufit dzienny.
 *
 * Wzorzec „jeden werdykt mózgu" (nauka z GymTrackera v2.65 — przecieki, bo push szedł z surowej karty,
 * nie z dispatchera). Każdy nowy typ powiadomienia przechodzi TĘDY albo nie istnieje.
 */
object NotificationPolicy {

    private fun level(app: DietetykApp): NotifLevel =
        runCatching { NotifLevel.valueOf(app.settings.notifIntensity) }.getOrDefault(NotifLevel.BALANCED)

    private fun dayKey(d: LocalDate) = "%04d%02d%02d".format(d.year, d.monthValue, d.dayOfMonth)

    /**
     * Spróbuj wysłać powiadomienie danego typu. Zwraca true jeśli poszło.
     * Kanał codzienny NIGDY nie woła API — treść (title/body) to gotowy szablon budowany przez wołającego.
     */
    suspend fun dispatch(app: DietetykApp, kind: NotifKind, title: String, body: String): Boolean {
        val now = LocalDate.now()
        val key = dayKey(now)
        val minuteOfDay = LocalTime.now().let { it.hour * 60 + it.minute }
        val decision = NotifPolicy.decide(
            kind = kind,
            level = level(app),
            minuteOfDay = minuteOfDay,
            dailySentCount = app.settings.notifDailyCount(key),
            masterOn = app.settings.notificationsEnabled
        )
        if (!decision.send) return false
        Notifications.postProactive(app, title, body, important = decision.channelImportant)
        if (kind.countsToCap) app.settings.incNotifDailyCount(key)
        return true
    }
}
