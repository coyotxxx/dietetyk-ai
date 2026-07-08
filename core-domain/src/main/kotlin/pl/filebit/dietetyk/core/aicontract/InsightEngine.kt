package pl.filebit.dietetyk.core.aicontract

import pl.filebit.dietetyk.core.calc.TrendDirection
import pl.filebit.dietetyk.core.model.DietGoalType
import java.time.LocalDate

/**
 * Typ proaktywnej podpowiedzi. KOLEJNOŚĆ = PRIORYTET (pierwszy pasujący wygrywa → MAX 1).
 * BEZPIECZEŃSTWO najwyżej, potem re-engagement, potem klasyk dietetyka, potem personalizowany makro-wow.
 */
enum class InsightType {
    // Problemy (najwyższy priorytet — pierwszy pasujący wygrywa):
    SAFETY, LOGGING_GAP, WEIGHT_STALL, PROTEIN_GAP,
    // Świętowanie zwycięstw nie-wagowych (NAJNIŻSZY priorytet — tylko gdy NIE ma problemu; wysoka poprzeczka):
    PROTEIN_WIN, CONSISTENCY_WIN
}

/** Jedna proaktywna podpowiedź na Dziś. Tekst = krótki wskaźnik; głębia (coaching) dzieje się w czacie. */
data class Insight(
    val type: InsightType,
    val text: String,
    val buttonLabel: String,
    /** Zaseedowana wiadomość do czatu — z LICZBAMI, żeby AI startowało z konkretu, nie „o co chodzi?". */
    val chatPrompt: String
)

/**
 * Deterministyczny „spotter" — wypatruje NAJWAŻNIEJSZEGO wzorca w danych i zwraca MAX 1 podpowiedź
 * (albo `null` = CISZA, stan domyślny). Kod tylko WYKRYWA + priorytetyzuje; coaching robi AI w rozmowie
 * (button → czat z `chatPrompt`). Konserwatywny: wzorce zależne od danych nie odpalają bez minimum
 * zalogowanych dni (insight z 2 dni = kłamstwo). Testowalny → gwarancja „komunikaty tylko prawidłowe".
 */
object InsightEngine {

    /** Ile dni wyciszenia po pokazaniu danego typu (0 = brak — bezpieczeństwo zawsze). */
    private val cooldownDays = mapOf(
        InsightType.SAFETY to 0L,
        InsightType.LOGGING_GAP to 3L,
        InsightType.WEIGHT_STALL to 7L,
        InsightType.PROTEIN_GAP to 4L,
        // Świętowanie rzadko (żeby nie spowszedniało/nie infantylizowało):
        InsightType.PROTEIN_WIN to 14L,
        InsightType.CONSISTENCY_WIN to 14L
    )

    fun detect(ctx: DietitianContext, cooldowns: Map<InsightType, LocalDate>, today: LocalDate): Insight? {
        for (type in InsightType.entries) {                 // entries w kolejności priorytetu
            if (onCooldown(type, cooldowns, today)) continue
            build(type, ctx)?.let { return it }
        }
        return null
    }

    private fun onCooldown(type: InsightType, cooldowns: Map<InsightType, LocalDate>, today: LocalDate): Boolean {
        val cd = cooldownDays[type] ?: 0L
        if (cd == 0L) return false
        val last = cooldowns[type] ?: return false
        return today.isBefore(last.plusDays(cd))            // wciąż w oknie wyciszenia
    }

    private fun build(type: InsightType, ctx: DietitianContext): Insight? = when (type) {
        InsightType.SAFETY ->
            if (ctx.referToDoctor) Insight(
                type,
                "Zauważyłem w Twoich danych coś, o czym warto porozmawiać — zadbajmy o Twoje zdrowie.",
                "Porozmawiajmy",
                "Zauważyłeś coś w moich danych, o czym warto pogadać — o co chodzi?"
            ) else null

        InsightType.LOGGING_GAP -> {
            val d = ctx.daysSinceLastLog
            if (d != null && d >= 3) Insight(
                type,
                "Nie widziałem Twoich posiłków od $d dni. Wróćmy do logowania — wtedy mogę Ci realnie pomóc. 🌱",
                "Wracam",
                "Chcę wrócić do logowania posiłków po $d dniach przerwy — od czego najlepiej zacząć?"
            ) else null
        }

        InsightType.WEIGHT_STALL -> {
            val wt = ctx.weightTrend
            val goalActive = ctx.profile.goal != DietGoalType.MAINTAIN && ctx.profile.goal != DietGoalType.HEALTH
            if (wt.direction == TrendDirection.FLAT && wt.sampleCount >= 4 && ctx.completeLogDays14d >= 5 && goalActive) Insight(
                type,
                "Twoja waga stoi w miejscu od około 2 tygodni, mimo że trzymasz plan. To normalne — ale możemy coś podkręcić, żeby ruszyła.",
                "Co zmienić?",
                "Moja waga stoi w miejscu od ~2 tygodni mimo trzymania planu — co możemy zmienić, żeby ruszyła?"
            ) else null
        }

        InsightType.PROTEIN_GAP -> {
            val pct = ctx.adherence14d.avgProteinPct
            if (ctx.completeLogDays14d >= 3 && pct in 1..79) {
                val fav = ctx.favoriteProducts.firstOrNull()
                val tail = if (fav != null) " Może dodać więcej: $fav?" else ""
                val promptTail = if (fav != null) ", może przez $fav?" else "?"
                Insight(
                    type,
                    "Ostatnio jesz mało białka — średnio ~$pct% celu.$tail",
                    "Jak dodać białko?",
                    "Ostatnio mam tylko ~$pct% białka z celu — jak to poprawić$promptTail"
                )
            } else null
        }

        // ŚWIĘTOWANIE — tylko przy REALNYM, znaczącym wzorcu (proporcjonalnie, bez konfetti za drobiazgi).
        InsightType.PROTEIN_WIN ->
            if (ctx.completeLogDays14d >= 7 && ctx.adherence14d.avgProteinPct in 95..140) Insight(
                type,
                "Białko trzymasz ostatnio naprawdę równo — to buduje mięśnie i sytość. Tak dalej! 💪",
                "Dzięki",
                "Widzę, że dobrze trzymam białko — co jeszcze warto podkręcić?"
            ) else null

        InsightType.CONSISTENCY_WIN ->
            if (ctx.completeLogDays14d >= 12) Insight(
                type,
                "Prawie codziennie logujesz od dwóch tygodni — ta konsekwencja procentuje bardziej niż perfekcja. 🌱",
                "Miło to słyszeć",
                "Trzymam konsekwencję w logowaniu — jak to najlepiej wykorzystać?"
            ) else null
    }
}
