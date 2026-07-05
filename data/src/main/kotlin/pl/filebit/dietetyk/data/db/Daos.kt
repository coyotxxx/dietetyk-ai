package pl.filebit.dietetyk.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 0")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 0")
    suspend fun get(): ProfileEntity?

    @Upsert
    suspend fun upsert(profile: ProfileEntity)
}

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight_samples ORDER BY dateMs ASC")
    fun observeAll(): Flow<List<WeightEntity>>

    @Query("SELECT * FROM weight_samples ORDER BY dateMs DESC LIMIT 1")
    suspend fun latest(): WeightEntity?

    @Query("SELECT * FROM weight_samples WHERE dateMs >= :sinceMs ORDER BY dateMs ASC")
    suspend fun since(sinceMs: Long): List<WeightEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sample: WeightEntity): Long
}
