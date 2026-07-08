package pl.filebit.dietetyk.core.aicontract

import pl.filebit.dietetyk.core.calc.TrendDirection
import pl.filebit.dietetyk.core.calc.WeightTrend
import pl.filebit.dietetyk.core.model.AdherenceSummary
import pl.filebit.dietetyk.core.model.ClinicalContext
import pl.filebit.dietetyk.core.model.DietGoalType
import pl.filebit.dietetyk.core.model.Gender
import pl.filebit.dietetyk.core.model.NutritionProfile
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Warunek wejścia proaktywnego silnika: gwarancja „komunikaty tylko prawidłowe".
 * Priorytet, progi, cisza-domyślna, cooldown, guardrail minimum-danych.
 */
class InsightEngineTest {
    private val today: LocalDate = LocalDate.of(2026, 7, 7)

    private fun ctx(
        refer: Boolean = false, daysSinceLog: Int? = 0, completeLogDays: Int = 10,
        proteinPct: Int = 88, trendDir: TrendDirection = TrendDirection.FALLING, trendSamples: Int = 10,
        goal: DietGoalType = DietGoalType.FAT_LOSS, favorites: List<String> = emptyList()
    ) = DietitianContext(
        careState = CareState(CareStage.ACTIVE),
        profile = NutritionProfile(gender = Gender.MALE, ageYears = 35, heightCm = 182, goal = goal),
        clinical = ClinicalContext.NONE,
        favoriteProducts = favorites,
        weightTrend = WeightTrend.NO_DATA.copy(direction = trendDir, sampleCount = trendSamples),
        adherence14d = AdherenceSummary(avgProteinPct = proteinPct),
        completeLogDays14d = completeLogDays,
        daysSinceLastLog = daysSinceLog,
        referToDoctor = refer
    )

    @Test fun `cisza gdy wszystko ok`() {
        assertNull(InsightEngine.detect(ctx(), emptyMap(), today))
    }

    @Test fun `bezpieczenstwo najwyzszy priorytet`() {
        val i = InsightEngine.detect(ctx(refer = true, daysSinceLog = 5, proteinPct = 40), emptyMap(), today)
        assertEquals(InsightType.SAFETY, i?.type)
    }

    @Test fun `porzucenie logowania od 3 dni, 2 dni cisza`() {
        assertEquals(InsightType.LOGGING_GAP, InsightEngine.detect(ctx(daysSinceLog = 3), emptyMap(), today)?.type)
        assertNull(InsightEngine.detect(ctx(daysSinceLog = 2), emptyMap(), today))
    }

    @Test fun `waga stoi tylko przy danych i aktywnym celu`() {
        assertEquals(InsightType.WEIGHT_STALL, InsightEngine.detect(ctx(trendDir = TrendDirection.FLAT), emptyMap(), today)?.type)
        assertNull(InsightEngine.detect(ctx(trendDir = TrendDirection.FLAT, completeLogDays = 2), emptyMap(), today))
        assertNull(InsightEngine.detect(ctx(trendDir = TrendDirection.FLAT, goal = DietGoalType.MAINTAIN), emptyMap(), today))
    }

    @Test fun `luka bialka wymaga minimum danych`() {
        assertEquals(InsightType.PROTEIN_GAP, InsightEngine.detect(ctx(proteinPct = 60), emptyMap(), today)?.type)
        assertNull(InsightEngine.detect(ctx(proteinPct = 60, completeLogDays = 2), emptyMap(), today))
    }

    @Test fun `bialko uzywa ulubionego produktu`() {
        val i = InsightEngine.detect(ctx(proteinPct = 60, favorites = listOf("Twaróg chudy")), emptyMap(), today)
        assertTrue(i?.text?.contains("Twaróg chudy") == true)
    }

    @Test fun `cooldown wycisza, po nim znow odpala`() {
        assertNull(InsightEngine.detect(ctx(proteinPct = 60), mapOf(InsightType.PROTEIN_GAP to today.minusDays(1)), today))
        assertEquals(InsightType.PROTEIN_GAP, InsightEngine.detect(ctx(proteinPct = 60), mapOf(InsightType.PROTEIN_GAP to today.minusDays(5)), today)?.type)
    }

    @Test fun `bezpieczenstwo bez cooldownu`() {
        assertEquals(InsightType.SAFETY, InsightEngine.detect(ctx(refer = true), mapOf(InsightType.SAFETY to today), today)?.type)
    }

    @Test fun `priorytet gdy kilka odpala`() {
        val i = InsightEngine.detect(ctx(daysSinceLog = 4, proteinPct = 50), emptyMap(), today)
        assertEquals(InsightType.LOGGING_GAP, i?.type)
    }

    // === ŚWIĘTOWANIE (nie-wagowe) — niski priorytet, wysoka poprzeczka, nigdy przy problemie ===

    @Test fun `swietuje wysokie bialko przy danych`() {
        assertEquals(InsightType.PROTEIN_WIN, InsightEngine.detect(ctx(proteinPct = 100, completeLogDays = 8), emptyMap(), today)?.type)
    }

    @Test fun `swietuje konsekwencje logowania`() {
        assertEquals(InsightType.CONSISTENCY_WIN, InsightEngine.detect(ctx(proteinPct = 88, completeLogDays = 13), emptyMap(), today)?.type)
    }

    @Test fun `NIE swietuje gdy jest problem - logowanie wygrywa`() {
        assertEquals(InsightType.LOGGING_GAP, InsightEngine.detect(ctx(proteinPct = 100, completeLogDays = 8, daysSinceLog = 5), emptyMap(), today)?.type)
    }

    @Test fun `NIE swietuje gdy waga stoi (tone-deaf)`() {
        assertEquals(InsightType.WEIGHT_STALL, InsightEngine.detect(ctx(proteinPct = 100, completeLogDays = 10, trendDir = TrendDirection.FLAT), emptyMap(), today)?.type)
    }

    @Test fun `swietowanie ma cooldown`() {
        assertNull(InsightEngine.detect(ctx(proteinPct = 100, completeLogDays = 8), mapOf(InsightType.PROTEIN_WIN to today.minusDays(3)), today))
    }
}
