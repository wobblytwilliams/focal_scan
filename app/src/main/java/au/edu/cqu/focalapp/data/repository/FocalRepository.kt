package au.edu.cqu.focalapp.data.repository

import au.edu.cqu.focalapp.data.local.BehaviorEventEntity
import au.edu.cqu.focalapp.data.local.FocalDao
import au.edu.cqu.focalapp.data.local.SamplingSessionEntity
import au.edu.cqu.focalapp.data.local.GraphHistoryEntity
import au.edu.cqu.focalapp.data.local.SessionFormatVersion
import au.edu.cqu.focalapp.domain.model.AnimalBehaviourTotals
import au.edu.cqu.focalapp.domain.model.Behavior
import au.edu.cqu.focalapp.domain.model.AnimalColor
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

    suspend fun getLatestFinishedSession(): SamplingSessionEntity? = dao.getLatestFinishedSession()

    suspend fun getFinishedSessions(): List<SamplingSessionEntity> = dao.getFinishedSessions()

    suspend fun getKnownAnimalIds(): List<String> = dao.getSavedAnimalIds()
        .flatMap(SessionAnimalIdsCodec::decode)
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinctBy { it.lowercase() }

    suspend fun clearGraphHistory(nowEpochMs: Long) {
        dao.saveGraphCutoff(GraphHistoryEntity(cutoffEpochMs = nowEpochMs))
    }

    suspend fun getSessionById(sessionId: Long): SamplingSessionEntity? = dao.getSessionById(sessionId)

    suspend fun startSession(
        startedAtEpochMs: Long,
        animalIds: List<String>,
        animalColors: List<AnimalColor>,
        observerName: String,
        timeOffsetSeconds: Double
    ): Long {
        return dao.insertSession(
            SamplingSessionEntity(
                animalCount = animalIds.size,
                animalIdsJson = SessionAnimalIdsCodec.encode(animalIds),
                animalColorsJson = SessionAnimalColorsCodec.encode(animalColors),
                sessionFormatVersion = SessionFormatVersion.CUSTOM_ANIMALS,
                observerName = observerName,
                timeOffsetSeconds = timeOffsetSeconds,
                startedAtEpochMs = startedAtEpochMs
            )
        )
    }

    suspend fun stopSession(sessionId: Long, endedAtEpochMs: Long) {
        dao.finishSession(sessionId, endedAtEpochMs)
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

    suspend fun trimAnimalToCutoff(
        sessionId: Long,
        animalId: String,
        cutoffEpochMs: Long
    ) {
        dao.trimAnimalToCutoff(sessionId, animalId, cutoffEpochMs)
    }

    suspend fun getEventsForSession(sessionId: Long): List<BehaviorEventEntity> {
        return dao.getEventsForSession(sessionId)
    }

    suspend fun getCumulativeBehaviourTotals(nowEpochMs: Long): List<AnimalBehaviourTotals> {
        val cutoff = dao.getGraphCutoff() ?: Long.MIN_VALUE
        val totals = linkedMapOf<String, AnimalBehaviourTotals>()
        for (event in dao.getEventsAfterGraphCutoff(cutoff)) {
            val animalId = event.animalId.trim()
            val key = animalId.lowercase()
            val end = event.endTimeEpochMs ?: nowEpochMs
            val start = maxOf(event.startTimeEpochMs, cutoff)
            val total = totals[key] ?: AnimalBehaviourTotals(animalId)
            totals[key] = total.add(event.behaviour, (end - start).coerceAtLeast(0L))
        }
        return totals.values.toList()
    }
}
