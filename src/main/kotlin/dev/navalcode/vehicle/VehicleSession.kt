package dev.navalcode.vehicle

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A deliberately fictional contract, not a replacement for Android Car APIs. */
fun interface VehicleGateway {
    fun open(callback: (VehicleEvent) -> Unit): AutoCloseable
}

enum class Metric(val unit: String, val range: ClosedFloatingPointRange<Double>) {
    SPEED("km/h", 0.0..240.0), BATTERY("%", 0.0..100.0), CABIN("°C", -40.0..85.0)
}

sealed interface VehicleEvent {
    data object Ready : VehicleEvent
    data class Reading(val metric: Metric, val value: Double?, val sequence: Long) : VehicleEvent
    data object Disconnected : VehicleEvent
}

enum class Phase { STOPPED, CONNECTING, LIVE, RETRYING, UNAVAILABLE }
data class Sample(val value: Double?, val sequence: Long, val receivedAtMs: Long, val stale: Boolean = false)
data class SessionState(
    val phase: Phase = Phase.STOPPED,
    val attempt: Int = 0,
    val samples: Map<Metric, Sample> = emptyMap(),
    val rejected: Int = 0,
    val reason: String? = null,
)

/**
 * start/stop belong to one owner dispatcher. Gateway callbacks may use any thread.
 * nowMs must be monotonic and use the same lifetime as this session.
 */
class VehicleSession(
    private val scope: CoroutineScope,
    private val gateway: VehicleGateway,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
    private val connectTimeoutMs: Long = 1_000,
    private val staleAfterMs: Long = 2_000,
    private val retryDelayMs: Long = 250,
    private val maxReconnects: Int = 2,
) : AutoCloseable {
    init {
        require(connectTimeoutMs > 0 && staleAfterMs >= 2 && retryDelayMs >= 0)
        require(maxReconnects in 0..10)
    }

    private val mutableState = MutableStateFlow(SessionState())
    val state = mutableState.asStateFlow()
    private var epoch = 0L
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        val token = ++epoch
        job = scope.launch {
            for (attempt in 1..maxReconnects + 1) {
                publish(token, SessionState(Phase.CONNECTING, attempt))
                try {
                    observe(token, attempt)
                } catch (deadline: TimeoutCancellationException) {
                    // This session owns its connection deadline, but never its parent's cancellation.
                    if (!currentCoroutineContext().isActive) publish(token, SessionState())
                    currentCoroutineContext().ensureActive()
                    recover(token, attempt, "connection deadline")
                } catch (cancelled: CancellationException) {
                    publish(token, SessionState())
                    throw cancelled
                } catch (failure: Exception) {
                    if (!currentCoroutineContext().isActive) publish(token, SessionState())
                    currentCoroutineContext().ensureActive()
                    recover(token, attempt, if (failure is SessionFailure) failure.message!! else "gateway failure")
                }
                if (attempt <= maxReconnects) delay(retryDelayMs)
            }
        }
    }

    fun stop() {
        ++epoch
        job?.cancel()
        job = null
        mutableState.value = SessionState()
    }

    override fun close() = stop()

    private fun publish(token: Long, state: SessionState) {
        if (token == epoch) mutableState.value = state
    }

    private fun recover(token: Long, attempt: Int, reason: String) {
        publish(token, SessionState(
            phase = if (attempt <= maxReconnects) Phase.RETRYING else Phase.UNAVAILABLE,
            attempt = attempt,
            reason = reason,
        ))
    }

    private suspend fun observe(token: Long, attempt: Int) {
        val events = Channel<VehicleEvent>(64)
        val overflow = AtomicBoolean(false)
        var lease: AutoCloseable? = null
        try {
            lease = gateway.open { event ->
                val result = events.trySend(event)
                if (result.isFailure && !result.isClosed) overflow.set(true)
            }
            withTimeout(connectTimeoutMs) {
                if (events.receive() != VehicleEvent.Ready) throw SessionFailure("expected ready")
            }
            var current = SessionState(Phase.LIVE, attempt)
            publish(token, current)
            while (currentCoroutineContext().isActive) {
                if (overflow.get()) throw SessionFailure("event queue overflow")
                val event = withTimeoutOrNull(maxOf(1, staleAfterMs / 2)) { events.receive() }
                val now = nowMs()
                current = when (event) {
                    VehicleEvent.Disconnected -> throw SessionFailure("disconnected")
                    VehicleEvent.Ready -> current.copy(rejected = current.rejected + 1)
                    is VehicleEvent.Reading -> accept(current, event, now)
                    null -> current
                }
                current = current.copy(samples = current.samples.mapValues { (_, sample) ->
                    sample.copy(stale = sample.value != null && now - sample.receivedAtMs >= staleAfterMs)
                })
                publish(token, current)
            }
        } finally {
            // Closing first also rejects synchronous callbacks during lease cleanup.
            events.close()
            lease?.close()
        }
    }

    private fun accept(current: SessionState, reading: VehicleEvent.Reading, now: Long): SessionState {
        val previous = current.samples[reading.metric]
        if (reading.sequence < 0 || (previous != null && reading.sequence <= previous.sequence) ||
            (reading.value != null && (!reading.value.isFinite() || reading.value !in reading.metric.range))) {
            return current.copy(rejected = current.rejected + 1)
        }
        val sample = Sample(reading.value, reading.sequence, now)
        return current.copy(samples = current.samples + (reading.metric to sample))
    }

    private class SessionFailure(reason: String) : Exception(reason)
}
