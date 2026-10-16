package app.dvphoto.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** One saved, exported photo. The image itself lives in the gallery or Downloads. */
@Entity(tableName = "photos")
data class PhotoRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val name: String,
    val createdAt: Long,
    val sizeBytes: Int,
    val sidePx: Int,
    val source: String,
    val role: String,
    val folder: String,
    val passed: Boolean,
)

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY createdAt DESC LIMIT 12")
    fun recent(): Flow<List<PhotoRecord>>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun byId(id: Long): PhotoRecord?

    @Insert
    suspend fun insert(record: PhotoRecord): Long

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [PhotoRecord::class], version = 2, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun photos(): PhotoDao

    companion object {
        @Volatile private var instance: AppDb? = null

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "dvphoto.db")
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}
