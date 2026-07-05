package pl.filebit.dietetyk.core.model

/** Poziom aktywności — mnożnik TDEE (Mifflin-St Jeor). Obejmuje też treningi (patrz opisy). */
enum class ActivityLevel(val tdeeMultiplier: Double) {
    SEDENTARY(1.2),        // siedzący tryb (biuro, mało ruchu)
    LIGHT(1.375),          // lekko aktywny (chodzenie, lekkie prace)
    MODERATE(1.55),        // umiarkowanie aktywny (3-5 treningów + sporo chodzenia)
    VERY_ACTIVE(1.725),    // bardzo aktywny (6-7 treningów + ciężka praca fizyczna)
    EXTREME(1.9)           // ekstremalnie aktywny (zawodowy sportowiec)
}

/** Preferencja/typ diety — wpływa na dobór produktów (twarde ograniczenia w [pl.filebit.dietetyk.core.plan]). */
enum class DietPreference { STANDARD, VEGETARIAN, VEGAN, PESCATARIAN, KETO, MEDITERRANEAN }

/**
 * Cel żywieniowy — JEDEN cel (filozofia „jeden user, jeden aktywny stan").
 * Zastępuje w Dietetyku podwójny model GymTrackera (WeightGoalType 4-typy + DietGoalType 8-typów);
 * bierzemy bogatszy, diet-relevantny 8-typowy.
 */
enum class DietGoalType {
    FAT_LOSS,       // redukcja tkanki tłuszczowej
    MUSCLE_GAIN,    // budowa masy mięśniowej
    RECOMP,         // rekompozycja (waga stała, mniej tłuszczu/więcej mięśni)
    MAINTAIN,       // utrzymanie masy
    STRENGTH,       // poprawa siły
    ENDURANCE,      // poprawa wydolności
    HEALTH,         // poprawa zdrowia (cholesterol/cukier)
    EVENT_PREP      // przygotowanie do konkretnego terminu/zawodów
}
