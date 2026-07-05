package pl.filebit.dietetyk.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MealSlotsTest {

    @Test
    fun `liczba tagow rowna liczbie posilkow w calym zakresie 2-8`() {
        for (n in MealSlots.MIN_MEALS..MealSlots.MAX_MEALS) {
            assertEquals("N=$n", n, MealSlots.typesFor(n).size)
        }
    }

    @Test
    fun `zawsze zaczyna sniadaniem i konczy kolacja`() {
        for (n in MealSlots.MIN_MEALS..MealSlots.MAX_MEALS) {
            val types = MealSlots.typesFor(n)
            assertEquals("N=$n start", MealType.BREAKFAST, types.first())
            assertEquals("N=$n koniec", MealType.DINNER, types.last())
        }
    }

    @Test
    fun `liczba posilkow poza zakresem jest przycinana do 2-8`() {
        assertEquals(2, MealSlots.typesFor(0).size)
        assertEquals(2, MealSlots.typesFor(1).size)
        assertEquals(8, MealSlots.typesFor(99).size)
    }

    @Test
    fun `mealTypeForSlot mapuje 1-based, poza zakresem daje SNACK`() {
        assertEquals(MealType.BREAKFAST, MealSlots.mealTypeForSlot(1, 3))
        assertEquals(MealType.LUNCH, MealSlots.mealTypeForSlot(2, 3))
        assertEquals(MealType.DINNER, MealSlots.mealTypeForSlot(3, 3))
        assertEquals(MealType.SNACK, MealSlots.mealTypeForSlot(9, 3))
    }

    @Test
    fun `label slotu to Posilek N`() {
        assertEquals("Posiłek 1", MealSlots.label(1))
        assertEquals("Posiłek 5", MealSlots.label(5))
    }

    @Test
    fun `5 posilkow ma dokladnie 2 przekaski`() {
        assertEquals(2, MealSlots.typesFor(5).count { it == MealType.SNACK })
    }
}
