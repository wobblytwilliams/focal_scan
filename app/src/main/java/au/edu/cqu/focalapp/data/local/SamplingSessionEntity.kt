package au.edu.cqu.focalapp.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SamplingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "animal_count") val animalCount: Int,
    @ColumnInfo(name = "animal_ids_json") val animalIdsJson: String,
    @ColumnInfo(name = "started_at") val startedAtEpochMs: Long,
    @ColumnInfo(name = "ended_at") val endedAtEpochMs: Long? = null
)
