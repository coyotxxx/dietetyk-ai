package pl.filebit.dietetyk.core.plan

import pl.filebit.dietetyk.core.model.DietPreference
import pl.filebit.dietetyk.core.model.FoodCategory
import pl.filebit.dietetyk.core.model.FoodProductModel

enum class ConstraintPriority {
    HARD,   // ZAKAZ łamania (alergie, choroby, weganizm, safety)
    SOFT    // preferencja, można zignorować z wyjaśnieniem
}

data class ConstraintViolation(
    val constraint: DietConstraint,
    val productNames: List<String>,
    val context: String = ""
)

/**
 * Pojedyncze ograniczenie diety. HARD = nie wolno łamać, SOFT = preferencja.
 * AI dostaje listę + instrukcję; jeśli musi złamać SOFT — wyjaśnia. HARD nigdy do złamania.
 *
 * Przeniesione z GymTrackera — logika `isViolated` 1:1; `FoodProduct`(Room) → [FoodProductModel].
 */
sealed class DietConstraint {
    abstract val priority: ConstraintPriority
    abstract val description: String

    /** Czy posiłek (lista produktów) narusza to ograniczenie. */
    abstract fun isViolated(products: List<FoodProductModel>): Boolean

    // === HARD ===

    data class Allergy(val allergen: String) : DietConstraint() {
        override val priority = ConstraintPriority.HARD
        override val description = "Alergia: $allergen — BEZWZGLĘDNIE unikaj"
        override fun isViolated(products: List<FoodProductModel>): Boolean {
            val key = allergen.trim().lowercase()
            return products.any { p -> allergenAliases(key).any { alias -> p.name.lowercase().contains(alias) } }
        }

        private fun allergenAliases(key: String): List<String> = when (key) {
            "laktoza" -> listOf("mleko", "śmietan", "twaróg", "ser", "jogurt", "skyr", "kefir", "masło")
            "gluten" -> listOf("pszenn", "pszenicy", "pszenica", "makaron", "pieczywo", "kasza pęczak", "kasza orkiszowa", "mąka", "wafle ryżowe")
            "jaja", "jajka" -> listOf("jajk", "jaja", "białko jaja")
            "orzechy" -> listOf("orzech", "migdał", "nerkowiec", "masło orzech")
            "ryby", "ryba" -> listOf("łosoś", "dorsz", "tuńczyk", "ryba", "śledź", "halibut", "makrela")
            "owoce_morza" -> listOf("krewetk", "kalmar", "ostryga", "małż")
            "soja" -> listOf("tofu", "soj", "edamame")
            "sezam" -> listOf("sezam", "tahini")
            else -> listOf(key)
        }
    }

    data class Intolerance(val substance: String) : DietConstraint() {
        override val priority = ConstraintPriority.HARD
        override val description = "Nietolerancja: $substance"
        override fun isViolated(products: List<FoodProductModel>): Boolean =
            Allergy(substance).isViolated(products)
    }

    data class DietPreferenceConstraint(val preference: DietPreference) : DietConstraint() {
        override val priority = ConstraintPriority.HARD
        override val description = when (preference) {
            DietPreference.STANDARD -> "—"
            DietPreference.VEGETARIAN -> "Wegetarianizm: BEZ mięsa i ryb"
            DietPreference.VEGAN -> "Weganizm: BEZ produktów odzwierzęcych (mięso, ryby, nabiał, jaja, miód)"
            DietPreference.PESCATARIAN -> "Pescetarianizm: ryby OK, BEZ mięsa"
            DietPreference.KETO -> "Keto: max 30g węgli netto/dzień"
            DietPreference.MEDITERRANEAN -> "Śródziemnomorska — preferuj oliwę, ryby, warzywa"
        }

        override fun isViolated(products: List<FoodProductModel>): Boolean = when (preference) {
            DietPreference.STANDARD, DietPreference.MEDITERRANEAN -> false
            DietPreference.VEGETARIAN -> products.any { isMeatOrFish(it) }
            DietPreference.VEGAN -> products.any { isAnimalOrigin(it) }
            DietPreference.PESCATARIAN -> products.any { isMeat(it) && !isFish(it) }
            DietPreference.KETO -> false // sprawdzane na poziomie sumy dnia
        }

        private fun isMeat(p: FoodProductModel): Boolean {
            val n = p.name.lowercase()
            return listOf("kurczak", "indyk", "wołowin", "wieprzow", "schab", "polędwicz", "mięs", "udko", "bażant", "kaczka", "królik", "dziczyzn").any { n.contains(it) }
        }
        private fun isFish(p: FoodProductModel): Boolean {
            val n = p.name.lowercase()
            return listOf("łosoś", "dorsz", "tuńczyk", "śledź", "halibut", "makrela", "ryba").any { n.contains(it) }
        }
        private fun isMeatOrFish(p: FoodProductModel): Boolean = isMeat(p) || isFish(p) ||
            p.name.lowercase().let { n -> n.contains("krewetk") || n.contains("kalmar") }
        private fun isAnimalOrigin(p: FoodProductModel): Boolean {
            if (isMeatOrFish(p)) return true
            val n = p.name.lowercase()
            return listOf("jajk", "jaja", "białko jaja", "mleko", "twaróg", "ser ", "serek", "jogurt", "skyr", "kefir", "masło", "miód").any { n.contains(it) } ||
                p.category == FoodCategory.DAIRY
        }
    }

    data class MedicalCondition(val condition: String) : DietConstraint() {
        override val priority = ConstraintPriority.HARD
        override val description = "Stan zdrowia: $condition — wymaga konsultacji"
        override fun isViolated(products: List<FoodProductModel>): Boolean = false // inform-only
    }

    data class SafetyKcalMin(val minKcal: Int) : DietConstraint() {
        override val priority = ConstraintPriority.HARD
        override val description = "Minimalne kcal/dzień: $minKcal (bezpieczeństwo)"
        override fun isViolated(products: List<FoodProductModel>): Boolean = false // poziom dnia
    }

    // === SOFT ===

    data class DislikedFood(val food: String) : DietConstraint() {
        override val priority = ConstraintPriority.SOFT
        override val description = "Nielubiane: $food"
        override fun isViolated(products: List<FoodProductModel>): Boolean {
            val key = food.trim().lowercase()
            return products.any { it.name.lowercase().contains(key) }
        }
    }

    data class CookingTimeMax(val minutes: Int) : DietConstraint() {
        override val priority = ConstraintPriority.SOFT
        override val description = "Max czas gotowania: $minutes min"
        override fun isViolated(products: List<FoodProductModel>): Boolean = false // poziom przepisu
    }

    data class WeeklyBudget(val pln: Int) : DietConstraint() {
        override val priority = ConstraintPriority.SOFT
        override val description = "Budżet tygodniowy: $pln zł"
        override fun isViolated(products: List<FoodProductModel>): Boolean = false // bez bazy cen
    }

    data class VarietyMin(val uniqueProductsPerWeek: Int) : DietConstraint() {
        override val priority = ConstraintPriority.SOFT
        override val description = "Minimalna różnorodność: $uniqueProductsPerWeek produktów/tydzień"
        override fun isViolated(products: List<FoodProductModel>): Boolean = false // poziom tygodnia
    }
}
