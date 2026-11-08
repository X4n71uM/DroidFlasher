package net.xantium.droidflasher.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "backup_sessions")
data class BackupSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceModel: String,
    val displayFolderPath: String,
    val localMirrorPath: String,
    val partitionCount: Int,
    val totalBytes: Long,
    val includesEfsRadio: Boolean,
    val exportedFileListCsv: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_pit_profiles")
data class SavedPitProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileName: String,
    val deviceModel: String,
    val entryCount: Int,
    val rawPitContent: String,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Dao
interface WorkbenchDao {
    @Query("SELECT * FROM backup_sessions ORDER BY createdAtMillis DESC")
    fun observeBackups(): Flow<List<BackupSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackupSession(session: BackupSessionEntity): Long

    @Query("DELETE FROM backup_sessions WHERE id = :id")
    suspend fun deleteBackupSession(id: Long)

    @Query("SELECT * FROM saved_pit_profiles ORDER BY updatedAtMillis DESC")
    fun observeSavedPitProfiles(): Flow<List<SavedPitProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPitProfile(profile: SavedPitProfileEntity): Long

    @Query("DELETE FROM saved_pit_profiles WHERE id = :id")
    suspend fun deletePitProfile(id: Long)
}

@Database(
    entities = [BackupSessionEntity::class, SavedPitProfileEntity::class],
    version = 1,
    exportSchema = false
)
abstract class WorkbenchDatabase : RoomDatabase() {
    abstract fun workbenchDao(): WorkbenchDao

    companion object {
        @Volatile
        private var INSTANCE: WorkbenchDatabase? = null

        fun getInstance(context: Context): WorkbenchDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WorkbenchDatabase::class.java,
                    "droidflasher_workbench.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class WorkbenchRepository(private val dao: WorkbenchDao) {
    val backupSessions: Flow<List<BackupSessionEntity>> = dao.observeBackups()
    val savedPitProfiles: Flow<List<SavedPitProfileEntity>> = dao.observeSavedPitProfiles()

    suspend fun saveBackupSession(session: BackupSessionEntity) = dao.insertBackupSession(session)
    suspend fun deleteBackupSession(id: Long) = dao.deleteBackupSession(id)

    suspend fun savePitProfile(profile: SavedPitProfileEntity) = dao.insertPitProfile(profile)
    suspend fun deletePitProfile(id: Long) = dao.deletePitProfile(id)
}
