package au.edu.cqu.focalapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface FocalDao {
    @Insert
    suspend fun insertSession(session: SamplingSessionEntity): Long

    @Query("UPDATE sessions SET ended_at = :endedAtEpochMs WHERE id = :sessionId")
    suspend fun endSession(sessionId: Long, endedAtEpochMs: Long)

    @Query("SELECT * FROM sessions WHERE ended_at IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun getActiveSession(): SamplingSessionEntity?

    @Query("SELECT * FROM sessions ORDER BY id DESC LIMIT 1")
    suspend fun getLatestSession(): SamplingSessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: Long): SamplingSessionEntity?

    @Insert
    suspend fun insertEvent(event: BehaviorEventEntity): Long

    @Query("UPDATE events SET end_time = :endTimeEpochMs WHERE id = :eventId AND end_time IS NULL")
    suspend fun endEvent(eventId: Long, endTimeEpochMs: Long)

    @Query("SELECT * FROM events WHERE session_id = :sessionId AND end_time IS NULL ORDER BY start_time ASC, id ASC")
    suspend fun getOpenEventsForSession(sessionId: Long): List<BehaviorEventEntity>

    @Query("SELECT * FROM events WHERE session_id = :sessionId ORDER BY start_time ASC, id ASC")
    suspend fun getEventsForSession(sessionId: Long): List<BehaviorEventEntity>
}
