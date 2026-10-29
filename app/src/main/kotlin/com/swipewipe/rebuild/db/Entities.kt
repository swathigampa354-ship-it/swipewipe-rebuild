package com.swipewipe.rebuild.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks", primaryKeys = ["mediaId", "mediaSource"])
data class BookmarkEntity(
    val mediaId: String,
    val mediaSource: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "stats")
data class StatsEntity(
    @PrimaryKey val mediaSource: String,
    val swipedCount: Int = 0,
    val deletedCount: Int = 0,
    val totalSize: Long = 0,
    val timesSeenCount: Int = 0
)

@Entity(tableName = "mediaDecisions", primaryKeys = ["mediaId", "mediaSource"])
data class MediaDecisionEntity(
    val mediaSource: String,
    val mediaId: String,
    val decision: String
)

@Entity(tableName = "localDecisionReconstruction")
data class LocalDecisionReconstructionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaSource: String,
    val mediaId: String,
    val decision: String,
    val pendingSync: Boolean = true
)

@Entity(tableName = "completedPeriods", primaryKeys = ["mediaSource", "year", "month"])
data class CompletedPeriodEntity(
    val mediaSource: String,
    val year: Int,
    val month: Int
)

@Entity(
    tableName = "similarImages",
    indices = [Index("groupId")]
)
data class SimilarImageEntity(
    @PrimaryKey val id: String,
    val mediaSource: String,
    val mediaId: String,
    val signatureBytes: ByteArray,
    val groupId: String?,
    val modifiedMs: Long
)

@Entity(tableName = "cloudMediaItems", primaryKeys = ["id", "mediaSource"])
data class CloudMediaItemEntity(
    val id: String,
    val mediaSource: String,
    val name: String,
    val displayUri: String?,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
    val isVideo: Boolean,
    val monthKey: String
)

@Entity(tableName = "cloudMonthAggregate", primaryKeys = ["mediaSource", "monthKey"])
data class CloudMonthAggregateEntity(
    val mediaSource: String,
    val monthKey: String,
    val itemCount: Int,
    val totalSize: Long
)
