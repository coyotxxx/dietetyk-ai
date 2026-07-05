# Dietetyk AI

Aplikacja Android (Kotlin / Jetpack Compose), w której **AI jest dietetykiem** — prowadzi wywiad,
układa plan, codziennie pilnuje i robi wizyty kontrolne. Silnik diety przeniesiony z GymTrackera.

Prywatna (Maciej + rodzina), dystrybucja przez GitHub Releases. Plan: `~/projects/9-dietetyk-ai/docs/PLAN.md`.

## Architektura modułów
```
:core-domain   czysty Kotlin/JVM — silnik diety (TDEE/makro, guardraile, estymaty, sloty).
               ZERO Androida. Testy ruszają na JVM (szybko).
:data          Room + repozytoria + SyncApi (furtka WWW) + OpenFoodFacts.   [TODO]
:ai            klient Claude API, tools, MasterContextBuilder, prompt.       [TODO]
:app           Compose UI + workery + powiadomienia + self-updater.          [TODO]
```

## Stack (zgodny z GymTrackerem)
Kotlin 2.1.0 · Gradle 8.10.2 · AGP 8.7.3 · JDK 17 · (docelowo) Room 2.7.2 · Hilt 2.56.2 · Compose BOM 2024.12.

## Build / test
```bash
export JAVA_HOME=/home/debian/jdk-17.0.19+10
./gradlew :core-domain:test        # czysty JVM, bez SDK — kilkanaście sekund
```

## Status Fazy 0 — port silnika diety z GymTrackera
Legenda: ✅ przeniesione + testy · 🔜 następny inkrement · ⛔ zostaje w :data/:app (Android)
Pakiety wg ARCHITECTURE.md: `model/` `calc/` `safety/` `adapt/` `plan/` `micro/` `aicontract/`.

### ✅ Inkrement 1+2 — modele domenowe + guardraile + estymaty (zrobione, 31 testów)
| Element | Pakiet/plik | Testy |
|---------|-------------|-------|
| `Gender`, `MealType`; `MealSlots` (Posiłki N 2–8, v2.73) | `model/Enums.kt`, `model/MealSlots.kt` | 6 ✅ |
| `NutritionProfile`, `ClinicalContext`+`MedicalCondition`, `WeightSample` (czyste modele, zero Room) | `model/` | — |
| `BodyFatEstimator` (Navy/Deurenberg, v2.69) | `calc/BodyFatEstimator.kt` | 5 ✅ |
| `TrendAnalyzer` (+`WeightTrend`,`TrendDirection`) — port na `WeightSample` | `calc/TrendAnalyzer.kt` | 6 ✅ |
| `SafetyGuard` (+`SafetyResult`) — sygnatura na `Gender` zamiast encji Room | `safety/SafetyGuard.kt` | 5 + matryca ✅ |
| `MedicalFlagger` (+`MedicalFlag`) — na `ClinicalContext` (enum) | `safety/MedicalFlagger.kt` | 8 ✅ |

Golden-referencja: testy `SafetyGuard{Test,MatrixTest}`/`TrendAnalyzerTest` przeniesione z GymTrackera —
pinowane wartości ze sprawdzonego produkcyjnie kodu = strażnik regresji przy porcie.

### ✅ Inkrement 3 — rdzeń celu + adaptacyjny TDEE (zrobione, +14 testów → 45 razem)
| Element | Pakiet/plik | Testy |
|---------|-------------|-------|
| `ActivityLevel`, `DietGoalType`; `NutritionProfile` rozszerzony (activity/goal/pace) | `model/DietEnums.kt`, `model/NutritionProfile.kt` | — |
| `GoalPipeline` (port `DietGoals` — TDEE Mifflin→deficyt wg celu→makro, `SafetyGuard` wewnątrz, breakdown) | `calc/GoalPipeline.kt` | 9 ✅ (golden TDEE=2759) |
| **`AdaptiveTdeeEstimator`** (NOWY — okno 14d, ≥8 dni kompletnych, ufność ciągła 0..1, `daysNeeded`) | `calc/AdaptiveTdeeEstimator.kt` | 5 ✅ |
| `DailyEnergyLog` (wąskie wejście energii dnia) | `model/DailyEnergyLog.kt` | — |

