package pl.filebit.dietetyk.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migracje Room. ŻELAZNA ZASADA: prawdziwe `Migration(N,N+1)` z DDL, NIGDY destructive — zero utraty
 * danych usera. SQL musi ODPOWIADAĆ schematowi generowanemu przez Room (typy: Boolean/Int/Long=INTEGER,
 * String=TEXT; autoGenerate PK = INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL).
 */

/** v1→v2: logi energii dnia + pamięć epizodyczna AI. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `energy_logs` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`dateMs` INTEGER NOT NULL, `kcalConsumed` INTEGER NOT NULL, " +
                "`isComplete` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `dirty` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ai_memory` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `dirty` INTEGER NOT NULL)"
        )
    }
}

/** v2→v3: aktualny plan diety (JSON). */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `diet_plan` (" +
                "`id` INTEGER PRIMARY KEY NOT NULL, " +
                "`planJson` TEXT NOT NULL, `targetKcal` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `dirty` INTEGER NOT NULL)"
        )
    }
}

/** v3→v4: katalog produktów spożywczych. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `food_products` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `nameNorm` TEXT NOT NULL, `kcal` INTEGER NOT NULL, " +
                "`proteinG` REAL NOT NULL, `carbsG` REAL NOT NULL, `fatG` REAL NOT NULL, " +
                "`category` TEXT NOT NULL, `source` TEXT NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_products_nameNorm` ON `food_products` (`nameNorm`)")
    }
}

/** v4→v5: cache przepisów. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recipe_cache` (" +
                "`mealKey` TEXT PRIMARY KEY NOT NULL, `json` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL)"
        )
    }
}

/** v5→v6: historia powiadomień. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `notifications` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timeMs` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, `body` TEXT NOT NULL, `read` INTEGER NOT NULL)"
        )
    }
}

/** v6→v7: makro w logu posiłków (do pierścieni na Dziś). */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `energy_logs` ADD COLUMN `proteinG` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `energy_logs` ADD COLUMN `carbsG` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `energy_logs` ADD COLUMN `fatG` INTEGER NOT NULL DEFAULT 0")
    }
}

/** v7→v8: trwała historia czatu. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `chat_messages` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fromUser` INTEGER NOT NULL, " +
                "`text` TEXT NOT NULL, `actionsCsv` TEXT NOT NULL, `cardsJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
    }
}

/** v8→v9: pomiar ciała — obwód pasa + tkanka tłuszczowa (nullable, nie-destrukcyjne). */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `weight_samples` ADD COLUMN `waistCm` REAL")
        db.execSQL("ALTER TABLE `weight_samples` ADD COLUMN `bodyFatPct` REAL")
    }
}

/** v9→v10: historia wizyt kontrolnych. */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `visit_reports` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dateMs` INTEGER NOT NULL, " +
                "`deltaKg` REAL, `adherencePct` INTEGER, `decisionText` TEXT NOT NULL)"
        )
    }
}

/** v10→v11: cel/liczba posiłków/preferencje w profilu (żeby łapała je kopia zapasowa .db). */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `profile` ADD COLUMN `goalWeightKg` REAL")
        db.execSQL("ALTER TABLE `profile` ADD COLUMN `mealsPerDay` INTEGER")
        db.execSQL("ALTER TABLE `profile` ADD COLUMN `dietaryPrefs` TEXT")
    }
}

/** v11→v12: miniatura zdjęcia w wiadomości czatu. */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `imageUri` TEXT NOT NULL DEFAULT ''")
    }
}

/** v12→v13: wyczyść cache przepisów (zmiana formatu na tablicę kroków — dane regenerowalne). */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM recipe_cache")
    }
}

/** v13→v14: ulubione produkty + kod kreskowy (baza produktów widoczna dla usera). */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `food_products` ADD COLUMN `favorite` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `food_products` ADD COLUMN `barcode` TEXT")
    }
}

/** v14→v15: zdjęcie produktu (opakowanie z OpenFoodFacts przy skanowaniu kodu). */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `food_products` ADD COLUMN `imageUrl` TEXT")
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
