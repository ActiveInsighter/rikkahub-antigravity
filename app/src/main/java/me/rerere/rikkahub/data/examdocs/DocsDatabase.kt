package me.rerere.rikkahub.data.examdocs

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(primaryKeys = ["site", "resource"], indices = [Index("fetchedAt")])
internal data class DocsCachedResource(
    val site: String,
    val resource: String,
    val sourceUrl: String,
    val compressed: ByteArray,
    val fetchedAt: Long,
)

@Entity(
    primaryKeys = ["site", "account", "kind", "recordId"],
    indices = [Index("site", "account", "path", "kind")],
)
internal data class DocsRecord(
    val site: String,
    val account: String,
    val kind: String,
    val recordId: String,
    val path: String,
    val timestamp: Long,
    val payload: String,
    val draft: String = "",
)

@Entity(primaryKeys = ["site", "account", "kind", "recordId"])
internal data class DocsPendingRecord(
    val site: String,
    val account: String,
    val kind: String,
    val recordId: String,
    val timestamp: Long,
    val payload: String,
)

@Entity(primaryKeys = ["site", "account"])
internal data class DocsSyncPosition(
    val site: String,
    val account: String,
    val cursor: String,
    val syncedAt: Long,
)

@Dao
internal interface DocsDao {
    @Query("SELECT * FROM DocsCachedResource WHERE site = :site AND resource = :resource")
    suspend fun resource(site: String, resource: String): DocsCachedResource?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putResource(resource: DocsCachedResource)

    @Query("""
        DELETE FROM DocsCachedResource WHERE resource != 'directory' AND rowid NOT IN (
            SELECT rowid FROM DocsCachedResource WHERE resource != 'directory' ORDER BY fetchedAt DESC LIMIT 160
        )
    """)
    suspend fun pruneResources()

    @Query("SELECT * FROM DocsRecord WHERE site = :site AND account = :account AND kind = :kind AND recordId = :id")
    suspend fun record(site: String, account: String, kind: String, id: String): DocsRecord?

    @Query("SELECT * FROM DocsRecord WHERE site = :site AND account = :account")
    suspend fun records(site: String, account: String): List<DocsRecord>

    @Query("SELECT * FROM DocsRecord WHERE site = :site AND account = :account")
    fun observeRecords(site: String, account: String): Flow<List<DocsRecord>>

    @Query("""
        SELECT * FROM DocsRecord WHERE site = :site AND account = :account
        AND path = :path AND kind = 'answers'
    """)
    fun observePage(site: String, account: String, path: String): Flow<List<DocsRecord>>

    @Query("DELETE FROM DocsRecord WHERE site = :site AND account = :account AND kind = :kind AND recordId = :id")
    suspend fun deleteRecord(site: String, account: String, kind: String, id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putRecord(record: DocsRecord)

    @Query("SELECT * FROM DocsPendingRecord WHERE site = :site AND account = :account ORDER BY timestamp LIMIT :limit")
    suspend fun pending(site: String, account: String, limit: Int): List<DocsPendingRecord>

    @Query("SELECT COUNT(*) FROM DocsPendingRecord WHERE site = :site AND account = :account")
    fun observePendingCount(site: String, account: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(record: DocsPendingRecord)

    @Query("""
        DELETE FROM DocsPendingRecord WHERE site = :site AND account = :account
        AND kind = :kind AND recordId = :id AND timestamp = :timestamp
    """)
    suspend fun acknowledge(site: String, account: String, kind: String, id: String, timestamp: Long)

    @Query("SELECT * FROM DocsSyncPosition WHERE site = :site AND account = :account")
    suspend fun position(site: String, account: String): DocsSyncPosition?

    @Query("SELECT * FROM DocsSyncPosition WHERE site = :site AND account = :account")
    fun observePosition(site: String, account: String): Flow<DocsSyncPosition?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPosition(position: DocsSyncPosition)
}

@Database(
    entities = [DocsCachedResource::class, DocsRecord::class, DocsPendingRecord::class, DocsSyncPosition::class],
    version = 1,
    exportSchema = false,
)
internal abstract class DocsDatabase : RoomDatabase() {
    abstract fun dao(): DocsDao

    companion object {
        fun create(context: Context): DocsDatabase =
            Room.databaseBuilder(context, DocsDatabase::class.java, "docs-reader.db").build()
    }
}
