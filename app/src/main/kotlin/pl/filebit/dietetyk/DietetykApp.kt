package pl.filebit.dietetyk

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import pl.filebit.dietetyk.ai.ClaudeHttpApi
import pl.filebit.dietetyk.ai.OpenFoodFactsClient
import pl.filebit.dietetyk.ai.RecipeGenerator
import pl.filebit.dietetyk.notify.CheckInWorker
import pl.filebit.dietetyk.notify.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
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

    /** Reaktywny tryb motywu ("system"/"light"/"dark") — zmiana odświeża całe UI. */
    val themeMode by lazy { androidx.compose.runtime.mutableStateOf(settings.themeMode) }

    val profileRepo: ProfileRepository by lazy { ProfileRepository(database.profileDao()) }
    val weightRepo: WeightRepository by lazy { WeightRepository(database.weightDao()) }

    val contextBuilder: DietitianContextBuilder by lazy {
        DietitianContextBuilder(profileRepo, weightRepo, database.energyLogDao(), database.aiMemoryDao(), database.foodProductDao())
    }

    /** Przepis (3 warianty) dla dania — z cache lub generowany na żądanie i cache'owany.
     *  KLUCZ = nazwa + składniki: różne dania o tej samej nazwie (np. „Śniadanie" = jajecznica vs
     *  płatki z twarogiem) NIE mogą kolidować w cache (dawniej klucz = sama nazwa → zły przepis). */
    suspend fun recipeFor(mealName: String, ingredients: String): String {
        val ingHash = Integer.toHexString(FoodProductSeed.normalize(ingredients).hashCode())
        val key = "${FoodProductSeed.normalize(mealName)}|$ingHash"
        database.recipeDao().get(key)?.let { return it.json }
        val json = RecipeGenerator(ClaudeHttpApi(settings.apiKey)).generate(mealName, ingredients)
        database.recipeDao().upsert(RecipeEntity(mealKey = key, json = json, updatedAt = System.currentTimeMillis()))
        return json
    }

    /** „Powtórz wczoraj" — skopiuj wczorajsze wpisy jedzenia na dziś. Zwraca liczbę skopiowanych. */
    suspend fun repeatYesterday(): Int {
        val z = java.time.ZoneId.systemDefault()
        val todayStart = java.time.LocalDate.now(z).atStartOfDay(z).toInstant().toEpochMilli()
        val yesterdayStart = todayStart - 24L * 3600 * 1000
        val dao = database.energyLogDao()
        val yLogs = dao.since(yesterdayStart).filter { it.dateMs < todayStart }
        val now = System.currentTimeMillis()
        yLogs.forEach { l ->
            dao.insert(pl.filebit.dietetyk.data.db.EnergyLogEntity(
                dateMs = now, kcalConsumed = l.kcalConsumed, isComplete = l.isComplete,
                proteinG = l.proteinG, carbsG = l.carbsG, fatG = l.fatG, updatedAt = now, dirty = true
            ))
        }
        return yLogs.size
    }

    /** Wiadomość do automatycznego wysłania w czacie po przejściu tam (np. „Zacznij wizytę"). */
    var pendingChatMessage: String? = null

    /** Zdjęcie (base64 JPEG) do wysłania w czacie po przejściu tam (FAB „Sfotografuj posiłek"). */
    var pendingChatPhoto: String? = null

    /** Sygnał: wyczyść rozmowę w pamięci ViewModelu (bazę i prefs czyści ekran Profilu). */
    var pendingChatClear: Boolean = false

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        // Zaseeduj bazę produktów przy pierwszym uruchomieniu (idempotentne).
        appScope.launch {
            val dao = database.foodProductDao()
            if (dao.count() == 0) dao.insertAll(FoodProductSeed.all)
        }
        // Backfill: przenieś cel/posiłki/preferencje z prefs do profilu (żeby łapała je kopia .db).
        appScope.launch { backfillPrefsToProfile() }
        // Cotygodniowa wizyta kontrolna (KEEP — nie resetuj harmonogramu przy każdym starcie).
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "weekly_checkin",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CheckInWorker>(7, TimeUnit.DAYS).build()
        )
        // Codzienna automatyczna kopia zapasowa (lokalna) — zero utraty danych.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_backup",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<pl.filebit.dietetyk.notify.BackupWorker>(1, TimeUnit.DAYS).build()
        )
        // Codzienny kontakt dietetyka (poranek / smart-nudge / wieczór + promocja insightów). Tyka co
        // godzinę i wysyła TYLKO w oknach czasowych, przez NotificationPolicy (poziom/cisza/sufit). KEEP.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_nudge",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<pl.filebit.dietetyk.notify.DailyNudgeWorker>(1, TimeUnit.HOURS).build()
        )
    }

    /** Jednorazowe przeniesienie cel/posiłki/preferencje z SharedPreferences do profilu w Room. */
    private suspend fun backfillPrefsToProfile() {
        if (settings.prefsMigratedToProfile) return
        val prof = profileRepo.get() ?: return   // brak profilu → spróbuj przy kolejnym starcie
        val g = settings.goalWeightKg.takeIf { it > 0 }
        val d = settings.dietaryPrefs.takeIf { it.isNotBlank() }
        val merged = prof.copy(
            goalWeightKg = prof.goalWeightKg ?: g,
            mealsPerDay = prof.mealsPerDay ?: settings.mealsPerDay,
            dietaryPrefs = prof.dietaryPrefs ?: d
        )
        if (merged != prof) profileRepo.save(merged, System.currentTimeMillis())
        settings.prefsMigratedToProfile = true
    }

    /** Ręczne wyzwolenie wizyty (przycisk „Sprawdź teraz" / test). */
    fun triggerCheckInNow() {
        WorkManager.getInstance(this).enqueue(OneTimeWorkRequestBuilder<CheckInWorker>().build())
    }
}
