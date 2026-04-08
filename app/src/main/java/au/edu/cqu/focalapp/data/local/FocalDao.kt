package au.edu.cqu.focalapp.data.local

import androidx.room.Dao
import androidx.room.Insert
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

    @Query(
        """
        SELECT events.*
        FROM events
        INNER JOIN sessions ON sessions.id = events.session_id
        WHERE sessions.session_format_version >= :minimumSessionFormatVersion
        ORDER BY events.start_time ASC, events.id ASC
        """
    )
    suspend fun getEventsForSessionFormat(
        minimumSessionFormatVersion: Int
    ): List<BehaviorEventEntity>

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
