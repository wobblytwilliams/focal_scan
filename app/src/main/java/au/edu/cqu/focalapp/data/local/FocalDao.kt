package au.edu.cqu.focalapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface FocalDao {
    @Insert
    suspend fun insertSession(session: SamplingSessionEntity): Long

    @Query("UPDATE sessions SET ended_at = :endedAtEpochMs WHERE id = :sessionId")
    suspend fun endSession(sessionId: Long, endedAtEpochMs: Long)

    @Query("SELECT * FROM sessions WHERE ended_at IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun getActiveSession(): SamplingSessionEntity?

    @Query("SELECT * FROM sessions WHERE ended_at IS NOT NULL ORDER BY id DESC LIMIT 1")
    suspend fun getLatestFinishedSession(): SamplingSessionEntity?

    @Query("SELECT * FROM sessions WHERE ended_at IS NOT NULL ORDER BY id DESC")
    suspend fun getFinishedSessions(): List<SamplingSessionEntity>

    @Query("SELECT animal_ids_json FROM sessions ORDER BY id DESC")
    suspend fun getSavedAnimalIds(): List<String>

    @Query("SELECT cutoff_epoch_ms FROM graph_history WHERE id = 1")
    suspend fun getGraphCutoff(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveGraphCutoff(history: GraphHistoryEntity)

    @Query("UPDATE events SET end_time = :endedAtEpochMs WHERE session_id = :sessionId AND end_time IS NULL")
    suspend fun closeOpenEvents(sessionId: Long, endedAtEpochMs: Long)

    @Transaction
    suspend fun finishSession(sessionId: Long, endedAtEpochMs: Long) {
        closeOpenEvents(sessionId, endedAtEpochMs)
        endSession(sessionId, endedAtEpochMs)
    }

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

    @Query(
        "SELECT * FROM events WHERE end_time IS NULL OR end_time > :cutoffEpochMs"
    )
    suspend fun getEventsAfterGraphCutoff(cutoffEpochMs: Long): List<BehaviorEventEntity>

    @Query("DELETE FROM events WHERE session_id = :sessionId AND start_time >= :cutoffEpochMs")
    suspend fun deleteEventsStartingFrom(sessionId: Long, cutoffEpochMs: Long): Int

    @Query(
        """
        UPDATE events
        SET end_time = :cutoffEpochMs
        WHERE session_id = :sessionId
            AND start_time < :cutoffEpochMs
            AND (end_time IS NULL OR end_time > :cutoffEpochMs)
        """
    )
    suspend fun truncateEventsAtCutoff(sessionId: Long, cutoffEpochMs: Long): Int

    @Transaction
    suspend fun trimSessionToCutoff(sessionId: Long, cutoffEpochMs: Long) {
        deleteEventsStartingFrom(sessionId, cutoffEpochMs)
        truncateEventsAtCutoff(sessionId, cutoffEpochMs)
    }

    @Query(
        """
        DELETE FROM events
        WHERE session_id = :sessionId
            AND animal_id = :animalId
            AND start_time >= :cutoffEpochMs
        """
    )
    suspend fun deleteAnimalEventsStartingFrom(
        sessionId: Long,
        animalId: String,
        cutoffEpochMs: Long
    ): Int

    @Query(
        """
        UPDATE events
        SET end_time = :cutoffEpochMs
        WHERE session_id = :sessionId
            AND animal_id = :animalId
            AND start_time < :cutoffEpochMs
            AND (end_time IS NULL OR end_time > :cutoffEpochMs)
        """
    )
    suspend fun truncateAnimalEventsAtCutoff(
        sessionId: Long,
        animalId: String,
        cutoffEpochMs: Long
    ): Int

    @Transaction
    suspend fun trimAnimalToCutoff(
        sessionId: Long,
        animalId: String,
        cutoffEpochMs: Long
    ) {
        deleteAnimalEventsStartingFrom(sessionId, animalId, cutoffEpochMs)
        truncateAnimalEventsAtCutoff(sessionId, animalId, cutoffEpochMs)
    }
}
