package pl.filebit.dietetyk.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ochrona przed regresją klasyfikatora produktów — kolejność wpisów ma znaczenie
 * (klucz będący podciągiem ogólniejszego z innej kategorii MUSI być wyżej).
 * Przy dokładaniu wpisów: dodaj tu parę nazwa→kategoria.
 */
class ProductClassifierTest {

    private fun cat(name: String) = inferCategory(name)

    @Test fun `kolizje ktore Fable wychwycil sa naprawione`() {
        assertEquals("Słodycze", cat("Deser czekoladowy"))      // „deser" zawiera „ser " (Nabiał) — musi wygrać Słodycze
        assertEquals("Orzechy", cat("Masło orzechowe"))          // „masło orzech" przed „masło" (Nabiał)
        assertEquals("Orzechy", cat("Masło arachidowe"))
        assertEquals("Owoce", cat("Porzeczka czerwona"))         // NIE Warzywa (usunięto bare „por")
        assertEquals("Nabiał", cat("Jogurt wysokobiałkowy"))     // NIE Napoje („sok" w „wysoko")
        assertEquals("Tłuszcze", cat("Mleko kokosowe"))          // NIE Nabiał
    }

    @Test fun `typ produktu wygrywa nad smakiem`() {
        assertEquals("Nabiał", cat("Jogurt truskawkowy"))        // NIE Owoce
        assertEquals("Nabiał", cat("Serek waniliowy"))
        assertEquals("Nabiał", cat("Kefir malinowy"))
        assertEquals("Warzywa", cat("Pomidor malinowy"))         // NIE Owoce
        assertEquals("Napoje", cat("Napój owsiany"))
    }

    @Test fun `podstawowe kategorie`() {
        assertEquals("Owoce", cat("Jabłko Gala"))
        assertEquals("Owoce", cat("Banan"))
        assertEquals("Warzywa", cat("Pomidor malinowy"))
        assertEquals("Warzywa", cat("Brokuł"))
        assertEquals("Mięso", cat("Pierś z kurczaka"))
        assertEquals("Mięso", cat("Schab wieprzowy"))
        assertEquals("Ryby", cat("Łosoś wędzony"))
        assertEquals("Nabiał", cat("Mleko 2%"))
        assertEquals("Nabiał", cat("Ser żółty Gouda"))
        assertEquals("Nabiał", cat("Twaróg półtłusty"))
        assertEquals("Jaja", cat("Jajko kurze"))
        assertEquals("Zboża", cat("Chleb żytni"))
        assertEquals("Zboża", cat("Ryż basmati"))
        assertEquals("Zboża", cat("Makaron pełnoziarnisty"))
        assertEquals("Tłuszcze", cat("Oliwa z oliwek"))
        assertEquals("Orzechy", cat("Orzechy włoskie"))
        assertEquals("Strączki", cat("Fasola czerwona"))
        assertEquals("Słodycze", cat("Czekolada gorzka"))
        assertEquals("Napoje", cat("Sok pomarańczowy"))
        assertEquals("Napoje", cat("Kawa mielona"))
    }

    @Test fun `nieznany produkt trafia do Inne`() {
        assertEquals("Inne", cat("Xyzabc niesklasyfikowany"))
    }

    @Test fun `ikona zgodna z kategoria`() {
        assertEquals("🍌", productEmoji("Banan", "Owoce"))
        assertEquals("🍗", productEmoji("Filet z kurczaka", "Mięso"))
        assertEquals("📦", productEmoji("Xyzabc", "Inne"))   // fallback ikona kategorii
    }
}