Uproszczenia vs GymTracker: JEDEN cel (`DietGoalType`, usunięty fallback `WeightGoalType`), zawsze pełny
Mifflin (Dietetyk ma komplet z wywiadu), `avgDailyCardioKcal`→ogólny `activityEnergyKcal`.

### ✅ Inkrement 4 — silnik korekt + wizyta + guardrail medyczny (zrobione, +18 → 63 razem)
| Element | Pakiet/plik | Testy |
|---------|-------------|-------|
| DTO `AdherenceSummary`/`RecoverySnapshot`/`NeatSnapshot` (czyste, okrojone) | `model/CoachingInputs.kt` | — |
| `CalorieAdjustmentEngine` (port — reguły „trend→małe kroki→wyjaśnij", cut/bulk/maintain) | `adapt/CalorieAdjustmentEngine.kt` | 9 ✅ |
| **`RedFlagDetector`** (NOWY — szybka utrata/głodzenie/BMI/ED → REFER, reużywa `MedicalFlagger`) | `safety/RedFlagDetector.kt` | 5 ✅ |
| **`CheckInEngine`** (NOWY — wizyta = JEDEN werdykt: red-flag nadrzędny, inaczej mapuje korektę) | `adapt/CheckInEngine.kt` | 4 ✅ |

`CheckInEngine` = „mózg decyduje, AI komunikuje": guardrail medyczny jest nadrzędny nad korektą kcal.

### ✅ Inkrement 5 — walidacja planu AI + system ograniczeń (zrobione, +11 → 74 razem)
| Element | Pakiet/plik | Testy |
|---------|-------------|-------|
| `FoodProductModel`+`FoodCategory`, `DietPreference`, DTO `AiDayPlan`/`AiMealRecipe`/`AiRecipeIngredient` | `model/` | — |
| `DietConstraint` (sealed: alergie/nietolerancje/dieta/medyczne/safety/soft) + `ConstraintResolver` (port, `object`) | `plan/DietConstraint.kt`, `plan/ConstraintResolver.kt` | 5 ✅ |
| `PlanValidator` (port `AiMealJsonValidator` — kcal/makro z bazy nie z AI, ±7% dzień, per-slot, fat<55%, keto, HARD/SOFT) | `plan/PlanValidator.kt` | 6 ✅ |

`ValidationContext.constraints` = `List<DietConstraint>` pusty na start → **szew pod diety kliniczne** (addytywne, bez zmiany sygnatur — ARCHITECTURE.md §3).

---

### ✅ Inkrement 6 — kontrakt AI↔dane (zrobione, +7 → 81 razem)
Realizuje dyrektywę Macieja „AI decyduje o WSZYSTKIM i widzi WSZYSTKIE dane". Pakiet `aicontract/`:
| Element | Plik | Testy |
|---------|------|-------|
| `CareState` — MIĘKKI stan opieki (etap + otwarte cele + warunek przejścia; `canDeferGoals`, miękki nudge) | `aicontract/CareState.kt` | — |
| `DietitianContext` — KOMPLETNY kontekst do każdej rozmowy (profil/klinika/cel/TDEE/trend/adherence/dziś/wizyta/pamięć/red-flag) | `aicontract/DietitianContext.kt` | — |
| `AiToolCatalog` — narzędzia (pełny dostęp do danych/akcji; `propose_adjustment` bez surowych kcal = guardrail strukturalny) | `aicontract/AiToolCatalog.kt` | — |
| `DietitianPrompt` — system prompt (persona 2.7a: naturalnie/jedno pytanie/bez żargonu) + render kontekstu + guidance etapu | `aicontract/DietitianPrompt.kt` | 7 ✅ |

## 🏁 Rdzeń silnika (`:core-domain`) KOMPLETNY — 81 testów
Cała deterministyczna warstwa „algorytm liczy" + kontrakt AI: modele domenowe (zero Room),
kalkulacje (TDEE/makro/trend/tkanka/adaptacyjny metabolizm), guardraile (SafetyGuard/MedicalFlagger/
RedFlagDetector), decyzje (CalorieAdjustmentEngine/CheckInEngine), walidacja planów (PlanValidator/Constraints),
kontrakt AI (DietitianContext/CareState/tools/prompt). Pozostaje `micro/` — świadomie odłożony (po pętlach E2E).

### 🚧 Faza Android — w toku
- ✅ **Szkielet modułów Android BUDUJE SIĘ** (APK debug): `:core-domain`(JVM) → `:data`(Room) → `:app`(Compose).
  Build: `./gradlew :data:assembleDebug :app:assembleDebug` (JDK17 + SDK android-35).
  - `:data` — Room DB v1 (`ProfileEntity`→`NutritionProfile`, `WeightEntity`→`WeightSample`) + mappery
    na granicy + repozytoria (oddają MODELE nie encje) + `SyncApi` seam (`NoopSyncApi`, MVP). Schemat eksportowany.
  - `:app` — `DietetykApp` + `MainActivity` (placeholder Compose w kolorach designu). appId `pl.filebit.dietetyk`.
- ✅ `:data` Room v2 + **builder kontekstu**: `EnergyLogEntity`→`DailyEnergyLog`, `AiMemoryEntity` (pamięć epizodyczna),
  `MIGRATION_1_2` (prawdziwa, DDL), `DietitianContextBuilder` (pobiera z repo → woła
  `DietitianContextAssembler` z rdzenia → pełny `DietitianContext`; brak profilu → null = tryb wywiadu).
  `core-domain/aicontract/DietitianContextAssembler` (czysty, 83 testy) spina cały silnik: cel+adaptacyjny
  TDEE+trend+red-flag w jeden komplet danych dla AI.
- 🔜 `:data` — dołożyć encje: plan/posiłki/wizyty/produkty + `OpenFoodFactsClient` + seeder ~150 produktów PL
- ✅ `:ai` (Kotlin/JVM, 7 testów) — klient Claude (OkHttp, `x-api-key`+`anthropic-version 2023-06-01`,
  modele: Sonnet 5 czat / Opus 4.8 wizyty), `ClaudeToolMapper` (AiToolCatalog→format tools Claude),
  `ClaudeMessages` (build request + parse tool_use), `DietitianConversation` (pętla tool-use: AI woła
  narzędzie→`ToolHandler`→wynik→…→end_turn), interfejs `ToolHandler` (impl w `:app`, dostęp do `:data`).
- ✅ `:app` — **czat z Dietetykiem spięty end-to-end** (APK się buduje): `DietToolHandler` (mapuje
  narzędzia AI na akcje: `calculate_targets`→GoalPipeline, `log_meal`/`log_measurement`→Room,
  `run_checkin`→CheckInEngine, `save_visit_note`→pamięć, `save_profile`→profil, `get_history`→kontekst),
  `ChatScreen` Compose (na kolorach designu, BYOK bramka na klucz), `sendToDietitian` buduje pełny
  `DietitianContext`→system prompt (`DietitianPrompt`)→`DietitianConversation` (pętla tool-use).
  Ręczne DI w `DietetykApp`. ⚠️ realne wywołanie Claude wymaga klucza (niesprawdzone sieciowo);
  logika pętli/narzędzi pokryta 7 testami w `:ai`.
- 🔜 `:app` — nawigacja Compose (Czat/Dziś/Plan/Postępy/Profil) na designie + workery wizyt + self-updater
- ⚠️ Migracje Room: ZAWSZE prawdziwe `Migration(N,N+1)`, NIGDY destructive (żelazna zasada).
- `AiMealJsonValidator` + `ConstraintResolver`/`DietConstraint` (odciąć `javax.inject` i Room `FoodProduct`)
- `MasterAiContext` (model + renderery promptu) — bez `MasterAiContextBuilder` (ten zostaje w :data/:app)
- DTO planu: `AiDayPlan`, `AiMealRecipe`, `AiRecipeIngredient` (`@Serializable`)

### ⛔ Zostaje w :data / :app (Android — nie do :core-domain)
`FoodProduct`/`MealEntry`/`UserProfile` (Room `@Entity`), `FoodProductSeeder` (assets/Context),
`food_products.json` (asset), `ProductResolver` + `OpenFoodFactsClient` (OkHttp), `AdherenceCalculator`
(DAO), `MasterAiContextBuilder` (Hilt + repo), `save_diet_plan` (`AiTools`/`AiToolHandler`/`DietRepository`).

> Mapa źródłowa portu (ścieżki plików w GymTrackerze) — patrz notatka projektu w pamięci
> `dietetyk-ai-project` / audyt w `docs/PLAN.md` sekcja 8.
