package pl.filebit.dietetyk.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Powiadomienie/wiadomość proaktywna Dietetyka — historia pod dzwonkiem. */
@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timeMs: Long,
    val title: String,
    val body: String,
    val read: Boolean = false
)

@Dao
interface NotificationDao {
    @Insert
    suspend fun insert(n: NotificationEntity): Long

    @Query("SELECT * FROM notifications ORDER BY timeMs DESC LIMIT 50")
    fun observe(): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE read = 0")
    fun unreadCount(): Flow<Int>

    @Query("UPDATE notifications SET read = 1 WHERE read = 0")
    suspend fun markAllRead()
}
