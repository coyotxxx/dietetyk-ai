package pl.filebit.dietetyk.notify

import java.time.LocalDate

/**
 * Szablony powiadomień CODZIENNYCH (kanał daily). Deterministyczne — ZERO wywołań AI (niezmiennik:
 * kanał daily nigdy nie woła API). Personalizacja = trafne liczby z silnika + rotacja wariantów,
 * nie proza modelu. Rotacja po dniu roku, żeby treść nie była identyczna każdego dnia.
 */
object NotifTemplates {

    private fun pick(options: List<String>, salt: Int): String {
        val idx = ((LocalDate.now().dayOfYear + salt) % options.size + options.size) % options.size
        return options[idx]
    }

    /** Poranny brief — plan na dziś (liczby z silnika). */
    fun morning(mealCount: Int, kcal: Int): Pair<String, String> {
        val title = pick(listOf("Dzień dobry! 🌅", "Plan na dziś 🌱", "Nowy dzień 🌅"), 0)
        val body = pick(
            listOf(
                "Na dziś mam dla Ciebie $mealCount posiłków (~$kcal kcal). Zerknij na plan i działamy.",
                "Dziś $mealCount posiłków, ~$kcal kcal. Trzymajmy się planu — dasz radę.",
                "Gotowy plan: $mealCount posiłków, ~$kcal kcal. Zacznij dzień dobrym śniadaniem."
            ), 1
        )
        return title to body
    }

    /** Smart-nudge: posiłek nie zalogowany po swojej porze. */
    fun mealNudge(mealName: String): Pair<String, String> {
        val title = pick(listOf("Jak tam posiłek? 🍽️", "Zamelduj się 🍽️", "Pytanie o jedzenie 🍽️"), 2)
        val body = pick(
            listOf(
                "Widzę, że posiłek $mealName jeszcze nie odhaczony. Zjadłeś? Daj znać, to policzę Ci dzień.",
                "Posiłek $mealName czeka na odhaczenie — zjedzone czy pomijamy? Kliknij, żeby zapisać.",
                "Jak poszło z posiłkiem $mealName? Zaznacz w apce, żeby trzymać rękę na pulsie."
            ), 3
        )
        return title to body
    }

    /** Wieczorne domknięcie dnia. */
    fun evening(eaten: Int, planned: Int, weightOverdue: Boolean): Pair<String, String> {
        val title = pick(listOf("Podsumujmy dzień 🌙", "Wieczorne domknięcie 🌙", "Jak minął dzień? 🌙"), 4)
        val body = when {
            weightOverdue && eaten < planned ->
                "Dziś $eaten z $planned posiłków, a wagi nie widziałem od kilku dni. Domkniemy jedno i drugie?"
            weightOverdue ->
                "Fajnie zjedzony dzień! Dorzuć jeszcze pomiar wagi — dawno go nie było, przyda się do trendu."
            else ->
                "Dziś $eaten z $planned posiłków. Zajrzyj, czego brakuje do domknięcia dnia — jutro gramy dalej."
        }
        return title to body
    }
}
