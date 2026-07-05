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
            "save_profile", "Zapisz/zaktualizuj profil po wywiadzie (wiek, płeć, wzrost, aktywność, cel, zdrowie, preferencje, sprzęt).",
            listOf(AiToolParam("profile", "object", true, "ustrukturyzowany profil zebrany w rozmowie")),
            mutating = true
        ),
        AiToolSpec(
            "calculate_targets", "Policz cel kcal/makro (TDEE Mifflin + adaptacyjny) dla aktualnego profilu. Zwraca liczby i breakdown.",
            readsData = true, emitsNumbers = true
        ),
        AiToolSpec(
            "save_diet_plan", "Zapisz plan dnia/tygodnia. Plan przechodzi walidację (kcal/makro z bazy, HARD constraints) ZANIM się zapisze.",
            listOf(AiToolParam("plan", "object", true, "posiłki per slot z przepisami i składnikami")),
            mutating = true, emitsNumbers = true
        ),
        AiToolSpec(
            "log_meal", "Zapisz zjedzony posiłek (z tekstu, zdjęcia, kodu kreskowego albo wybór z planu).",
            listOf(AiToolParam("meal", "object", true, "opis/pozycje posiłku")), mutating = true, emitsNumbers = true
        ),
        AiToolSpec(
            "log_measurement", "Zapisz pomiar (waga, obwody, tkanka) — także ze zdjęcia wyświetlacza wagi/badań.",
            listOf(AiToolParam("measurement", "object", true, "waga i/lub obwody i/lub % tkanki")), mutating = true
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
