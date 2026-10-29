package com.swipewipe.rebuild.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(b: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE mediaId = :mediaId AND mediaSource = :source")
    suspend fun removeBookmark(mediaId: String, source: String)
}

@Dao
interface StatsDao {
    @Query("SELECT * FROM stats")
    fun observeAll(): Flow<List<StatsEntity>>

    @Upsert
    suspend fun upsert(s: StatsEntity)

    @Upsert
    suspend fun upsertStatsBatch(list: List<StatsEntity>)

    @Query("SELECT * FROM stats")
    suspend fun observeAllStats(): List<StatsEntity>
}

@Dao
interface MediaDecisionDao {
    @Query("SELECT * FROM mediaDecisions WHERE mediaSource = :source")
    suspend fun forSource(source: String): List<MediaDecisionEntity>

    @Query("SELECT mediaId FROM mediaDecisions WHERE mediaSource = :source AND decision = :decision")
    suspend fun idsFor(source: String, decision: String): List<String>

    @Upsert
    suspend fun upsert(d: MediaDecisionEntity)

    @Query("DELETE FROM mediaDecisions WHERE mediaSource = :source AND mediaId = :mediaId")
    suspend fun removeDecision(source: String, mediaId: String)
}

@Dao
interface LocalDecisionReconstructionDao {
    @Query("SELECT * FROM localDecisionReconstruction WHERE pendingSync = 1")
    suspend fun pending(): List<LocalDecisionReconstructionEntity>

    @Query("UPDATE localDecisionReconstruction SET pendingSync = 0 WHERE id = :id")
    suspend fun markSynced(id: Long)

    @Insert
    suspend fun add(e: LocalDecisionReconstructionEntity): Long
}

@Dao
interface CompletedPeriodDao {
    @Upsert
    suspend fun upsertAll(list: List<CompletedPeriodEntity>)

    @Query("SELECT * FROM completedPeriods WHERE mediaSource = :source ORDER BY year DESC, month DESC")
    fun observePeriods(source: String): Flow<List<CompletedPeriodEntity>>
}

@Dao
interface SimilarImageDao {
    @Query("SELECT COALESCE(groupId, id) AS g FROM similarImages GROUP BY g HAVING COUNT(*) > 1")
    suspend fun duplicateGroupIds(): List<String>

    @Query("SELECT * FROM similarImages WHERE COALESCE(groupId, id) IN (:ids)")
    suspend fun byGroups(ids: List<String>): List<SimilarImageEntity>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsertSimilar(rows: List<SimilarImageEntity>)

    @androidx.room.Query("UPDATE similarImages SET groupId = :gid WHERE id = :id")
    suspend fun assignOne(id: String, gid: String)

    suspend fun assignGroups(map: Map<String, String>) =
        map.forEach { (id, gid) -> assignOne(id, gid) }

    @Query("DELETE FROM similarImages WHERE id = :id")
    suspend fun remove(id: String)

    @Query("DELETE FROM similarImages")
    suspend fun clear()
}

@Dao
interface CloudMediaDao {
    @Upsert
    suspend fun upsertAll(items: List<CloudMediaItemEntity>)

    @Query("SELECT * FROM cloudMediaItems WHERE mediaSource = :source ORDER BY lastModifiedMs DESC")
    fun observe(source: String): Flow<List<CloudMediaItemEntity>>

    @Query("UPDATE cloudMediaItems SET displayUri = NULL WHERE id IN (:ids)")
    suspend fun markTrashed(ids: List<String>)

    @Query("DELETE FROM cloudMediaItems WHERE mediaSource = :source")
    suspend fun clearSource(source: String)
}

data class MonthAggregate(
    @Embedded val period: CloudMonthAggregateEntity
)

@Dao
interface CloudMonthAggregateDao {
    @Upsert
    suspend fun upsertAll(list: List<CloudMonthAggregateEntity>)

    @Query("SELECT * FROM cloudMonthAggregate ORDER BY monthKey DESC")
    fun observeAggregateAll(): Flow<List<CloudMonthAggregateEntity>>

    @Query("SELECT * FROM cloudMonthAggregate WHERE mediaSource = :source ORDER BY monthKey DESC")
    suspend fun aggregatesFor(source: String): List<CloudMonthAggregateEntity>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsertAggregateMonths(list: List<CloudMonthAggregateEntity>)
}

@Dao
interface AppDao :
    BookmarkDao, StatsDao, MediaDecisionDao, LocalDecisionReconstructionDao,
    CompletedPeriodDao, SimilarImageDao, CloudMediaDao, CloudMonthAggregateDao

@androidx.room.Database(
    entities = [
        BookmarkEntity::class, StatsEntity::class, MediaDecisionEntity::class,
        LocalDecisionReconstructionEntity::class, CompletedPeriodEntity::class,
        SimilarImageEntity::class, CloudMediaItemEntity::class, CloudMonthAggregateEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : androidx.room.RoomDatabase() {
    abstract fun appDao(): AppDao
}
