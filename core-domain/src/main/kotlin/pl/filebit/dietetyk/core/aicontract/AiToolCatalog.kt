package pl.filebit.dietetyk.core.aicontract

/** Parametr narzędzia (opis semantyczny — warstwa `:ai` renderuje to do formatu JSON Claude). */
data class AiToolParam(
    val name: String,
    val type: String,          // "string" | "int" | "double" | "bool" | "enum" | "object" | "array"
    val required: Boolean,
    val description: String
)

/**
 * Specyfikacja narzędzia AI. `readsData=true` → daje AI dostęp do danych; `mutating=true` → zmienia stan.
 * `emitsNumbers=true` → narzędzie ZWRACA policzone liczby (jedyne źródło liczb dla AI).
 */
data class AiToolSpec(
    val name: String,
    val description: String,
    val params: List<AiToolParam> = emptyList(),
    val readsData: Boolean = false,
    val mutating: Boolean = false,
    val emitsNumbers: Boolean = false
)

/**
 * Kanoniczny katalog narzędzi dietetyka-AI (PLAN.md §3.4 + care). Realizuje dyrektywę „AI ma dostęp do
 * WSZYSTKICH danych i akcji". Warstwa `:ai` mapuje to na tool-use Claude + podpina handlery do `:data`.
 *
 * GUARDRAIL STRUKTURALNY (Fable): narzędzia MUTUJĄCE nie przyjmują surowych liczb bezwzględnych tam,
 * gdzie liczy je kod — `propose_adjustment(direction, magnitude)` zamiast `set_kcal(1850)`. Kod (silnik
 * korekt + SafetyGuard) wylicza i clampuje faktyczną wartość. AI wybiera SPOŚRÓD bezpiecznych opcji.
 */
object AiToolCatalog {

    val all: List<AiToolSpec> = listOf(
        AiToolSpec(
            "save_profile", "Zapisz/zaktualizuj profil po zebraniu danych w wywiadzie. Podaj tyle pól, ile znasz.",
            listOf(
                AiToolParam("gender", "enum", true, "płeć: MALE lub FEMALE"),
                AiToolParam("ageYears", "int", true, "wiek w latach"),
                AiToolParam("heightCm", "int", true, "wzrost w cm"),
                AiToolParam("weightKg", "double", false, "aktualna waga w kg"),
                AiToolParam("activityLevel", "enum", false, "aktywność: SEDENTARY (siedzący), LIGHT (lekko aktywny), MODERATE (3-5 treningów), VERY_ACTIVE (6-7 treningów), EXTREME (zawodowo)"),
                AiToolParam("daysPerWeek", "int", false, "liczba treningów/tydzień"),
                AiToolParam("goal", "enum", true, "cel: FAT_LOSS (redukcja), MUSCLE_GAIN (masa), RECOMP (rekompozycja), MAINTAIN (utrzymanie), STRENGTH (siła), ENDURANCE (wydolność), HEALTH (zdrowie), EVENT_PREP (na termin)"),
                AiToolParam("paceKgPerWeek", "double", false, "docelowe tempo zmiany masy w kg/tydzień, wartość dodatnia (np. 0.5)")
            ),
            mutating = true
        ),
        AiToolSpec(
            "calculate_targets", "Policz cel kcal/makro (TDEE Mifflin + adaptacyjny) dla aktualnego profilu. Zwraca liczby i breakdown.",
            readsData = true, emitsNumbers = true
        ),
        AiToolSpec(
            "save_diet_plan", "Zapisz plan dnia. Suma kcal posiłków powinna być bliska celowi (calculate_targets).",
            listOf(
                AiToolParam("meals", "array", true,
                    "tablica posiłków; każdy: {\"name\":\"Owsianka z malinami\",\"timeHint\":\"7:30\",\"kcal\":550,\"proteinG\":30,\"carbsG\":70,\"fatG\":12,\"ingredients\":\"płatki 60g, mleko 200ml, maliny 100g\"}")
            ),
            mutating = true, emitsNumbers = true
        ),
        AiToolSpec(
            "log_meal", "Zapisz zjedzony posiłek. Podaj szacowane kcal (policz je sam z produktów przez search_products jeśli trzeba).",
            listOf(
                AiToolParam("kcal", "int", true, "kalorie posiłku"),
                AiToolParam("name", "string", false, "krótki opis posiłku")
            ),
            mutating = true, emitsNumbers = true
        ),
        AiToolSpec(
            "log_measurement", "Zapisz pomiar ciała (waga i/lub obwody) — także odczytany ze zdjęcia wagi.",
            listOf(
                AiToolParam("weightKg", "double", true, "waga w kg"),
                AiToolParam("waistCm", "double", false, "obwód talii w cm"),
                AiToolParam("neckCm", "double", false, "obwód karku w cm")
            ),
            mutating = true
        ),
        AiToolSpec(
            "get_history", "Pobierz historię: logi posiłków, pomiary, trend wagi, poprzednie wizyty, pamięć epizodyczną.",
            listOf(AiToolParam("range", "string", false, "np. 7d/14d/30d/all"), AiToolParam("kind", "enum", false, "meals|weight|visits|memory|all")),
            readsData = true, emitsNumbers = true
        ),
        AiToolSpec(
            "search_products", "Znajdź produkt w bazie lokalnej + OpenFoodFacts (po nazwie lub kodzie kreskowym).",
            listOf(AiToolParam("query", "string", true, "nazwa lub kod"), AiToolParam("barcode", "string", false, "opcjonalny kod")),
            readsData = true, emitsNumbers = true
        ),
        AiToolSpec(
            "add_missing_product", "Dodaj brakujący produkt (auto z OpenFoodFacts) do bazy lokalnej.",
            listOf(AiToolParam("product", "object", true, "nazwa + makro per 100g surowego")), mutating = true
        ),
        AiToolSpec(
            "generate_shopping_list", "Zbuduj listę zakupów z aktualnego planu tygodnia (produkty surowe, pogrupowane).",
            readsData = true
        ),
        AiToolSpec(
            "run_checkin", "Uruchom wizytę kontrolną: analiza trendu+adherence+red-flag → werdykt (HOLD/ADJUST/DIET_BREAK/REFER).",
            readsData = true, emitsNumbers = true
        ),
        AiToolSpec(
            "propose_adjustment", "Zaproponuj korektę celu KIERUNKIEM i SIŁĄ — kod policzy i zclampuje bezpieczną wartość kcal.",
            listOf(
                AiToolParam("direction", "enum", true, "increase|decrease"),
                AiToolParam("magnitude", "enum", true, "small|medium")
            ),
            mutating = true, emitsNumbers = true
        ),
        AiToolSpec(
            "schedule_checkin", "Umów wizytę kontrolną / przypomnienie na wskazany termin.",
            listOf(AiToolParam("whenDays", "int", true, "za ile dni")), mutating = true
        ),
        AiToolSpec(
            "save_visit_note", "Zapisz ustrukturyzowaną notatkę z wizyty do pamięci epizodycznej (ustalenia, przeszkody, obietnice).",
            listOf(AiToolParam("note", "string", true, "skrót ustaleń — kartoteka pacjenta")), mutating = true
        ),
        AiToolSpec(
            "defer_goal", "Zawieś cel bieżącego etapu, bo user potrzebuje wsparcia/rozmowy (kod tego nie karze).",
            listOf(AiToolParam("reason", "string", true, "dlaczego odkładamy agendę")), mutating = false
        )
    )

    fun byName(name: String): AiToolSpec? = all.firstOrNull { it.name == name }
}
