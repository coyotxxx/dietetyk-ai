package pl.filebit.dietetyk.ui

/**
 * Baza wiedzy o produktach: słowo-klucz → (ikona emoji + kategoria).
 * Jedno źródło prawdy używane i przy renderowaniu ikon, i przy AUTO-klasyfikacji
 * produktów dodawanych przez skaner / ręcznie (ikona + kategoria ustawiane same).
 *
 * Kolejność MA znaczenie — pierwszy pasujący klucz wygrywa, więc wpisy bardziej
 * szczegółowe (np. „mleko kokosowe") muszą być PRZED ogólnymi („mleko").
 */
private data class ProductClass(val keys: List<String>, val emoji: String, val category: String)

private fun c(emoji: String, category: String, vararg keys: String) = ProductClass(keys.toList(), emoji, category)

private val PRODUCT_CLASSES: List<ProductClass> = listOf(
    // === specjalne (przed ogólnymi) ===
    c("🥥", "Tłuszcze", "mleko kokos", "olej kokos"),
    c("🥛", "Napoje", "mleko roślin", "mleko roslin", "napój sojow", "napoj sojow", "napój owsian", "napoj owsian", "napój migdał", "napoj migdal"),

    // === OWOCE ===
    c("🍎", "Owoce", "jabłk", "jablk"),
    c("🍌", "Owoce", "banan"),
    c("🍉", "Owoce", "arbuz"),
    c("🍐", "Owoce", "gruszk"),
    c("🍊", "Owoce", "pomarańcz", "pomarancz", "mandarynk", "grejpfrut", "klementynk"),
    c("🍋", "Owoce", "cytryn", "limonk"),
    c("🍓", "Owoce", "truskaw", "malin", "poziomk"),
    c("🍇", "Owoce", "winogron", "rodzynk"),
    c("🍑", "Owoce", "brzoskwin", "morel", "nektaryn", "śliwk", "sliwk"),
    c("🍍", "Owoce", "ananas"),
    c("🫐", "Owoce", "borówk", "borowk", "jagod", "aronia"),
    c("🍒", "Owoce", "wiśni", "wisni", "czereśni", "czeresni"),
    c("🥝", "Owoce", "kiwi", "agrest"),
    c("🥭", "Owoce", "mango", "papaj"),
    c("🍈", "Owoce", "melon", "kantalup"),
    c("🥑", "Owoce", "awokado"),
    c("🥥", "Owoce", "kokos"),
    c("🍎", "Owoce", "owoc", "granat", "figa", "daktyl", "żurawin", "zurawin"),

    // === WARZYWA ===
    c("🍅", "Warzywa", "pomidor"),
    c("🥕", "Warzywa", "marchew"),
    c("🥒", "Warzywa", "ogórek", "ogorek", "korniszon"),
    c("🥦", "Warzywa", "brokuł", "brokul", "kalafior"),
    c("🫑", "Warzywa", "papryk"),
    c("🥔", "Warzywa", "ziemniak", "kartofel", "frytk"),
    c("🍠", "Warzywa", "batat"),
    c("🌽", "Warzywa", "kukurydz"),
    c("🧅", "Warzywa", "cebul", "por "),
    c("🧄", "Warzywa", "czosnek"),
    c("🍆", "Warzywa", "bakłażan", "baklazan", "oberżyn"),
    c("🥬", "Warzywa", "sałat", "salat", "szpinak", "kapust", "roszpon", "rukol", "jarmuż", "jarmuz", "seler", "botwin"),
    c("🍄", "Warzywa", "grzyb", "pieczark", "boczniak", "kurk"),
    c("🎃", "Warzywa", "dyni", "dynia", "cukini", "kabacz"),
    c("🥕", "Warzywa", "burak", "rzodkiew", "rzepa", "pietruszk", "warzyw"),
    c("🍅", "Warzywa", "cukinia", "szparag", "karczoch", "por"),

    // === STRĄCZKI ===
    c("🫘", "Strączki", "fasol", "soczewic", "ciecierzyc", "cieciork", "groch", "groszek", "bób", "bob", "hummus", "edamame", "soja", "tofu"),

    // === MIĘSO ===
    c("🍗", "Mięso", "kurczak", "kurcz", "indyk", "drób", "drob", "udko", "pierś", "piers", "skrzydeł", "skrzydel", "kaczk", "gęś", "ges"),
    c("🥩", "Mięso", "wołow", "wolow", "stek", "rozbef", "polędwic", "poledwic", "antrykot", "cielęc", "cielec", "jagnięc", "jagniec", "mielone"),
    c("🥓", "Mięso", "boczek", "schab", "szynk", "kabanos", "kiełbas", "kielbas", "wieprz", "parówk", "parowk", "salami", "karkówk", "karkowk", "wędlin", "wedlin", "pasztet", "mortadel", "żeberk", "zeberk"),

    // === RYBY ===
    c("🐟", "Ryby", "łosoś", "losos", "tuńczyk", "tunczyk", "dorsz", "makrel", "śledź", "sledz", "ryb", "pstrąg", "pstrag", "sandacz", "halibut", "flądr", "fladr", "morszczuk", "panga", "tilapia", "sardynk", "szprot"),
    c("🦐", "Ryby", "krewetk", "owoce morza", "kalmar", "małż", "malz", "ostryg", "krab"),

    // === NABIAŁ ===
    c("🧀", "Nabiał", "ser ", "serek", "twaróg", "twarog", "mozzarell", "feta", "parmezan", "gouda", "cheddar", "camembert", "brie", "ricotta", "mascarpone", "ser żółt", "ser zolt", "ser biał", "ser bial"),
    c("🥛", "Nabiał", "jogurt", "kefir", "maślank", "maslank", "śmietan", "smietan", "skyr", "mleko", "mleko"),
    c("🧈", "Nabiał", "masło", "maslo", "margaryn"),

    // === JAJA ===
    c("🥚", "Jaja", "jaj"),

    // === ZBOŻA / PIECZYWO ===
    c("🍞", "Zboża", "chleb", "bułk", "bulk", "pieczyw", "bagiet", "tost", "grzank", "chrupk", "sucharek", "pumpernikiel"),
    c("🍚", "Zboża", "ryż", "ryz"),
    c("🍝", "Zboża", "makaron", "spaghetti", "penne", "łazank", "lazank", "kluski", "lasagne", "tagliatelle"),
    c("🥣", "Zboża", "płatki", "platki", "owsian", "musli", "granol", "otręby", "otreby"),
    c("🌾", "Zboża", "kasz", "quinoa", "komos", "bulgur", "kuskus", "jaglan", "gryczan", "pęczak", "peczak", "mąka", "maka", "zboż", "zboz"),
    c("🥞", "Zboża", "naleśnik", "nalesnik", "gofr", "placek", "tortilla", "wrap"),

    // === ORZECHY / NASIONA ===
    c("🥜", "Orzechy", "orzech", "migdał", "migdal", "nerkowc", "pistacj", "masło orzech", "maslo orzech", "arachid"),
    c("🌰", "Orzechy", "słonecznik", "slonecznik", "pestk", "siemię", "siemie", "chia", "sezam", "nasion", "len ", "lnian"),

    // === TŁUSZCZE ===
    c("🫒", "Tłuszcze", "oliw", "olej", "smalec", "łój", "loj"),

    // === NAPOJE ===
    c("☕", "Napoje", "kaw", "espresso", "latte", "cappuccino"),
    c("🍵", "Napoje", "herbat", "matcha", "napar"),
    c("💧", "Napoje", "woda ", "woda"),
    c("🧃", "Napoje", "sok", "nektar", "smoothie", "koktajl"),
    c("🥤", "Napoje", "cola", "napój gazow", "napoj gazow", "lemoniad", "oranżad", "oranzad", "izoton", "energetyk"),
    c("🍺", "Napoje", "piwo", "wino", "alkohol"),

    // === SŁODYCZE / PRZEKĄSKI ===
    c("🍫", "Słodycze", "czekolad", "baton", "praline", "nutella", "krem czekolad"),
    c("🍬", "Słodycze", "cukier", "cukierk", "żelk", "zelk", "landrynk", "lizak", "guma do żucia"),
    c("🍯", "Słodycze", "miód", "miod", "syrop"),
    c("🍓", "Słodycze", "dżem", "dzem", "konfitur", "marmolad", "powidł", "powidl"),
    c("🍪", "Słodycze", "ciast", "ciasteczk", "herbatnik", "wafel", "waflik", "pierni", "keks", "muffin", "brownie", "sernik", "biszkopt"),
    c("🍦", "Słodycze", "lody", "lód", "sorbet", "deser"),
    c("🥨", "Słodycze", "chips", "chipsy", "paluszk", "krakers", "precel", "popcorn", "nachos", "przekąsk", "przekask"),

    // === PRZYPRAWY / SOSY ===
    c("🧂", "Przyprawy", "sól", "sol ", "pieprz", "przypraw", "zioł", "ziol", "bazyli", "oregano", "kurkum", "cynamon", "papryka wędz", "papryka wedz"),
    c("🥫", "Przyprawy", "ketchup", "musztard", "majonez", "sos", "koncentrat", "passata", "przecier"),

    // === GOTOWE DANIA ===
    c("🍕", "Inne", "pizza"),
    c("🍔", "Inne", "burger", "hamburger"),
    c("🥟", "Inne", "pierogi", "pieróg", "pierog", "gyoza", "uszk"),
    c("🍲", "Inne", "zupa", "gulasz", "danie gotow", "bigos"),
)

