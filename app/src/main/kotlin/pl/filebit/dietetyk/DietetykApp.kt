package pl.filebit.dietetyk

import android.app.Application
import pl.filebit.dietetyk.ai.ClaudeHttpApi
import pl.filebit.dietetyk.ai.OpenFoodFactsClient
import pl.filebit.dietetyk.ai.RecipeGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pl.filebit.dietetyk.data.context.DietitianContextBuilder
import pl.filebit.dietetyk.data.db.AppDatabase
import pl.filebit.dietetyk.data.db.FoodProductSeed
import pl.filebit.dietetyk.data.db.RecipeEntity
import pl.filebit.dietetyk.data.repository.ProfileRepository
import pl.filebit.dietetyk.data.repository.WeightRepository

/**
 * Klasa aplikacji — ręczna kompozycja zależności (DI Hilt dojdzie później).
 * Trzyma bazę, repozytoria, builder kontekstu AI i ustawienia (BYOK klucz).
 */
class DietetykApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val offClient: OpenFoodFactsClient by lazy { OpenFoodFactsClient() }

    val profileRepo: ProfileRepository by lazy { ProfileRepository(database.profileDao()) }
    val weightRepo: WeightRepository by lazy { WeightRepository(database.weightDao()) }

    val contextBuilder: DietitianContextBuilder by lazy {
        DietitianContextBuilder(profileRepo, weightRepo, database.energyLogDao(), database.aiMemoryDao())
    }

    /** Przepis (3 warianty) dla dania — z cache lub generowany na żądanie i cache'owany. */
    suspend fun recipeFor(mealName: String, ingredients: String): String {
        val key = FoodProductSeed.normalize(mealName)
        database.recipeDao().get(key)?.let { return it.json }
        val json = RecipeGenerator(ClaudeHttpApi(settings.apiKey)).generate(mealName, ingredients)
        database.recipeDao().upsert(RecipeEntity(mealKey = key, json = json, updatedAt = System.currentTimeMillis()))
        return json
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Zaseeduj bazę produktów przy pierwszym uruchomieniu (idempotentne).
        appScope.launch {
            val dao = database.foodProductDao()
            if (dao.count() == 0) dao.insertAll(FoodProductSeed.all)
        }
    }
}
