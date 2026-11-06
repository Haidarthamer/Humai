package com.example.core.personality

import com.example.core.model.CommunicationStyle
import com.example.core.model.EmotionState
import com.example.core.model.PersonalityTraits
import com.example.core.model.SimulatedEmotion
import com.example.database.dao.PersonalityDao
import com.example.database.entities.PersonalityProfileEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

class PersonalityAndEmotionEngines(
    private val personalityDao: PersonalityDao
) {

    private val _traits = MutableStateFlow(PersonalityTraits())
    val traits: StateFlow<PersonalityTraits> = _traits.asStateFlow()

    private val _emotion = MutableStateFlow(EmotionState())
    val emotion: StateFlow<EmotionState> = _emotion.asStateFlow()

    private val _evolutionHistory = MutableStateFlow<List<String>>(
        listOf("Initial baseline neural calibration: High curiosity, warm friendliness.")
    )
    val evolutionHistory: StateFlow<List<String>> = _evolutionHistory.asStateFlow()

    suspend fun loadProfile() {
        val entity = personalityDao.getPersonalityProfileDirect()
        if (entity != null) {
            _traits.value = PersonalityTraits(
                humor = entity.humor,
                curiosity = entity.curiosity,
                friendliness = entity.friendliness,
                patience = entity.patience,
                creativity = entity.creativity,
                formality = entity.formality,
                enthusiasm = entity.enthusiasm,
                communicationStyle = runCatching {
                    CommunicationStyle.valueOf(entity.communicationStyle)
                }.getOrDefault(CommunicationStyle.EMPATHETIC)
            )
            _emotion.value = EmotionState(
                current = runCatching {
                    SimulatedEmotion.valueOf(entity.simulatedEmotion)
                }.getOrDefault(SimulatedEmotion.CURIOSITY),
                intensity = entity.emotionIntensity,
                lastTrigger = entity.lastEvolutionReason
            )
        } else {
            saveProfile("Baseline profile established")
        }
    }

    suspend fun updateTraits(newTraits: PersonalityTraits, reason: String = "User calibrated matrix") {
        _traits.value = newTraits
        saveProfile(reason)
    }

    suspend fun resetToBaseline() {
        _traits.value = PersonalityTraits()
        _emotion.value = EmotionState(
            current = SimulatedEmotion.CURIOSITY,
            intensity = 0.7f,
            lastTrigger = "Matrix reset to pristine baseline"
        )
        saveProfile("Reset matrix to baseline")
    }

    fun triggerEmotion(newEmotion: SimulatedEmotion, intensity: Float, trigger: String) {
        _emotion.value = EmotionState(
            current = newEmotion,
            intensity = intensity.coerceIn(0.1f, 1.0f),
            lastTrigger = trigger,
            triggerTimestamp = System.currentTimeMillis()
        )
    }

    /**
     * Decays current emotional intensity towards baseline (Homeostasis).
     */
    fun decayEmotion() {
        val current = _emotion.value
        val elapsedMinutes = (System.currentTimeMillis() - current.triggerTimestamp) / (60 * 1000f)
        if (elapsedMinutes > 5f && current.intensity > 0.5f) {
            val decayedIntensity = max(0.5f, current.intensity - (elapsedMinutes * 0.05f))
            _emotion.value = current.copy(intensity = decayedIntensity)
        }
    }

    /**
     * Dynamically evolves personality based on conversational interaction cues.
     */
    suspend fun processDialogueEvolution(userMessage: String) {
        val lower = userMessage.lowercase()
        val current = _traits.value
        var changed = false
        var reason = ""

        var newHumor = current.humor
        var newCuriosity = current.curiosity
        var newPatience = current.patience
        var newCreativity = current.creativity

        // If user jokes or asks for humor
        if (lower.contains("joke") || lower.contains("funny") || lower.contains("ضحك") || lower.contains("نكتة")) {
            newHumor = (newHumor + 0.02f).coerceAtMost(0.95f)
            changed = true
            reason = "Adaptive response: heightened humor responsiveness"
            triggerEmotion(SimulatedEmotion.HAPPINESS, 0.85f, "Humorous interaction detected")
        }

        // If user asks deep academic/scientific questions
        if (lower.contains("why") || lower.contains("how") || lower.contains("explain") || lower.contains("لماذا") || lower.contains("كيف") || lower.contains("اشرحلي")) {
            newCuriosity = (newCuriosity + 0.015f).coerceAtMost(0.98f)
            changed = true
            reason = "Adaptive response: deep inquiry reinforced curiosity"
            triggerEmotion(SimulatedEmotion.CURIOSITY, 0.90f, "Intellectual exploration prompt")
        }

        // If user writes long detailed prompt
        if (userMessage.length > 180) {
            newPatience = (newPatience + 0.015f).coerceAtMost(0.95f)
            newCreativity = (newCreativity + 0.01f).coerceAtMost(0.95f)
            changed = true
            reason = "Adaptive response: increased patience for comprehensive user input"
        }

        if (changed) {
            _traits.value = current.copy(
                humor = newHumor,
                curiosity = newCuriosity,
                patience = newPatience,
                creativity = newCreativity
            )
            saveProfile(reason)
            _evolutionHistory.value = listOf(reason) + _evolutionHistory.value.take(15)
        }
    }

    private suspend fun saveProfile(reason: String) {
        val t = _traits.value
        val e = _emotion.value
        personalityDao.upsertPersonalityProfile(
            PersonalityProfileEntity(
                id = 1,
                humor = t.humor,
                curiosity = t.curiosity,
                friendliness = t.friendliness,
                patience = t.patience,
                creativity = t.creativity,
                formality = t.formality,
                enthusiasm = t.enthusiasm,
                communicationStyle = t.communicationStyle.name,
                simulatedEmotion = e.current.name,
                emotionIntensity = e.intensity,
                lastEvolutionReason = reason,
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}
