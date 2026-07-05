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

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
