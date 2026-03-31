package au.edu.cqu.focalapp.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import au.edu.cqu.focalapp.domain.model.Behavior

@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = SamplingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("session_id"),
        Index(value = ["session_id", "animal_id"])
    ]
)
data class BehaviorEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "animal_id") val animalId: String,
    val behaviour: Behavior,
    @ColumnInfo(name = "start_time") val startTimeEpochMs: Long,
    @ColumnInfo(name = "end_time") val endTimeEpochMs: Long? = null
)
