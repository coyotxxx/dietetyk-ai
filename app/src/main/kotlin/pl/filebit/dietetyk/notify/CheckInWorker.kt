package pl.filebit.dietetyk.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import pl.filebit.dietetyk.DietetykApp
import pl.filebit.dietetyk.ai.ClaudeHttpApi
import pl.filebit.dietetyk.ai.OneShot

/**
 * Proaktywna wizyta kontrolna (tygodniowa). Dietetyk sam się odzywa: buduje kontekst,
 * prosi AI o krótką ciepłą wiadomość (fallback = szablon), wysyła powiadomienie + do historii.
 */
class CheckInWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as DietetykApp
        if (!app.settings.notificationsEnabled) return Result.success()
        // Bez profilu nie ma o czym przypominać.
        app.profileRepo.get() ?: return Result.success()

        val now = System.currentTimeMillis()
        val dctx = app.contextBuilder.build(now)
        val goalKcal = dctx?.currentGoal?.kcal
        val lastWeight = dctx?.latestWeightKg

        val body = runCatching {
            val summary = buildString {
                append("Tygodniowy check-in. ")
                if (goalKcal != null) append("Cel: $goalKcal kcal. ")
                if (lastWeight != null) append("Ostatnia zapisana waga: $lastWeight kg. ")
                append("Napisz krótką (1-2 zdania), ciepłą, konkretną wiadomość zachęcającą do zważenia się i podsumowania tygodnia. Bez markdown i bez powitań na start.")
            }
            OneShot.ask(
                ClaudeHttpApi(app.settings.apiKey),
                system = "Jesteś empatycznym, konkretnym dietetykiem. Piszesz po polsku, zwykłym tekstem, maksymalnie 2 zdania.",
                user = summary,
                maxTokens = 200
            ).takeIf { it.isNotBlank() } ?: FALLBACK
        }.getOrDefault(FALLBACK)

        Notifications.postProactive(app, "Dietetyk — wizyta kontrolna", body)

        // Zapis wizyty do historii (idempotentnie — najwyżej 1 na dzień).
        runCatching {
            val dao = app.database.visitDao()
            val last = dao.latestDateMs()
            if (last == null || !isSameDay(last, now)) {
                val recent = app.weightRepo.since(now - 9L * 24 * 3600 * 1000)
                val delta = if (recent.size >= 2)
                    recent.maxByOrNull { it.dateMs }!!.weightKg - recent.minByOrNull { it.dateMs }!!.weightKg else null
                dao.insert(
                    pl.filebit.dietetyk.data.db.VisitEntity(
                        dateMs = now, deltaKg = delta, adherencePct = adherence7d(app), decisionText = body
                    )
                )
            }
        }
        return Result.success()
    }

    private fun isSameDay(a: Long, b: Long): Boolean {
        val z = java.time.ZoneId.systemDefault()
        return java.time.Instant.ofEpochMilli(a).atZone(z).toLocalDate() ==
            java.time.Instant.ofEpochMilli(b).atZone(z).toLocalDate()
    }

    private fun adherence7d(app: DietetykApp): Int? {
        val today = java.time.LocalDate.now()
        val meals = app.settings.mealsPerDay
        var eaten = 0; var active = 0
        for (i in 0 until 7) {
            val d = today.minusDays(i.toLong())
            val c = app.settings.eatenCountForDay("%04d%02d%02d".format(d.year, d.monthValue, d.dayOfMonth))
            if (c > 0) { eaten += c; active++ }
        }
        return if (active >= 1 && meals > 0) (eaten * 100 / (active * meals)).coerceIn(0, 100) else null
    }

    private companion object {
        const val FALLBACK = "Minął tydzień 👋 Zważ się i napisz, jak minęły ostatnie dni — sprawdzę Twoje postępy i w razie potrzeby dopasuję plan."
    }
}
