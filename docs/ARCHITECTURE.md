# Dietetyk AI — Architektura silnika (decyzja wspólna Claude + Fable 5)
> 2026-07-04. Wynik dwurundowej burzy mózgów dwóch modeli. Zatwierdzone decyzje właściciela na dole.

## Werdykt naczelny
**Port zwalidowanych algorytmów, greenfield modeli i orkiestracji.** Nie „wszystko od zera",
nie „przenieś wszystko". Linia podziału:
> Portuj 1:1 wszystko, co jest **zakodowaną wiedzą walidowaną produkcją**.
> Przepisz od zera wszystko, co jest **kształtem apki treningowej** (modele, proces, kontekst AI).

Silnik liczbowy GymTrackera (~1270 linii czystej logiki + setki testów, 2,5 roku poprawek edge-case)
jest dobry i dostajemy go tanio. Cała NOWA praca projektowa idzie w to, czego GymTracker nie ma:
**proces opieki, pamięć epizodyczna (kartoteka), warstwa kliniczna, mikroskładniki.**

### Ochrona przed regresją portu: golden-diff
Te same wejścia przez stary kod GymTrackera i nowy `:core-domain` → wynik identyczny co do kcal.
Port testów RAZEM z kodem (nigdy „testy później"). Property-based na guardraile.

## Podział prac (plan, nie opcje)
| Element | Decyzja |
|---------|---------|
| `SafetyGuard`, `MedicalFlagger`, `TrendAnalyzer`, `BodyFatEstimator`, `MealSlots`, enumy | **PORT 1:1 + testy** |
| `DietGoals.computeDailyGoal` | **PORT algorytmu, NOWA sygnatura** (wejście: czysty `NutritionProfile`, nie Room; wywalić parametry treningowe → ogólny `activityEnergyKcal`) |
| `CalorieAdjustmentEngine` | **PORT reguł** + osadzić w jawnym `AdaptiveTdeeEstimator` |
| `AiMealJsonValidator`, `ConstraintResolver` | **PORT + oczyszczenie z javax.inject** |
| Modele domenowe | **GREENFIELD** — `NutritionProfile`, `ClinicalContext`, `WeightSample`, `DayLog`, `FoodItem`, `Meal`, `MealPlan`, `CarePlan` |
| `MasterAiContextBuilder` + orkiestracja AI | **GREENFIELD** (kontekst dietetyka ≠ trenera) |
| `ProductResolver`/`OpenFoodFactsClient` | PORT do `:app` za interfejsem `FoodCatalog` z core |

**Świadoma decyzja:** NIE robimy współdzielonej biblioteki dla GymTrackera i Dietetyka — fork z adnotacją
pochodzenia; poprawki bezpieczeństwa przenosimy ręcznie w obie strony.

## Docelowa struktura `:core-domain` (czysty Kotlin/JVM, zero Androida)
```
model/     NutritionProfile, ClinicalContext, WeightSample, DayLog, FoodItem, Meal, MealPlan,
           MealSlot(2–8), CarePlan(=KONTRAKT: cel, tempo, kcal/makro, faza, data wizyty)
calc/      EnergyCalculator(Mifflin, „tylko start"), AdaptiveTdeeEstimator(NOWY),
           MacroPlanner, TrendAnalyzer(port), BodyFatEstimator, GoalPipeline(→ GoalResult z breakdownem)
safety/    SafetyGuard(port), MedicalFlagger(port), RedFlagDetector(NOWY → REFER_TO_DOCTOR)
adapt/     CalorieAdjustmentEngine(port), CheckInEngine(NOWY: silnik wizyty tygodniowej →
           werdykt HOLD/ADJUST/PHASE_CHANGE/DIET_BREAK/REFER; AI go KOMUNIKUJE)
plan/      PlanValidator(port), ConstraintResolver(port), clinical/(PÓŹNIEJ: DASH/IR/Hashimoto/FODMAP)
micro/     MicronutrientNorms(PÓŹNIEJ: EFSA/NCEŻ), MicroGapAnalyzer(PÓŹNIEJ)
aicontract/ tools(schematy DTO), context(DietitianContext), prompt(renderery czyste)

:app  Room + mappery, Hilt, Compose, AiToolHandler(Claude tool use → funkcje core), OFF, notyfikacje
```
Reguła twarda: **encja Room NIGDY nie jest parametrem funkcji core.** `:core-domain` bez zależności na `:app`
(wymuszone Gradle, nie dyscypliną).

## Cztery rozstrzygnięcia (obie rundy, konsensus)
1. **Proces opieki = miękki stan, nie skrypt.** FSM steruje TYLKO: (a) dostępnością narzędzi,
   (b) listą OTWARTYCH CELÓW etapu (fakty do zebrania/decyzje — AI zbiera naturalnie, jedno pytanie na raz,
   w dowolnej kolejności; cel znika gdy KOD wykryje że fakt podany), (c) warunkiem przejścia (tylko kod:
   `INTERVIEW→CONTRACT` gdy `open_goals.isEmpty()`). AI ma narzędzie `defer_goal`/tryb „support" — przy
   emocjach odkłada agendę bez kary. **Scheduler mówi „wizyta należna"; AI mówi „kiedy w rozmowie".**
   Cele mają priorytet miękki + łagodne wygasanie (zalega → podniesiony priorytet w kontekście, NIE blokada).
2. **Baza produktów:** rdzeń kurowany **~150 surowych PL** (ryż/kasza/kurczak — OFF robi je źle; fundament
   planów+zakupów, żelazna zasada „surowe") + **OFF na markowe/gotowe** (fundament logowania). Trust score
   steruje sortowaniem/domyślnością; auto-„popularny" z użycia; ale **„zweryfikowany do obliczeń" tylko przez
   walidator spójności makro** (suma makro×kcal/g = deklarowane kcal ±10%) LUB rdzeń. Nie wpuszczać śmieci do silnika zdrowotnego.
3. **`micro/` i `clinical/` PRZESUNIĘTE za pierwsze pełne pętle end-to-end** — ale z dwoma SZWAMI od dnia 1:
   (a) `PlanValidator` od początku bierze `List<Constraint>` (pusta na start) — diety kliniczne = nowe dane, zero zmiany sygnatur;
   (b) `DayLog`/`FoodItem` mają `Map<Micronutrient, Double>` (pustą na start) — inaczej późniejsze micro = bolesna migracja modelu spożycia.
4. **Adaptacyjny TDEE: próbka, nie ciągłość.** Okno 14d, próg **≥8 dni KOMPLETNYCH** (nie 80% ciągłości —
   weekendy mogą być puste), ufność CIĄGŁA 0..1 (8 dni→~0.4 blend 60/40 Mifflin/pomiar; 12+→~0.8),
   waga ze slope średnich kroczących (toleruje dziury, ~2 wiarygodne punkty). Estymator zwraca `daysNeeded` →
   AI mówi „mam 6 z 8 dni, zaloguj jeszcze 2 a przeliczę". Śmieciowy sygnał (rozrzut 1200↔4000, brak 2 punktów wagi)
   → ufność w dół, bliżej Mifflina, AI to komunikuje.

## Decyzje właściciela (2026-07-04)
- **A. Rdzeń produktów:** ✅ **dobić do ~150 surowych z góry** (jednorazowy koszt fundamentu przed pierwszym logowaniem).
- **B. Ton przy unikaniu wizyty/ważenia:** ✅ **WYWAŻONY** — domyślnie empatyczny, przy kilkudniowym zaleganiu delikatnie
  ale konkretnie wraca do tematu. (Kod: priorytet celu = parametr; wybrany profil „balans".)

## Kolejność realizacji
1. **Inkr. 2:** modele domenowe (`NutritionProfile`, `ClinicalContext`, `WeightSample`, `DayLog`) + port
   `SafetyGuard`, `MedicalFlagger`, `TrendAnalyzer` z testami + golden-diff.
2. **Inkr. 3:** `GoalPipeline` (port DietGoals na nowe modele) + `AdaptiveTdeeEstimator` + golden-set profili.
3. **Inkr. 4:** `CalorieAdjustmentEngine` (port) + `CheckInEngine` + `RedFlagDetector`.
4. **Inkr. 5:** `aicontract/` (narzędzia, `DietitianContext`, miękki stan opieki) — pierwszy pełny wywiad end-to-end.
5. **Po pętlach:** `PlanValidator`+`ConstraintResolver`, potem `clinical/` i `micro/`.
