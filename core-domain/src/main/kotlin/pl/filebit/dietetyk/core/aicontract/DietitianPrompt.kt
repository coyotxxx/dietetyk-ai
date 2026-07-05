package pl.filebit.dietetyk.core.aicontract

import pl.filebit.dietetyk.core.calc.TdeeSource

/**
 * Renderery promptu dietetyka-AI. Czyste funkcje (testowalne) — zamieniają [DietitianContext] na tekst.
 *
 * Ucieleśnia dyrektywy Macieja: AI DECYDUJE o wszystkim i widzi WSZYSTKIE dane (kontekst poniżej),
 * rozmawia NATURALNIE jak człowiek (PLAN.md 2.7a), a wszystkie LICZBY bierze z narzędzi (nie wymyśla).
 */
object DietitianPrompt {

    /** System prompt: persona + proces opieki + twarde zasady. Krótki i stabilny (prompt caching). */
    fun systemPrompt(): String = """
        Jesteś dietetykiem prowadzącym użytkownika w aplikacji „Dietetyk AI". Zachowujesz się jak
        prawdziwy dietetyk z bardzo dobrym kontaktem z pacjentem — nie jak asystent czy bot.

        JAK ROZMAWIASZ (bezwzględnie):
        - Mówisz jak człowiek: krótko (1–3 zdania), zero list i nagłówków w czacie, zero „jako AI".
        - Piszesz ZWYKŁYM tekstem — bez markdown: żadnych gwiazdek (**pogrubienie**), krzyżyków (#), myślników jako listy.
        - JEDNO pytanie na raz. Nigdy ankieta w jednej wiadomości.
        - Reagujesz na emocje: frustracja → najpierw wsparcie, potem merytoryka; sukces → szczera radość;
          wstyd po odstępstwie → normalizacja bez moralizowania.
        - Pamiętasz i nawiązujesz do wcześniejszych rozmów (masz notatki w kontekście).
        - Ciepły, konkretny, czasem lekki humor. Emoji z umiarem (0–1 na wiadomość).
        - Język ludzki, nie żargon: „spalasz mniej, niż myślisz" zamiast „TDEE", „trzymanie planu" zamiast „adherence".

        JAK DECYDUJESZ:
        - TY decydujesz o wszystkim: strategii, planie, korektach, tonie, tempie rozmowy.
        - Ale WSZYSTKIE LICZBY bierzesz z narzędzi (calculate_targets, run_checkin, propose_adjustment,
          search_products…). NIGDY nie podajesz kcal/makro/wagi „z głowy".
        - Korektę celu proponujesz KIERUNKIEM i SIŁĄ (propose_adjustment) — bezpieczną wartość liczy kod.
        - Confirm-first: plan i istotne zmiany user akceptuje, zanim je zapiszesz.

        ZDROWIE PONAD WSZYSTKO:
        - Jeśli kontekst mówi „SKIERUJ DO LEKARZA" — mówisz to wprost, z troską, i NIE prowadzisz dalej
          w tym obszarze. Tego nie wolno Ci zagadać. Nie proponujesz diet <1400/1200 kcal ani głodówek.

        PROCES: wywiad (poznaj osobę, jedno pytanie na raz) → plan-kontrakt → codzienne prowadzenie →
        cotygodniowa wizyta kontrolna. Stan opieki masz w kontekście — trzymaj się celu etapu, ale gdy
        user potrzebuje rozmowy, odłóż agendę (defer_goal). Wizyta „należna" nie znaczy „przepytaj teraz".
    """.trimIndent()

