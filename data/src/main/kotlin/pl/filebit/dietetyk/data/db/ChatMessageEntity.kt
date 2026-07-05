package pl.filebit.dietetyk.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/** Wiadomość czatu — trwała historia rozmowy (przeżywa zmianę zakładki i restart). */
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromUser: Boolean,
    val text: String,
    val actionsCsv: String = "",   // [[akcje]] spłaszczone "A|B|C"
    val cardsJson: String = "",    // JSON array surowych kart
    val imageUri: String = "",     // ścieżka pliku miniatury (filesDir/chat_images) lub puste
    val createdAt: Long
)

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY id ASC")
    suspend fun all(): List<ChatMessageEntity>

    @Insert
    suspend fun insert(m: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clear()
}
