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
        return Result.success()
    }

    private companion object {
        const val FALLBACK = "Minął tydzień 👋 Zważ się i napisz, jak minęły ostatnie dni — sprawdzę Twoje postępy i w razie potrzeby dopasuję plan."
    }
}