    /** Render pełnego kontekstu — WSZYSTKIE dane, które AI widzi w tej rozmowie. */
    fun renderContext(ctx: DietitianContext): String = buildString {
        appendLine("=== KONTEKST (dane policzone przez aplikację — Twoje jedyne źródło liczb) ===")

        // Stan opieki
        appendLine(renderCareGuidance(ctx.careState))

        // Bezpieczeństwo — nadrzędne
        if (ctx.referToDoctor) {
            appendLine("⚠️ SKIERUJ DO LEKARZA: ${ctx.redFlagMessage}")
            appendLine("   → Powiedz to z troską i NIE prowadź diety w tym obszarze.")
        }

        // Kim jest
        val p = ctx.profile
        appendLine("Osoba: ${p.gender}, ${p.ageYears} lat, ${p.heightCm} cm" +
            (ctx.latestWeightKg?.let { ", waga ${it} kg" } ?: (p.weightKg?.let { ", waga ~$it kg (z profilu)" } ?: "")) +
            ", cel: ${p.goal}, aktywność: ${p.activityLevel}.")
        if (ctx.clinical.conditions.isNotEmpty())
            appendLine("Zdrowie: ${ctx.clinical.conditions.joinToString(", ")}.")
        if (ctx.memoryNotes.isNotEmpty()) {
            appendLine("Pamiętasz o tej osobie:")
            ctx.memoryNotes.forEach { appendLine("  - $it") }
        }

        // Cel / kontrakt
        ctx.currentGoal?.let { g ->
            appendLine("Aktualny cel: ${g.kcal} kcal (B ${g.proteinG} / W ${g.carbsG} / T ${g.fatG} g). ${g.breakdown.deficitLabel}.")
            if (g.safetyWarnings.isNotEmpty()) appendLine("  Uwagi bezpieczeństwa: ${g.safetyWarnings.joinToString(" ")}")
        }
        ctx.tdeeEstimate?.let { t ->
            when (t.source) {
                TdeeSource.FORMULA_ONLY -> appendLine("Metabolizm: na razie ze wzoru (${t.formulaTdeeKcal} kcal). ${t.note}")
                else -> appendLine("Metabolizm (realny): ${t.tdeeKcal} kcal, pewność ${(t.confidence * 100).toInt()}%. ${t.note}")
            }
        }

        // Pomiary / trend
        if (ctx.weightTrend.hasEnoughData) {
            val slope = ctx.weightTrend.slopeKgPerWeek
            appendLine("Trend wagi: ${ctx.weightTrend.direction}" + (slope?.let { ", %.2f kg/tydz".format(it) } ?: "") + ".")
        }

        // Prowadzenie
        appendLine("Trzymanie planu (14d): kcal ${ctx.adherence14d.avgKcalPct}%, białko ${ctx.adherence14d.avgProteinPct}%, " +
            "dni z pełnym logiem: ${ctx.completeLogDays14d}." + (ctx.daysSinceLastLog?.let { " Ostatni log: $it dni temu." } ?: ""))
        val today = ctx.today
        if (today.mealsPlanned > 0 || today.kcalConsumed > 0)
            appendLine("Dziś: ${today.kcalConsumed} kcal, białko ${today.proteinConsumedG} g, posiłki ${today.mealsEaten}/${today.mealsPlanned}, woda ${today.waterMl}/${today.waterTargetMl} ml.")

        // Ostatnia wizyta
        ctx.lastCheckIn?.let { appendLine("Ostatnia wizyta: ${it.verdict} — ${it.headline}") }
    }

    /** Wskazówka co robić na tym etapie opieki (cel etapu — NIE skrypt rozmowy). */
    fun renderCareGuidance(state: CareState): String = when (state.stage) {
        CareStage.INTERVIEW -> buildString {
            append("Etap: WYWIAD. Zbierz naturalnie (jedno pytanie na raz, w dowolnej kolejności): ")
            append(state.openInterviewTopics.joinToString(", ") { topicLabel(it) })
            append(". Gdy komplet — zaproponuj plan.")
        }
        CareStage.CONTRACT_PROPOSAL -> "Etap: PROPOZYCJA PLANU. Masz komplet danych — ułóż plan-kontrakt i poproś o akceptację (confirm-first)."
        CareStage.ACTIVE -> buildString {
            append("Etap: PROWADZENIE. Codzienny nadzór, komentarze, odpowiedzi na pytania.")
            if (state.checkInDue) append(nudge(state.checkInNudgePriority))
        }
        CareStage.CHECKIN_DUE -> buildString {
            append("Etap: WIZYTA NALEŻNA. Najpierw zapytaj jak minął tydzień (samopoczucie/głód/energia), " +
                "POTEM uruchom run_checkin i omów werdykt.")
            append(nudge(state.checkInNudgePriority))
        }
    }

    private fun nudge(priority: Int): String = when (priority) {
        2 -> " Wizyta zalega — delikatnie, ale konkretnie wróć do niej, jeśli rozmowa pozwala."
        1 -> " Jeśli rozmowa na to pozwala, delikatnie zaproponuj wizytę."
        else -> ""
    }

    private fun topicLabel(t: InterviewTopic): String = when (t) {
        InterviewTopic.AGE_SEX -> "wiek i płeć"
        InterviewTopic.ANTHROPOMETRICS -> "wzrost i waga"
        InterviewTopic.ACTIVITY -> "aktywność i praca"
        InterviewTopic.HEALTH -> "zdrowie (choroby/leki/alergie)"
        InterviewTopic.LIFESTYLE -> "styl życia (sen/stres/alkohol/czas na gotowanie/budżet)"
        InterviewTopic.PREFERENCES -> "preferencje (co lubi/czego nie je, liczba posiłków)"
        InterviewTopic.KITCHEN_EQUIPMENT -> "sprzęt kuchenny"
        InterviewTopic.GOAL_AND_WHY -> "cel i dlaczego"
        InterviewTopic.BLOODWORK -> "wyniki badań (opcjonalnie)"
    }
}
