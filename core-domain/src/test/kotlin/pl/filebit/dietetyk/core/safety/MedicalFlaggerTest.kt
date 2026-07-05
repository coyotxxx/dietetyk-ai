package pl.filebit.dietetyk.core.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.dietetyk.core.model.ClinicalContext
import pl.filebit.dietetyk.core.model.Gender
import pl.filebit.dietetyk.core.model.MedicalCondition
import pl.filebit.dietetyk.core.model.NutritionProfile

class MedicalFlaggerTest {

    private fun profile(weightKg: Double?, heightCm: Int = 180, gender: Gender = Gender.MALE) =
        NutritionProfile(gender = gender, ageYears = 30, heightCm = heightCm, weightKg = weightKg)

    private fun codes(flags: List<MedicalFlag>) = flags.map { it.code }.toSet()

    @Test
    fun `BMI ponizej 16 to CRITICAL very_low_bmi`() {
        // 45 kg / 1.80 m -> BMI 13.9
        val flags = MedicalFlagger.analyze(profile(45.0), ClinicalContext.NONE)
        assertTrue(codes(flags).contains("very_low_bmi"))
        assertTrue(MedicalFlagger.anyCritical(flags))
    }

    @Test
    fun `BMI niedowaga 16-18_5 to WARN low_bmi`() {
        // 55 kg / 1.80 m -> BMI 17.0
        val flags = MedicalFlagger.analyze(profile(55.0), ClinicalContext.NONE)
        val f = flags.single { it.code == "low_bmi" }
        assertEquals(MedicalSeverity.WARN, f.severity)
        assertFalse(MedicalFlagger.anyCritical(flags))
    }

    @Test
    fun `BMI otylosc znaczna od 35 to WARN high_bmi`() {
        // 120 kg / 1.80 m -> BMI 37.0
        val flags = MedicalFlagger.analyze(profile(120.0), ClinicalContext.NONE)
        assertEquals(MedicalSeverity.WARN, flags.single { it.code == "high_bmi" }.severity)
    }

    @Test
    fun `zdrowe BMI nie daje flagi antropometrycznej`() {
        // 80 kg / 1.80 m -> BMI 24.7
        val flags = MedicalFlagger.analyze(profile(80.0), ClinicalContext.NONE)
        assertTrue(flags.none { it.code.endsWith("bmi") })
    }

    @Test
    fun `currentWeightKg ma pierwszenstwo nad waga z profilu`() {
        // profil 80 kg (zdrowe), ale realny pomiar 45 kg → flaga very_low_bmi
        val flags = MedicalFlagger.analyze(profile(80.0), ClinicalContext.NONE, currentWeightKg = 45.0)
        assertTrue(codes(flags).contains("very_low_bmi"))
    }

    @Test
    fun `cukrzyca to CRITICAL, nadcisnienie to WARN`() {
        val flags = MedicalFlagger.analyze(
            profile(80.0),
            ClinicalContext(setOf(MedicalCondition.DIABETES, MedicalCondition.HYPERTENSION))
        )
        assertEquals(MedicalSeverity.CRITICAL, flags.single { it.code == "diabetes" }.severity)
        assertEquals(MedicalSeverity.WARN, flags.single { it.code == "hypertension" }.severity)
        assertTrue(MedicalFlagger.anyCritical(flags))
    }

    @Test
    fun `zaburzenia odzywiania kieruja do specjalisty (CRITICAL)`() {
        val flags = MedicalFlagger.analyze(
            profile(80.0), ClinicalContext(setOf(MedicalCondition.EATING_DISORDER))
        )
        val f = flags.single { it.code == "eating_disorder" }
        assertEquals(MedicalSeverity.CRITICAL, f.severity)
        assertTrue("rekomendacja wskazuje specjalistę", f.recommendation.contains("psychiatr"))
    }

    @Test
    fun `brak wagi i brak stanow to zero flag`() {
        assertTrue(MedicalFlagger.analyze(profile(null), ClinicalContext.NONE).isEmpty())
    }
}
