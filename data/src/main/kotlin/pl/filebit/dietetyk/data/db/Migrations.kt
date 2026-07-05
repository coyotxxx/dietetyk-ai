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

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
