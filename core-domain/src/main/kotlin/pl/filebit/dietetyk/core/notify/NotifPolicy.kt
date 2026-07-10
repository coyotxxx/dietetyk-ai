package pl.filebit.dietetyk.core.notify

/**
 * Czysta, testowalna polityka powiadomień (werdykt ja+Fable). Cała decyzja „czy i którym kanałem
 * wysłać" żyje TU, bez Androida — warstwa `:app` (dispatcher `NotificationPolicy`) tylko wykonuje.
 *
 * ZASADY (Fable):
 * - Dwa kanały: WAŻNE (wizyta/red-flag/milestone — nieodłączalne krytyczne) vs CODZIENNE (rutyna).
 * - Poziom intensywności = JEDEN suwak: MINIMAL < BALANCED < COACH. Typ leci tylko gdy poziom ≥ jego próg.
 * - Cisza nocna 21:30–7:00 dotyczy WSZYSTKIEGO (żaden push nie budzi w nocy).
 * - Sufit dzienny liczy TYLKO powiadomienia codzienne (ważne są rzadkie i nie liczą się do limitu).
 */
enum class NotifLevel { MINIMAL, BALANCED, COACH }

/**
 * Typ powiadomienia. `important` = kanał „Ważne" (nieodłączalny). `countsToCap` = liczy się do sufitu
 * dziennego. `minLevel` = najniższy poziom suwaka, na którym typ w ogóle jest aktywny.
 */
enum class NotifKind(val important: Boolean, val countsToCap: Boolean, val minLevel: NotifLevel) {
    RED_FLAG(important = true, countsToCap = false, minLevel = NotifLevel.MINIMAL),
    WEEKLY_VISIT(important = true, countsToCap = false, minLevel = NotifLevel.MINIMAL),
    MILESTONE(important = true, countsToCap = false, minLevel = NotifLevel.BALANCED),
    MORNING(important = false, countsToCap = true, minLevel = NotifLevel.BALANCED),
    MEAL_NUDGE(important = false, countsToCap = true, minLevel = NotifLevel.BALANCED),
    EVENING(important = false, countsToCap = true, minLevel = NotifLevel.BALANCED),
    PRE_MEAL(important = false, countsToCap = true, minLevel = NotifLevel.COACH)
}

data class NotifDecision(val send: Boolean, val channelImportant: Boolean, val reason: String)

object NotifPolicy {
    /** Maksymalna liczba CODZIENNYCH powiadomień na dobę (ważne nie liczą się do sufitu). */
    const val DAILY_CAP = 3

    private const val QUIET_START = 21 * 60 + 30   // 21:30
    private const val QUIET_END = 7 * 60            // 07:00

    /** Cisza nocna 21:30–7:00 (minuta doby 0–1439). */
    fun inQuietHours(minuteOfDay: Int): Boolean =
        minuteOfDay >= QUIET_START || minuteOfDay < QUIET_END

    /**
     * Jedyna reguła decyzyjna. Kolejność bramek: master → poziom → cisza → sufit.
     * @param dailySentCount ile CODZIENNYCH poszło już dziś (do sufitu).
     */
    fun decide(
        kind: NotifKind,
        level: NotifLevel,
        minuteOfDay: Int,
        dailySentCount: Int,
        masterOn: Boolean
    ): NotifDecision {
        val ch = kind.important
        if (!masterOn) return NotifDecision(false, ch, "master_off")
        if (level.ordinal < kind.minLevel.ordinal) return NotifDecision(false, ch, "below_level")
        if (inQuietHours(minuteOfDay)) return NotifDecision(false, ch, "quiet_hours")
        if (kind.countsToCap && dailySentCount >= DAILY_CAP) return NotifDecision(false, ch, "daily_cap")
        return NotifDecision(true, ch, "ok")
    }
}
