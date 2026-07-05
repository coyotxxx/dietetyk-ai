package pl.filebit.dietetyk.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pl.filebit.dietetyk.core.model.NutritionProfile
import pl.filebit.dietetyk.core.model.WeightSample
import pl.filebit.dietetyk.data.db.ProfileDao
import pl.filebit.dietetyk.data.db.WeightDao
import pl.filebit.dietetyk.data.mapper.toEntity
import pl.filebit.dietetyk.data.mapper.toModel

/**
 * Repozytoria — oddają/przyjmują MODELE rdzenia, nie encje. Warstwa `:ai`/`:app` widzi tylko modele.
 * `nowMs` wstrzykiwane (nie `System.currentTimeMillis()` w środku) — testowalność + determinizm sync.
 */
class ProfileRepository(private val dao: ProfileDao) {
    fun observe(): Flow<NutritionProfile?> = dao.observe().map { it?.toModel() }
    suspend fun get(): NutritionProfile? = dao.get()?.toModel()
    suspend fun save(profile: NutritionProfile, nowMs: Long) = dao.upsert(profile.toEntity(nowMs))
}

class WeightRepository(private val dao: WeightDao) {
    fun observeAll(): Flow<List<WeightSample>> = dao.observeAll().map { list -> list.map { it.toModel() } }
    suspend fun latest(): WeightSample? = dao.latest()?.toModel()
    suspend fun since(sinceMs: Long): List<WeightSample> = dao.since(sinceMs).map { it.toModel() }
    suspend fun add(sample: WeightSample, nowMs: Long): Long = dao.insert(sample.toEntity(nowMs))
}
