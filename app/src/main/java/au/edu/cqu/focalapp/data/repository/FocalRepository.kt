package au.edu.cqu.focalapp.data.repository

import au.edu.cqu.focalapp.data.local.BehaviorEventEntity
import au.edu.cqu.focalapp.data.local.FocalDao
import au.edu.cqu.focalapp.data.local.SamplingSessionEntity
import au.edu.cqu.focalapp.domain.model.AnimalColor
import au.edu.cqu.focalapp.domain.model.Behavior
import au.edu.cqu.focalapp.util.SessionAnimalColorsCodec
import au.edu.cqu.focalapp.util.SessionAnimalIdsCodec

data class ActiveSessionSnapshot(
    val session: SamplingSessionEntity,
    val openEvents: List<BehaviorEventEntity>
)

class FocalRepository(
    private val dao: FocalDao
) {
    suspend fun getActiveSessionSnapshot(): ActiveSessionSnapshot? {
        val session = dao.getActiveSession() ?: return null
        return ActiveSessionSnapshot(
            session = session,
            openEvents = dao.getOpenEventsForSession(session.id)
        )
    }

    suspend fun getLatestSession(): SamplingSessionEntity? = dao.getLatestSession()

    suspend fun getSessionById(sessionId: Long): SamplingSessionEntity? = dao.getSessionById(sessionId)

    suspend fun startSession(
        startedAtEpochMs: Long,
        animalCount: Int,
        animalIds: List<String>,
        animalColors: List<AnimalColor>
    ): Long {
        return dao.insertSession(
            SamplingSessionEntity(
                animalCount = animalCount,
                animalIdsJson = SessionAnimalIdsCodec.encode(animalIds),
                animalColorsJson = SessionAnimalColorsCodec.encode(animalColors),
                startedAtEpochMs = startedAtEpochMs
            )
        )
    }

    suspend fun stopSession(sessionId: Long, endedAtEpochMs: Long) {
        dao.endSession(sessionId, endedAtEpochMs)
    }

    suspend fun startEvent(
        sessionId: Long,
        animalId: String,
        behaviour: Behavior,
        startTimeEpochMs: Long
    ): Long {
        return dao.insertEvent(
            BehaviorEventEntity(
                sessionId = sessionId,
                animalId = animalId,
                behaviour = behaviour,
                startTimeEpochMs = startTimeEpochMs
            )
        )
    }

    suspend fun endEvent(eventId: Long, endTimeEpochMs: Long) {
        dao.endEvent(eventId, endTimeEpochMs)
    }

    suspend fun trimSessionToCutoff(sessionId: Long, cutoffEpochMs: Long) {
        dao.trimSessionToCutoff(sessionId, cutoffEpochMs)
    }

    suspend fun getEventsForSession(sessionId: Long): List<BehaviorEventEntity> {
        return dao.getEventsForSession(sessionId)
    }
}
