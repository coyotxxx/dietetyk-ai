package pl.filebit.dietetyk.core.model

/**
 * Zadeklarowane stany zdrowotne wpływające na bezpieczeństwo i kształt diety.
 *
 * W GymTrackerze były luźnym stringiem CSV (`medicalConditions` + `parsedMedicalConditions()`).
 * Tu — typowany zbiór enumów: czyściej, bez literówek, gotowe pod przyszłe diety kliniczne
 * (DASH/insulinooporność/Hashimoto — inkrement po pętlach E2E, patrz ARCHITECTURE.md).
 */
enum class MedicalCondition {
    DIABETES,           // cukrzyca
    KIDNEY_DISEASE,     // choroby nerek
    LIVER_DISEASE,      // choroby wątroby
    HEART_DISEASE,      // choroby serca
    HYPERTENSION,       // nadciśnienie
    PREGNANCY,          // ciąża
    BREASTFEEDING,      // karmienie piersią
    EATING_DISORDER     // zaburzenia odżywiania
}

/** Kontekst kliniczny użytkownika. Pusty = brak zadeklarowanych stanów. */
data class ClinicalContext(
    val conditions: Set<MedicalCondition> = emptySet()
) {
    companion object {
        val NONE = ClinicalContext()
    }
}
