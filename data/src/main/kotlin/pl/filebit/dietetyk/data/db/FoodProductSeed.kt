package pl.filebit.dietetyk.data.db

/**
 * Wbudowana baza polskich produktów — wartości na 100 g produktu SUROWEGO.
 * Zasada: NIGDY wartości "gotowane" (patrz feedback: produkty zawsze surowe).
 */
object FoodProductSeed {

    fun normalize(s: String): String {
        val sb = StringBuilder()
        for (c in s.lowercase().trim()) {
            sb.append(
                when (c) {
                    'ą' -> 'a'; 'ć' -> 'c'; 'ę' -> 'e'; 'ł' -> 'l'; 'ń' -> 'n'
                    'ó' -> 'o'; 'ś' -> 's'; 'ź' -> 'z'; 'ż' -> 'z'
                    else -> c
                }
            )
        }
        return sb.toString()
    }

    private fun p(name: String, kcal: Int, prot: Double, carb: Double, fat: Double, cat: String) =
        FoodProductEntity(name = name, nameNorm = normalize(name), kcal = kcal, proteinG = prot, carbsG = carb, fatG = fat, category = cat, source = "seed")

    val all: List<FoodProductEntity> = listOf(
        // === NABIAŁ ===
        p("Mleko 2%", 51, 3.4, 4.8, 2.0, "Nabiał"),
        p("Mleko 3,2%", 61, 3.3, 4.7, 3.2, "Nabiał"),
        p("Mleko 0,5%", 40, 3.4, 4.9, 0.5, "Nabiał"),
        p("Jogurt naturalny 2%", 60, 4.3, 6.2, 2.0, "Nabiał"),
        p("Jogurt grecki", 97, 9.0, 3.6, 5.0, "Nabiał"),
        p("Skyr naturalny", 63, 11.0, 4.0, 0.2, "Nabiał"),
        p("Kefir", 51, 3.3, 4.7, 2.0, "Nabiał"),
        p("Maślanka", 39, 3.4, 4.7, 0.5, "Nabiał"),
        p("Twaróg chudy", 99, 19.8, 3.5, 0.5, "Nabiał"),
        p("Twaróg półtłusty", 133, 18.0, 3.7, 5.0, "Nabiał"),
        p("Twaróg tłusty", 175, 17.0, 3.5, 10.0, "Nabiał"),
        p("Serek wiejski", 98, 12.0, 3.4, 4.3, "Nabiał"),
        p("Ser żółty gouda", 356, 25.0, 0.0, 28.0, "Nabiał"),
        p("Mozzarella", 253, 18.0, 2.2, 19.0, "Nabiał"),
        p("Parmezan", 392, 36.0, 3.2, 25.0, "Nabiał"),
        p("Feta", 264, 14.0, 4.1, 21.0, "Nabiał"),
        p("Śmietana 18%", 184, 2.5, 3.6, 18.0, "Nabiał"),
        p("Masło", 735, 0.7, 0.7, 81.0, "Tłuszcze"),
        // === JAJA ===
        p("Jajko kurze całe", 143, 12.6, 0.7, 9.9, "Jaja"),
        p("Białko jaja", 52, 11.0, 0.7, 0.2, "Jaja"),
        // === MIĘSO / DRÓB ===
        p("Pierś z kurczaka", 110, 23.0, 0.0, 1.9, "Mięso"),
        p("Udko z kurczaka bez skóry", 130, 18.5, 0.0, 6.0, "Mięso"),
        p("Pierś z indyka", 105, 22.0, 0.0, 1.5, "Mięso"),
        p("Wołowina chuda (rozbef)", 130, 21.0, 0.0, 5.0, "Mięso"),
        p("Mięso mielone wołowe", 250, 18.0, 0.0, 20.0, "Mięso"),
        p("Schab wieprzowy", 140, 21.0, 0.0, 6.0, "Mięso"),
        p("Szynka wieprzowa gotowana", 110, 18.0, 1.0, 4.0, "Mięso"),
        p("Boczek", 500, 9.0, 0.0, 53.0, "Mięso"),
        p("Polędwica z kurczaka (wędlina)", 95, 19.0, 1.0, 1.5, "Mięso"),
        p("Kabanosy", 460, 25.0, 1.0, 40.0, "Mięso"),
        // === RYBY ===
        p("Łosoś", 208, 20.0, 0.0, 13.0, "Ryby"),
        p("Dorsz", 82, 18.0, 0.0, 0.7, "Ryby"),
        p("Tuńczyk w sosie własnym", 108, 25.0, 0.0, 1.0, "Ryby"),
        p("Makrela", 205, 19.0, 0.0, 14.0, "Ryby"),
        p("Śledź", 161, 18.0, 0.0, 9.0, "Ryby"),
        p("Krewetki", 85, 18.0, 0.9, 1.0, "Ryby"),
        // === ZBOŻA / PIECZYWO / MĄKI ===
        p("Płatki owsiane", 372, 13.0, 60.0, 7.0, "Zboża"),
        p("Ryż biały", 349, 7.0, 79.0, 0.7, "Zboża"),
        p("Ryż brązowy", 337, 7.5, 72.0, 2.8, "Zboża"),
        p("Kasza gryczana", 336, 12.0, 70.0, 2.5, "Zboża"),
        p("Kasza jaglana", 346, 10.5, 72.0, 3.0, "Zboża"),
        p("Makaron pszenny", 362, 12.0, 72.0, 1.8, "Zboża"),
        p("Makaron pełnoziarnisty", 340, 13.5, 64.0, 2.5, "Zboża"),
        p("Chleb żytni razowy", 224, 6.5, 45.0, 1.5, "Zboża"),
        p("Chleb pszenny", 265, 8.0, 50.0, 3.0, "Zboża"),
        p("Bułka pszenna", 290, 9.0, 57.0, 2.5, "Zboża"),
        p("Mąka pszenna", 341, 10.0, 72.0, 1.2, "Zboża"),
        p("Kasza bulgur", 342, 12.0, 63.0, 1.3, "Zboża"),
        p("Komosa ryżowa (quinoa)", 368, 14.0, 64.0, 6.0, "Zboża"),
        p("Otręby owsiane", 246, 17.0, 50.0, 7.0, "Zboża"),
        // === WARZYWA ===
        p("Ziemniaki", 77, 2.0, 17.0, 0.1, "Warzywa"),
        p("Batat", 86, 1.6, 20.0, 0.1, "Warzywa"),
        p("Pomidor", 18, 0.9, 3.9, 0.2, "Warzywa"),
        p("Ogórek", 15, 0.7, 3.6, 0.1, "Warzywa"),
        p("Papryka czerwona", 31, 1.0, 6.0, 0.3, "Warzywa"),
        p("Brokuły", 34, 2.8, 7.0, 0.4, "Warzywa"),
        p("Kalafior", 25, 1.9, 5.0, 0.3, "Warzywa"),
        p("Marchew", 41, 0.9, 10.0, 0.2, "Warzywa"),
        p("Cebula", 40, 1.1, 9.0, 0.1, "Warzywa"),
        p("Szpinak", 23, 2.9, 3.6, 0.4, "Warzywa"),
        p("Sałata", 15, 1.4, 2.9, 0.2, "Warzywa"),
        p("Cukinia", 17, 1.2, 3.1, 0.3, "Warzywa"),
        p("Kapusta biała", 25, 1.3, 5.8, 0.1, "Warzywa"),
        p("Buraki", 43, 1.6, 10.0, 0.2, "Warzywa"),
        p("Pieczarki", 22, 3.1, 3.3, 0.3, "Warzywa"),
        p("Rzodkiewka", 16, 0.7, 3.4, 0.1, "Warzywa"),
        p("Fasolka szparagowa", 31, 1.8, 7.0, 0.1, "Warzywa"),
        p("Kukurydza konserwowa", 86, 3.2, 19.0, 1.2, "Warzywa"),
        p("Awokado", 160, 2.0, 9.0, 15.0, "Warzywa"),
        // === OWOCE ===
        p("Banan", 89, 1.1, 23.0, 0.3, "Owoce"),
        p("Jabłko", 52, 0.3, 14.0, 0.2, "Owoce"),
        p("Gruszka", 57, 0.4, 15.0, 0.1, "Owoce"),
        p("Pomarańcza", 47, 0.9, 12.0, 0.1, "Owoce"),
        p("Truskawki", 32, 0.7, 7.7, 0.3, "Owoce"),
        p("Maliny", 52, 1.2, 12.0, 0.7, "Owoce"),
        p("Borówki", 57, 0.7, 14.0, 0.3, "Owoce"),
        p("Winogrona", 69, 0.7, 18.0, 0.2, "Owoce"),
        p("Kiwi", 61, 1.1, 15.0, 0.5, "Owoce"),
        p("Ananas", 50, 0.5, 13.0, 0.1, "Owoce"),
        p("Arbuz", 30, 0.6, 8.0, 0.2, "Owoce"),
        p("Mango", 60, 0.8, 15.0, 0.4, "Owoce"),
        p("Grejpfrut", 42, 0.8, 11.0, 0.1, "Owoce"),
        // === TŁUSZCZE ===
        p("Oliwa z oliwek", 884, 0.0, 0.0, 100.0, "Tłuszcze"),
        p("Olej rzepakowy", 884, 0.0, 0.0, 100.0, "Tłuszcze"),
        p("Olej lniany", 884, 0.0, 0.0, 100.0, "Tłuszcze"),
        // === ORZECHY / NASIONA ===
        p("Orzechy włoskie", 654, 15.0, 14.0, 65.0, "Orzechy"),
        p("Migdały", 579, 21.0, 22.0, 50.0, "Orzechy"),
        p("Orzechy nerkowca", 553, 18.0, 30.0, 44.0, "Orzechy"),
        p("Orzeszki ziemne", 567, 26.0, 16.0, 49.0, "Orzechy"),
        p("Masło orzechowe", 588, 25.0, 20.0, 50.0, "Orzechy"),
        p("Siemię lniane", 534, 18.0, 29.0, 42.0, "Orzechy"),
        p("Nasiona chia", 486, 17.0, 42.0, 31.0, "Orzechy"),
        p("Słonecznik (pestki)", 584, 21.0, 20.0, 51.0, "Orzechy"),
        p("Pestki dyni", 559, 30.0, 11.0, 49.0, "Orzechy"),
        // === STRĄCZKI ===
        p("Soczewica czerwona", 353, 24.0, 60.0, 1.5, "Strączki"),
        p("Ciecierzyca", 364, 19.0, 61.0, 6.0, "Strączki"),
        p("Fasola czerwona", 337, 23.0, 61.0, 1.0, "Strączki"),
        p("Groch", 341, 24.0, 60.0, 1.5, "Strączki"),
        p("Tofu", 144, 15.0, 2.0, 8.0, "Strączki"),
        p("Hummus", 177, 8.0, 20.0, 8.0, "Strączki"),
        // === INNE ===
        p("Miód", 304, 0.3, 82.0, 0.0, "Inne"),
        p("Cukier", 400, 0.0, 100.0, 0.0, "Inne"),
        p("Dżem", 250, 0.5, 62.0, 0.1, "Inne"),
        p("Czekolada gorzka 70%", 546, 8.0, 46.0, 38.0, "Inne"),
        p("Kakao (proszek)", 228, 20.0, 58.0, 14.0, "Inne"),
        p("Ketchup", 112, 1.3, 26.0, 0.2, "Inne"),
        p("Musztarda", 105, 6.0, 6.0, 6.0, "Inne"),
        p("Odżywka białkowa WPC", 375, 75.0, 8.0, 5.0, "Inne")
    )
}
