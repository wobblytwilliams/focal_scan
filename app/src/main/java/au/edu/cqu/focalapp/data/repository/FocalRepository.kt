package au.edu.cqu.focalapp.data.repository

import au.edu.cqu.focalapp.data.local.BehaviorEventEntity
import au.edu.cqu.focalapp.data.local.FocalDao
import au.edu.cqu.focalapp.data.local.SamplingSessionEntity
import au.edu.cqu.focalapp.data.local.SessionFormatVersion
import au.edu.cqu.focalapp.domain.model.AnimalBehaviourTotals
import au.edu.cqu.focalapp.domain.model.Behavior
import au.edu.cqu.focalapp.domain.model.TrackedAnimal
import au.edu.cqu.focalapp.util.SessionAnimalColorsCodec
import au.edu.cqu.focalapp.util.SessionAnimalIdsCodec
import au.edu.cqu.focalapp.util.SessionTrackedAnimalsCodec

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
        trackedAnimals: List<TrackedAnimal>,
        observerName: String,
        timeOffsetSeconds: Double
    ): Long {
        val animalIds = trackedAnimals.map(TrackedAnimal::displayName)
        val animalColors = trackedAnimals.map(TrackedAnimal::animalColor)
        return dao.insertSession(
            SamplingSessionEntity(
                animalCount = trackedAnimals.size,
                animalIdsJson = SessionAnimalIdsCodec.encode(animalIds),
                animalColorsJson = SessionAnimalColorsCodec.encode(animalColors),
                trackedAnimalsJson = SessionTrackedAnimalsCodec.encode(trackedAnimals),
                sessionFormatVersion = SessionFormatVersion.TRACKED_ANIMALS,
                observerName = observerName,
                timeOffsetSeconds = timeOffsetSeconds,
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
        val totalsByAnimal = dao.getEventsForSessionFormat(
            minimumSessionFormatVersion = SessionFormatVersion.TRACKED_ANIMALS
        ).fold(
            initial = TrackedAnimal.entries.associateWith { trackedAnimal ->
                AnimalBehaviourTotals(trackedAnimal)
            }
        ) { totals, event ->
            val trackedAnimal = TrackedAnimal.fromStoredAnimalId(event.animalId) ?: return@fold totals
            val endTimeEpochMs = event.endTimeEpochMs ?: nowEpochMs
            val durationMs = (endTimeEpochMs - event.startTimeEpochMs).coerceAtLeast(0L)

            totals + (
                trackedAnimal to totals.getValue(trackedAnimal).add(
                    behaviour = event.behaviour,
                    durationMs = durationMs
                )
            )
        }

        return TrackedAnimal.entries.map { trackedAnimal ->
            totalsByAnimal.getValue(trackedAnimal)
        }
    }
}
