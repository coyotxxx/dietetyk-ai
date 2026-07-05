package pl.filebit.dietetyk.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Baza Room aplikacji. Wersja 1 (start).
 *
 * ŻELAZNA ZASADA (dziedziczona z GymTrackera): przy KAŻDEJ zmianie schematu — prawdziwa
 * `Migration(N, N+1)` z ALTER TABLE, NIGDY `fallbackToDestructiveMigration`. Zero utraty danych usera.
 */
@Database(
    entities = [ProfileEntity::class, WeightEntity::class, EnergyLogEntity::class, AiMemoryEntity::class, PlanEntity::class, FoodProductEntity::class, RecipeEntity::class, NotificationEntity::class, ChatMessageEntity::class],
    version = 8,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun weightDao(): WeightDao
    abstract fun energyLogDao(): EnergyLogDao
    abstract fun aiMemoryDao(): AiMemoryDao
    abstract fun planDao(): PlanDao
    abstract fun foodProductDao(): FoodProductDao
    abstract fun recipeDao(): RecipeDao
    abstract fun notificationDao(): NotificationDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "dietetyk.db")
                // Świadomie BEZ fallbackToDestructiveMigration — prawdziwe migracje.
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
