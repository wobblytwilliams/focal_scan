package au.edu.cqu.focalapp.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "graph_history")
data class GraphHistoryEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "cutoff_epoch_ms") val cutoffEpochMs: Long
)
