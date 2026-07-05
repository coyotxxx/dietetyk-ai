package pl.filebit.dietetyk.data.mapper

import pl.filebit.dietetyk.core.model.DailyEnergyLog
import pl.filebit.dietetyk.data.db.EnergyLogEntity

fun EnergyLogEntity.toModel(): DailyEnergyLog =
    DailyEnergyLog(dateMs = dateMs, kcalConsumed = kcalConsumed, isComplete = isComplete)

fun DailyEnergyLog.toEntity(updatedAt: Long, dirty: Boolean = true): EnergyLogEntity =
    EnergyLogEntity(dateMs = dateMs, kcalConsumed = kcalConsumed, isComplete = isComplete, updatedAt = updatedAt, dirty = dirty)
