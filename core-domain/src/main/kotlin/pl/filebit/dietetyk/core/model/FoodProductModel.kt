package pl.filebit.dietetyk.core.model

/** Kategoria produktu spożywczego. */
enum class FoodCategory { CARBS, PROTEIN, FAT, VEGETABLE, DAIRY, FRUIT, OTHER }

/**
 * Czysty model produktu spożywczego — wartości per 100 g SUROWEGO produktu (żelazna zasada).
 *
 * Zastępuje w `:core-domain` androidowe `FoodProduct` (Room `@Entity`). To wspólna zależność
 * walidatora planu, resolvera ograniczeń i (docelowo) katalogu produktów. Warstwa `:data` mapuje
 * encję Room na ten model.
 */
data class FoodProductModel(
    val id: Long = 0,
    val name: String,
    val category: FoodCategory = FoodCategory.OTHER,
    val kcalPer100g: Int,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val fiberPer100g: Double = 0.0
)
