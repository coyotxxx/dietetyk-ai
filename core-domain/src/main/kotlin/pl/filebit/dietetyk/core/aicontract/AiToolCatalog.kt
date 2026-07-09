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
                AiToolParam("name", "string", false, "imię użytkownika (do spersonalizowanego powitania)"),
                AiToolParam("gender", "enum", true, "płeć: MALE lub FEMALE"),
                AiToolParam("ageYears", "int", true, "wiek w latach"),
                AiToolParam("heightCm", "int", true, "wzrost w cm"),
                AiToolParam("weightKg", "double", false, "aktualna waga w kg"),
                AiToolParam("activityLevel", "enum", false, "aktywność: SEDENTARY (siedzący), LIGHT (lekko aktywny), MODERATE (3-5 treningów), VERY_ACTIVE (6-7 treningów), EXTREME (zawodowo)"),
                AiToolParam("daysPerWeek", "int", false, "liczba treningów/tydzień"),
                AiToolParam("goal", "enum", true, "cel: FAT_LOSS (redukcja), MUSCLE_GAIN (masa), RECOMP (rekompozycja), MAINTAIN (utrzymanie), STRENGTH (siła), ENDURANCE (wydolność), HEALTH (zdrowie), EVENT_PREP (na termin)"),
                AiToolParam("paceKgPerWeek", "double", false, "docelowe tempo zmiany masy w kg/tydzień, wartość dodatnia (np. 0.5)"),
                AiToolParam("goalWeightKg", "double", false, "docelowa waga w kg (do paska postępu w profilu)"),
                AiToolParam("mealsPerDay", "int", false, "preferowana liczba posiłków dziennie (2-8)"),
                AiToolParam("preferences", "string", false, "MIĘKKIE upodobania/uwagi krótko (np. 'lubi kuchnię włoską, mało czasu rano'). To NIE alergie."),
                AiToolParam("allergens", "string", false, "ALERGIE/NIETOLERANCJE oddzielone średnikiem — TWARDE, egzekwowane w planie bezwzględnie (np. 'orzechy; laktoza; gluten'). Podawaj proste kanoniczne słowa. Zawsze zapisz tu każdą alergię/nietolerancję, którą user wymieni."),
                AiToolParam("dietType", "enum", false, "typ diety: STANDARD | VEGETARIAN | VEGAN | PESCATARIAN | KETO | MEDITERRANEAN (twarde ograniczenie doboru produktów)"),
                AiToolParam("varietyMode", "enum", false, "kadencja różnorodności: SAME_DAILY (user woli jeść codziennie podobnie — silnik ułoży jeden dzień na cały tydzień) | VARIED (chce różnorodności między dniami)"),
                AiToolParam("equipment", "string", false, "sprzęt kuchenny użytkownika jako CSV z {airfryer,thermomix} — np. 'airfryer,thermomix' albo 'airfryer'. Puste = tylko kuchenka/piekarnik. Filtruje warianty przepisów.")
            ),
            mutating = true
        ),
        AiToolSpec(
            "calculate_targets", "Policz cel kcal/makro (TDEE Mifflin + adaptacyjny) dla aktualnego profilu. Zwraca liczby i breakdown.",
            readsData = true, emitsNumbers = true
        ),
        AiToolSpec(
            "save_diet_plan", "Zapisz plan JEDNEGO dnia. Składniki podaj STRUKTURALNIE (produkt+gramatura surowa) — silnik SAM przelicza kcal/makro z bazy produktów i sprawdza zgodność z celem. Używaj nazw produktów z bazy (search_products). KADENCJA zależy od profilu (`varietyMode`): przy SAME_DAILY ułóż JEDEN dobry dzień i wywołaj to narzędzie RAZ, BEZ `dayOfWeek` — silnik sam skopiuje ten dzień na cały tydzień (user woli powtarzalność). Przy VARIED wołaj RAZ NA DZIEŃ z `dayOfWeek` 1-7 (7 wywołań) i RÓŻNICUJ posiłki między dniami (nie 7× to samo).",
            listOf(
                AiToolParam("meals", "array", true,
                    "tablica posiłków; każdy: {\"name\":\"Owsianka z malinami\",\"timeHint\":\"7:30\",\"prepMinutes\":10,\"ingredients\":[{\"productName\":\"Płatki owsiane\",\"grams\":60},{\"productName\":\"Mleko 2%\",\"grams\":200},{\"productName\":\"Maliny\",\"grams\":100}]}"),
                AiToolParam("dayOfWeek", "int", false, "dzień tygodnia 1-7 (1=poniedziałek…7=niedziela); pominięty = dzisiejszy dzień")
            ),
            mutating = true, emitsNumbers = true
        ),
        AiToolSpec(
            "log_meal", "Zapisz zjedzony posiłek. Podaj kcal oraz makro (policz je z produktów przez search_products jeśli trzeba).",
            listOf(
                AiToolParam("kcal", "int", true, "kalorie posiłku"),
                AiToolParam("name", "string", false, "krótki opis posiłku"),
                AiToolParam("proteinG", "int", false, "białko w gramach"),
                AiToolParam("carbsG", "int", false, "węglowodany w gramach"),
                AiToolParam("fatG", "int", false, "tłuszcz w gramach")
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
            "add_missing_product", "Pobierz brakujący produkt z OpenFoodFacts (po nazwie lub kodzie kreskowym) i dodaj do bazy lokalnej. Zwraca makro na 100g.",
            listOf(
                AiToolParam("query", "string", false, "nazwa produktu do wyszukania"),
                AiToolParam("barcode", "string", false, "kod kreskowy (dokładniejszy niż nazwa)")
            ),
            mutating = true, emitsNumbers = true
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
        ),
        AiToolSpec(
            "set_food_preference",
            "Zapisz SMAK uzytkownika do konkretnego produktu, GDY DOWIESZ SIE W ROZMOWIE ze cos lubi albo nie je. " +
                "PREFER = lubi/uwielbia (preferuj w planach). AVOID = nie je / nie znosi (NIGDY nie planuj — twardy guardrail). " +
                "Przyklady: user mowi ze nie znosi twarogu -> set_food_preference(twarog, AVOID); uwielbia lososia -> (losos, PREFER). " +
                "To JEDYNE wlasciwe miejsce na smaki — NIE zapisuj ich do notatek. Wolaj proaktywnie gdy tylko wychwycisz preferencje.",
            listOf(
                AiToolParam("product", "string", true, "nazwa produktu (np. twarog, losos)"),
                AiToolParam("preference", "enum", true, "PREFER (lubi) | AVOID (nie je) | NEUTRAL (cofnij)")
            ),
            mutating = true
        ),
        AiToolSpec(
            "remember_context",
            "Zapisz MIEKKI kontekst o zyciu uzytkownika, gdy SAM o nim wspomni: stres w pracy, zly sen, ciezki tydzien, " +
                "nastroj, wydarzenie zyciowe. Wolaj TYLKO gdy user sam to ofiaruje — NIGDY nie wypytuj proaktywnie o emocje/sen/stres. " +
                "Dzieki temu mozesz wrocic do tego naturalnie i z troska pozniej (raz, gdy trafne). Zapisz krotki FAKT, nie interpretacje.",
            listOf(AiToolParam("note", "string", true, "krotki fakt, np. ciezki tydzien w pracy / slabo sypia od kilku dni")),
            mutating = true
        )
    )

    fun byName(name: String): AiToolSpec? = all.firstOrNull { it.name == name }
}
