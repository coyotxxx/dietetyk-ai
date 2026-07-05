package pl.filebit.dietetyk.core.safety

import pl.filebit.dietetyk.core.model.ClinicalContext
import pl.filebit.dietetyk.core.model.MedicalCondition
import pl.filebit.dietetyk.core.model.NutritionProfile

enum class MedicalSeverity { INFO, WARN, CRITICAL }

data class MedicalFlag(
    val code: String,
    val severity: MedicalSeverity,
    val message: String,
    val recommendation: String
)

/**
 * Wykrywa flagi zdrowotne wymagające ostrożności / konsultacji ze specjalistą.
 *
 * Kryteria: BMI <16/<18.5/≥35, zadeklarowane stany medyczne, (tempo redukcji — w silniku korekt).
 * Aplikacja NIE jest narzędziem medycznym. Przy CRITICAL flag pokazujemy disclaimer i zalecamy
 * konsultację z lekarzem/dietetykiem klinicznym.
 *
 * Przeniesione z GymTrackera. ZMIANA vs oryginał: przyjmuje czyste [NutritionProfile]+[ClinicalContext]
 * (stany jako enum zamiast CSV-stringa).
 */
object MedicalFlagger {

    fun analyze(
        profile: NutritionProfile,
        clinical: ClinicalContext,
        currentWeightKg: Double? = null
    ): List<MedicalFlag> {
        val flags = mutableListOf<MedicalFlag>()

        // BMI (jeśli mamy wagę i wzrost)
        val weight = currentWeightKg ?: profile.weightKg
        val height = profile.heightCm
        if (weight != null && height > 0) {
            val heightM = height / 100.0
            val bmi = weight / (heightM * heightM)
            when {
                bmi < 16.0 -> flags += MedicalFlag(
                    "very_low_bmi", MedicalSeverity.CRITICAL,
                    "BMI %.1f — bardzo niska masa ciała.".format(bmi),
                    "Skontaktuj się z lekarzem przed rozpoczęciem jakiejkolwiek diety. Apka nie jest dla Ciebie odpowiednia."
                )
                bmi < 18.5 -> flags += MedicalFlag(
                    "low_bmi", MedicalSeverity.WARN,
                    "BMI %.1f — niedowaga.".format(bmi),
                    "Cel: budowa masy. Skonsultuj plan z dietetykiem klinicznym."
                )
                bmi >= 35.0 -> flags += MedicalFlag(
                    "high_bmi", MedicalSeverity.WARN,
                    "BMI %.1f — otyłość znaczna.".format(bmi),
                    "Apka pomoże ale zalecana konsultacja z dietetykiem + lekarzem (badania, ciśnienie)."
                )
            }
        }

        // Stany medyczne
        for (cond in clinical.conditions) {
            flags += flagFor(cond)
        }

        return flags
    }

    private fun flagFor(cond: MedicalCondition): MedicalFlag = when (cond) {
        MedicalCondition.DIABETES -> MedicalFlag(
            "diabetes", MedicalSeverity.CRITICAL,
            "Cukrzyca — dieta wymaga kontroli węgli i indeksu glikemicznego.",
            "Plan diety MUSI być zatwierdzony przez lekarza diabetologa. Apka traktowana wyłącznie pomocniczo."
        )
        MedicalCondition.KIDNEY_DISEASE -> MedicalFlag(
            "kidney_disease", MedicalSeverity.CRITICAL,
            "Choroba nerek — wysokie białko może być szkodliwe.",
            "Konsultacja z nefrologiem konieczna przed dietą wysokobiałkową."
        )
        MedicalCondition.LIVER_DISEASE -> MedicalFlag(
            "liver_disease", MedicalSeverity.CRITICAL,
            "Choroba wątroby — wymaga specjalnej diety.",
            "Plan tylko z dietetykiem klinicznym."
        )
        MedicalCondition.HEART_DISEASE -> MedicalFlag(
            "heart_disease", MedicalSeverity.WARN,
            "Choroba serca — kontrola sodu i tłuszczy nasyconych.",
            "Konsultacja z kardiologiem zalecana."
        )
        MedicalCondition.HYPERTENSION -> MedicalFlag(
            "hypertension", MedicalSeverity.WARN,
            "Nadciśnienie — ograniczenie sodu (<2300 mg/dzień).",
            "Apka będzie sugerować mniej soli, ale konsultacja z lekarzem zalecana."
        )
        MedicalCondition.PREGNANCY -> MedicalFlag(
            "pregnancy", MedicalSeverity.CRITICAL,
            "Ciąża — apka NIE jest narzędziem dla kobiet w ciąży.",
            "Plan żywienia tylko z lekarzem prowadzącym i dietetykiem ciążowym."
        )
        MedicalCondition.BREASTFEEDING -> MedicalFlag(
            "breastfeeding", MedicalSeverity.WARN,
            "Karmienie piersią — zwiększone zapotrzebowanie kaloryczne (+500 kcal).",
            "Apka nie wzięła tego pod uwagę. Konsultacja z dietetykiem laktacyjnym."
        )
        MedicalCondition.EATING_DISORDER -> MedicalFlag(
            "eating_disorder", MedicalSeverity.CRITICAL,
            "Zaburzenia odżywiania — apki śledzące dietę mogą pogorszyć stan.",
            "Apka nie jest dla Ciebie. Skonsultuj się z psychiatrą / psychologiem specjalizującym się w zaburzeniach odżywiania (NEFA, https://nefa.pl)."
        )
    }

    fun anyCritical(flags: List<MedicalFlag>): Boolean =
        flags.any { it.severity == MedicalSeverity.CRITICAL }
}