private fun classify(name: String): ProductClass? {
    val n = " ${name.lowercase().trim()} "
    return PRODUCT_CLASSES.firstOrNull { pc -> pc.keys.any { n.contains(it) } }
}

/** Ikona konkretnego produktu (po nazwie), z fallbackiem na ikonę kategorii. */
fun productEmoji(name: String, category: String): String = classify(name)?.emoji ?: catEmoji(category)

/** Auto-kategoria produktu z nazwy (dla skanera / dodawania). Fallback: podana domyślna. */
fun inferCategory(name: String, default: String = "Inne"): String = classify(name)?.category ?: default

/** Ikona kategorii (folder + fallback dla produktu bez własnej ikony). */
fun catEmoji(cat: String): String = when {
    cat.contains("warzyw", true) -> "🥦"
    cat.contains("owoc", true) -> "🍎"
    cat.contains("ryb", true) -> "🐟"
    cat.contains("mię", true) || cat.contains("mie", true) || cat.contains("drób", true) || cat.contains("drob", true) -> "🍗"
    cat.contains("nabia", true) || cat.contains("ser", true) || cat.contains("mleko", true) -> "🥛"
    cat.contains("zbo", true) || cat.contains("pieczyw", true) || cat.contains("kasz", true) || cat.contains("makaron", true) || cat.contains("ryż", true) -> "🌾"
    cat.contains("tłuszcz", true) || cat.contains("tluszcz", true) || cat.contains("olej", true) || cat.contains("oliw", true) -> "🫒"
    cat.contains("orzech", true) || cat.contains("nasion", true) -> "🥜"
    cat.contains("jaj", true) -> "🥚"
    cat.contains("strącz", true) || cat.contains("stracz", true) -> "🫘"
    cat.contains("napó", true) || cat.contains("napo", true) -> "🥤"
    cat.contains("słody", true) || cat.contains("slody", true) -> "🍫"
    cat.contains("przypraw", true) -> "🧂"
    else -> "📦"
}

/** Grupowanie kategorii w 3 akcenty + neutral (spec Fable): 0=Green, 1=Orange, 2=Blue, 3=Neutral. */
fun categoryHue(cat: String): Int = when {
    cat.contains("mię", true) || cat.contains("mie", true) || cat.contains("ryb", true) ||
        cat.contains("tłuszcz", true) || cat.contains("tluszcz", true) || cat.contains("olej", true) ||
        cat.contains("orzech", true) || cat.contains("drób", true) || cat.contains("drob", true) -> 1
    cat.contains("nabia", true) || cat.contains("jaj", true) || cat.contains("ser", true) || cat.contains("mleko", true) ||
        cat.contains("napó", true) || cat.contains("napo", true) -> 2
    cat.contains("inne", true) || cat.contains("przypraw", true) || cat.contains("słody", true) || cat.contains("slody", true) -> 3
    else -> 0 // warzywa, owoce, strączki, zboża + reszta roślinna
}
