package pl.filebit.dietetyk.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import pl.filebit.dietetyk.DietetykApp
import pl.filebit.dietetyk.core.aicontract.InsightEngine
import pl.filebit.dietetyk.core.aicontract.InsightType
import pl.filebit.dietetyk.core.notify.NotifKind
import pl.filebit.dietetyk.ui.PlanData
import java.time.LocalDate
import java.time.LocalTime

/**
 * Codzienny „kontakt" dietetyka (werdykt ja+Fable). Odpala się co godzinę i — jeśli trafia w okno —
 * wysyła CO NAJWYŻEJ właściwe powiadomienie przez [NotificationPolicy] (który pilnuje poziomu/ciszy/sufitu).
 *
 * Typy: poranny brief, smart-nudge o posiłku (tylko gdy NIE zalogowany), wieczorne domknięcie — wszystko
 * z SZABLONÓW ([NotifTemplates], zero AI). Plus promocja insightów: red-flag zdrowotny + kamień milowy
 * (kanał „Ważne"). Trudne insighty (plateau/brak-logu) zostają in-app.
 */
class DailyNudgeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as DietetykApp
        if (!app.settings.notificationsEnabled) return Result.success()
        app.profileRepo.get() ?: return Result.success()

        val now = LocalTime.now()
        val today = LocalDate.now()
        val dayKey = "%04d%02d%02d".format(today.year, today.monthValue, today.dayOfMonth)
        val minuteOfDay = now.hour * 60 + now.minute

        // 1) Kanał WAŻNE — red-flag zdrowotny + milestone (promocja z InsightEngine).
        runCatching { promoteInsights(app, dayKey, today) }

        // 2) Kanał CODZIENNY — wymaga planu (z porami posiłków).
        val plan = app.database.planDao().get() ?: return Result.success()
        val meals = PlanData.mealsForDay(plan.planJson, today.dayOfWeek.value)
            ?.mapNotNull { it as? JsonObject } ?: emptyList()
        if (meals.isEmpty()) return Result.success()
        val plannedCount = meals.size
        val timed = meals.mapNotNull { m ->
            val name = m["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            val t = parseTime(m["timeHint"]?.jsonPrimitive?.content)
            if (name != null && t != null) name to t else null
        }

        // Poranny brief: ~30 min przed pierwszym posiłkiem (okno, bo worker tyka co godzinę).
        val firstT = timed.minByOrNull { it.second }?.second
        if (firstT != null && !app.settings.notifMark(dayKey, "morning") && minuteOfDay in (firstT - 45)..(firstT - 10)) {
            val (t, b) = NotifTemplates.morning(plannedCount, plan.targetKcal)
            if (NotificationPolicy.dispatch(app, NotifKind.MORNING, t, b)) app.settings.setNotifMark(dayKey, "morning")
        }

        // Smart-nudge: posiłek 75 min po swojej porze, wciąż PLANNED, limit 2/dzień, raz na posiłek.
        for ((name, t) in timed) {
            if (app.settings.notifNudgeCount(dayKey) >= 2) break
            val mark = "nudge:$name"
            if (minuteOfDay >= t + 75 && !app.settings.notifMark(dayKey, mark)) {
                val status = app.settings.mealStatus(dayKey, name).first
                if (status == "PLANNED") {
                    val (tt, bb) = NotifTemplates.mealNudge(name)
                    if (NotificationPolicy.dispatch(app, NotifKind.MEAL_NUDGE, tt, bb)) {
                        app.settings.setNotifMark(dayKey, mark)
                        app.settings.incNotifNudgeCount(dayKey)
                    }
                }
            }
        }

        // Wieczorne domknięcie: ~20:30, gdy dzień niedokończony lub zaległa waga.
        if (!app.settings.notifMark(dayKey, "evening") && minuteOfDay in (20 * 60 + 15)..(20 * 60 + 55)) {
            val eaten = app.settings.eatenCountForDay(dayKey)
            val weightOverdue = weightOverdue(app)
            if (eaten < plannedCount || weightOverdue) {
                val (t, b) = NotifTemplates.evening(eaten, plannedCount, weightOverdue)
                if (NotificationPolicy.dispatch(app, NotifKind.EVENING, t, b)) app.settings.setNotifMark(dayKey, "evening")
            } else {
                app.settings.setNotifMark(dayKey, "evening") // dzień domknięty — nie sprawdzaj ponownie
            }
        }

        return Result.success()
    }

    /** Red-flag zdrowotny + milestone → push (kanał Ważne). Trudne insighty zostają in-app. */
    private suspend fun promoteInsights(app: DietetykApp, dayKey: String, today: LocalDate) {
        val ctx = app.contextBuilder.build(System.currentTimeMillis()) ?: return
        val cooldowns = InsightType.entries.mapNotNull { t -> app.settings.insightShownDate(t.name)?.let { t to it } }.toMap()
        val insight = InsightEngine.detect(ctx, cooldowns, today) ?: return
        when (insight.type) {
            // Red-flag: raz dziennie (SAFETY ma cooldown 0 = zawsze in-app, więc osobny znacznik na push).
            InsightType.SAFETY -> {
                if (!app.settings.notifMark(dayKey, "redflag_push")) {
                    if (NotificationPolicy.dispatch(app, NotifKind.RED_FLAG, "Zadbajmy o Twoje zdrowie", insight.text)) {
                        app.settings.setNotifMark(dayKey, "redflag_push")
                    }
                }
            }
            // Milestone: świętowanie; cooldown 14 dni współdzielony z in-app (świętujemy raz).
            InsightType.PROTEIN_WIN, InsightType.CONSISTENCY_WIN -> {
                if (NotificationPolicy.dispatch(app, NotifKind.MILESTONE, "Dobra robota 💪", insight.text)) {
                    app.settings.markInsightShown(insight.type.name, today)
                }
            }
            // LOGGING_GAP / WEIGHT_STALL / PROTEIN_GAP — trudne, TYLKO in-app (ekran Dziś).
            else -> Unit
        }
    }

    /** Waga zaległa, gdy ostatni pomiar starszy niż 5 dni (lub brak). */
    private suspend fun weightOverdue(app: DietetykApp): Boolean {
        val last = app.weightRepo.latest()?.dateMs ?: return true
        return System.currentTimeMillis() - last > 5L * 24 * 3600 * 1000
    }

    /** „7:30" / „07.30" → minuta doby. null gdy brak/niepoprawny. */
    private fun parseTime(s: String?): Int? {
        if (s.isNullOrBlank()) return null
        val m = Regex("(\\d{1,2})[:.](\\d{2})").find(s) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].toIntOrNull() ?: return null
        if (h !in 0..23 || min !in 0..59) return null
        return h * 60 + min
    }
}
