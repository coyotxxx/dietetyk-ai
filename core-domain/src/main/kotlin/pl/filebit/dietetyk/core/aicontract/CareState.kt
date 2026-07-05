package pl.filebit.dietetyk.core.aicontract

/**
 * Etap opieki dietetycznej — MIĘKKI stan (decyzja Claude+Fable, ARCHITECTURE.md §1).
 * Kod steruje TYLKO: dostępnością narzędzi + listą otwartych celów + warunkiem przejścia.
 * PRZEBIEG ROZMOWY jest w 100% w rękach AI (naturalnie, jedno pytanie na raz, reaguje na emocje).
 */
enum class CareStage {
    INTERVIEW,           // pierwszy wywiad — zbieramy fakty (AI pyta naturalnie)
    CONTRACT_PROPOSAL,   // komplet danych → AI proponuje plan-kontrakt (confirm-first)
    ACTIVE,              // codzienne prowadzenie
    CHECKIN_DUE          // należna wizyta kontrolna (scheduler; AI decyduje KIEDY w rozmowie ją poprowadzi)
}

/** Temat wywiadu = fakt do zebrania. Znika z listy, gdy KOD wykryje, że fakt jest podany. */
enum class InterviewTopic {
    AGE_SEX,             // wiek, płeć
    ANTHROPOMETRICS,     // wzrost, waga
    ACTIVITY,            // poziom aktywności, praca
    HEALTH,              // choroby, leki, alergie/nietolerancje
    LIFESTYLE,           // sen, stres, alkohol, czas na gotowanie, budżet
    PREFERENCES,         // co lubi / czego nie je, liczba posiłków
    KITCHEN_EQUIPMENT,   // Air Fryer / Thermomix / piekarnik…
    GOAL_AND_WHY,        // cel + „dlaczego" (jak dobry dietetyk dopytuje)
    BLOODWORK            // wyniki badań (opcjonalne — zdjęcie → odczyt)
}

/**
 * Stan opieki widziany przez AI. Cele mają PRIORYTET MIĘKKI: gdy user wnosi emocje, AI może
 * zawiesić agendę (`canDeferGoals`) — kod tego nie karze. Wizyta „należna" ≠ „musisz teraz".
 */
data class CareState(
    val stage: CareStage,
    /** Fakty wciąż do zebrania w wywiadzie (puste → można przejść do CONTRACT_PROPOSAL). */
    val openInterviewTopics: List<InterviewTopic> = emptyList(),
    /** Czy scheduler uznał wizytę za należną. */
    val checkInDue: Boolean = false,
    /** Od ilu dni wizyta zalega (podnosi miękki priorytet przypomnienia, NIE blokuje). */
    val checkInOverdueDays: Int = 0
) {
    /** Warunek przejścia INTERVIEW→CONTRACT: komplet faktów (decyduje KOD, nie AI). */
    val interviewComplete: Boolean get() = openInterviewTopics.isEmpty()

    /** AI zawsze ma prawo odłożyć cel etapu, gdy user potrzebuje wsparcia. */
    val canDeferGoals: Boolean get() = true

    /** Miękki priorytet powrotu do wizyty (0 = brak nacisku). Rośnie z zaległością — nigdy blokada. */
    val checkInNudgePriority: Int
        get() = when {
            !checkInDue -> 0
            checkInOverdueDays >= 5 -> 2
            else -> 1
        }
}
