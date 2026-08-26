package dev.stan.alarum.alarm

import dev.stan.alarum.domain.Challenge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Everything the ring screen needs to draw itself. */
data class RingUiState(
    val alarmId: String,
    val alarmLabel: String,
    val stageName: String,
    val stageIndex: Int,
    val totalStages: Int,
    val stageProgress: Float,
    val elapsedSec: Int,
    val secondsToNextStage: Int?,
    val isFinalStage: Boolean,
    val allowSnooze: Boolean,
    val snoozeMinutes: Int,
    val screenBrightness: Float,
    val audioLevel: Float,
    val challenge: Challenge,
)

/**
 * The bridge between the ring service and the ring screen.
 *
 * A plain object rather than a bound service because there is at most one ring
 * at a time and the activity may be created and destroyed underneath it.
 */
object Ringing {
    private val _state = MutableStateFlow<RingUiState?>(null)
    val state: StateFlow<RingUiState?> = _state.asStateFlow()

    @Volatile var onDismiss: (() -> Unit)? = null
    @Volatile var onSnooze: (() -> Unit)? = null

    fun publish(state: RingUiState?) {
        _state.value = state
    }

    val isRinging: Boolean get() = _state.value != null
}
