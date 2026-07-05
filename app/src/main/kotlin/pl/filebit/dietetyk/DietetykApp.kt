package pl.filebit.dietetyk

import android.app.Application
import pl.filebit.dietetyk.data.context.DietitianContextBuilder
import pl.filebit.dietetyk.data.db.AppDatabase
import pl.filebit.dietetyk.data.repository.ProfileRepository
import pl.filebit.dietetyk.data.repository.WeightRepository

/**
 * Klasa aplikacji — ręczna kompozycja zależności (DI Hilt dojdzie później).
 * Trzyma bazę, repozytoria, builder kontekstu AI i ustawienia (BYOK klucz).
 */
class DietetykApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }

    val profileRepo: ProfileRepository by lazy { ProfileRepository(database.profileDao()) }
    val weightRepo: WeightRepository by lazy { WeightRepository(database.weightDao()) }

    val contextBuilder: DietitianContextBuilder by lazy {
        DietitianContextBuilder(profileRepo, weightRepo, database.energyLogDao(), database.aiMemoryDao())
    }
}
