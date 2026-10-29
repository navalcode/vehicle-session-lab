@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.navalcode.vehicle

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class VehicleSessionTest {
    private class Gateway : VehicleGateway {
        val callbacks = mutableListOf<(VehicleEvent) -> Unit>()
        var closed = 0
        var closing: () -> Unit = {}
        var opening: ((VehicleEvent) -> Unit) -> Unit = {}
        override fun open(callback: (VehicleEvent) -> Unit): AutoCloseable {
            callbacks += callback
            opening(callback)
            return AutoCloseable { closed++; closing() }
        }
        fun send(event: VehicleEvent, index: Int = callbacks.lastIndex) = callbacks[index](event)
    }
    private fun TestScope.session(gateway: Gateway, retries: Int = 2) = VehicleSession(
        backgroundScope, gateway, nowMs = { testScheduler.currentTime },
        connectTimeoutMs = 100, staleAfterMs = 200, retryDelayMs = 20, maxReconnects = retries,
    )
    private fun reading(value: Double?, seq: Long = 1, metric: Metric = Metric.BATTERY) =
        VehicleEvent.Reading(metric, value, seq)

    @Test fun startIsIdempotentAndStopReleasesExactlyOnce() = runTest {
        val g = Gateway(); val s = session(g)
        s.start(); s.start(); runCurrent()
        assertEquals(1, g.callbacks.size)
        g.send(VehicleEvent.Ready); runCurrent(); assertEquals(Phase.LIVE, s.state.value.phase)
        s.stop(); s.stop(); runCurrent()
        assertEquals(1, g.closed); assertEquals(Phase.STOPPED, s.state.value.phase)
    }
    @Test fun synchronousCallbackDuringOpenIsObserved() = runTest {
        val g = Gateway().apply { opening = { it(VehicleEvent.Ready); it(reading(60.0)) } }
        val s = session(g); s.start(); runCurrent()
        assertEquals(60.0, s.state.value.samples[Metric.BATTERY]!!.value)
    }
    @Test fun connectionDeadlineClosesLeaseBeforeRetry() = runTest {
        val g = Gateway(); val s = session(g); s.start(); runCurrent()
        advanceTimeBy(100); runCurrent()
        assertEquals(1, g.closed); assertEquals(Phase.RETRYING, s.state.value.phase)
        advanceTimeBy(20); runCurrent(); assertEquals(2, g.callbacks.size)
    }
    @Test fun deadlineRetriesAreBoundedAndManualStartResetsBudget() = runTest {
        val g = Gateway(); val s = session(g); s.start(); runCurrent()
        advanceTimeBy(400); runCurrent()
        assertEquals(3, g.callbacks.size); assertEquals(3, g.closed)
        assertEquals(Phase.UNAVAILABLE, s.state.value.phase)
        s.start(); runCurrent(); assertEquals(4, g.callbacks.size)
        assertEquals(1, s.state.value.attempt)
    }
    @Test fun disconnectClearsLiveValuesAndNewSessionRecovers() = runTest {
        val g = Gateway(); val s = session(g); s.start(); runCurrent()
        g.send(VehicleEvent.Ready); g.send(reading(70.0)); runCurrent()
        g.send(VehicleEvent.Disconnected); runCurrent()
        assertTrue(s.state.value.samples.isEmpty()); assertEquals(1, g.closed)
        advanceTimeBy(20); runCurrent()
        g.send(VehicleEvent.Ready); g.send(reading(30.0)); runCurrent()
        assertEquals(30.0, s.state.value.samples[Metric.BATTERY]!!.value)
    }
    @Test fun oldCallbacksCannotWriteIntoReconnectedSession() = runTest {
        val g = Gateway(); val s = session(g); s.start(); runCurrent()
        g.send(VehicleEvent.Ready); g.send(VehicleEvent.Disconnected); runCurrent()
        advanceTimeBy(20); runCurrent(); g.send(VehicleEvent.Ready); g.send(reading(30.0)); runCurrent()
        g.send(reading(99.0, 100), index = 0); g.send(VehicleEvent.Disconnected, index = 0); runCurrent()
        assertEquals(Phase.LIVE, s.state.value.phase)
        assertEquals(30.0, s.state.value.samples[Metric.BATTERY]!!.value)
    }
    @Test fun stopIgnoresQueuedAndLateCallbacks() = runTest {
        val g = Gateway(); val s = session(g); s.start(); runCurrent()
        g.send(VehicleEvent.Ready); g.send(reading(60.0)); s.stop(); runCurrent()
        g.send(reading(70.0)); runCurrent()
        assertEquals(SessionState(), s.state.value); assertEquals(1, g.closed)
    }
    @Test fun parentCancellationClosesWithoutRetry() = runTest {
        val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val g = Gateway(); val s = VehicleSession(owner,g)
        s.start(); runCurrent(); g.send(VehicleEvent.Ready); g.send(reading(60.0)); runCurrent()
        owner.cancel(); runCurrent()
        advanceTimeBy(5_000); runCurrent()
        assertEquals(1,g.callbacks.size); assertEquals(1,g.closed)
        assertEquals(Phase.STOPPED,s.state.value.phase)
        s.close()
    }
    @Test fun parentDeadlineIsNotTreatedAsSourceTimeoutOrRetried() = runTest {
        val g = Gateway(); lateinit var s: VehicleSession
        val result = runCatching {
            withTimeout(50) {
                s = VehicleSession(this,g,connectTimeoutMs=500)
                s.start()
                delay(1_000)
            }
        }
        assertTrue(result.exceptionOrNull() is TimeoutCancellationException)
        assertEquals(1,g.callbacks.size); assertEquals(1,g.closed)
        assertEquals(SessionState(),s.state.value)
    }
    @Test fun repeatedAndRegressiveSequencesAreRejectedPerMetric() = runTest {
        val g = Gateway(); val s = session(g); s.start(); runCurrent(); g.send(VehicleEvent.Ready)
        g.send(reading(50.0,5)); g.send(reading(90.0,5)); g.send(reading(80.0,4))
        g.send(reading(22.0,1,Metric.CABIN)); runCurrent()
        assertEquals(50.0,s.state.value.samples[Metric.BATTERY]!!.value)
        assertEquals(22.0,s.state.value.samples[Metric.CABIN]!!.value)
        assertEquals(2,s.state.value.rejected)
    }
    @Test fun nonFiniteOutOfRangeAndNegativeSequenceDoNotReplaceAcceptedValue() = runTest {
        val g = Gateway(); val s = session(g); s.start(); runCurrent(); g.send(VehicleEvent.Ready)
        g.send(reading(40.0)); g.send(reading(Double.NaN,2)); g.send(reading(Double.POSITIVE_INFINITY,2))
        g.send(reading(101.0,2)); g.send(reading(-1.0,2)); g.send(reading(50.0,-1)); runCurrent()
        assertEquals(40.0,s.state.value.samples[Metric.BATTERY]!!.value)
        assertEquals(5,s.state.value.rejected)
    }
    @Test fun unavailableValueIsExplicitAndCanRecover() = runTest {
        val g = Gateway(); val s = session(g); s.start(); runCurrent(); g.send(VehicleEvent.Ready)
        g.send(reading(40.0)); g.send(reading(null,2)); runCurrent()
        assertNull(s.state.value.samples[Metric.BATTERY]!!.value)
        g.send(reading(50.0,3)); runCurrent(); assertEquals(50.0,s.state.value.samples[Metric.BATTERY]!!.value)
    }
    @Test fun quietMetricBecomesStaleEvenWhileOtherMetricsContinue() = runTest {
        val g = Gateway(); val s = session(g); s.start(); runCurrent(); g.send(VehicleEvent.Ready)
        g.send(reading(50.0)); runCurrent()
        repeat(4) { advanceTimeBy(60); g.send(reading(20.0,it.toLong(),Metric.CABIN)); runCurrent() }
        assertTrue(s.state.value.samples[Metric.BATTERY]!!.stale)
        assertFalse(s.state.value.samples[Metric.CABIN]!!.stale)
        g.send(reading(55.0,2)); runCurrent(); assertFalse(s.state.value.samples[Metric.BATTERY]!!.stale)
    }
    @Test fun idleMetricBecomesStaleAndInvalidEventsDoNotRefreshIt() = runTest {
        val g = Gateway(); val s = session(g); s.start(); runCurrent(); g.send(VehicleEvent.Ready)
        g.send(reading(50.0)); runCurrent(); advanceTimeBy(200); runCurrent()
        assertTrue(s.state.value.samples[Metric.BATTERY]!!.stale)
        g.send(reading(200.0,2)); runCurrent(); assertTrue(s.state.value.samples[Metric.BATTERY]!!.stale)
    }
    @Test fun readingBeforeReadyFailsProtocolAndReleasesLease() = runTest {
        val g = Gateway(); val s = session(g,retries=0); s.start(); runCurrent()
        g.send(reading(50.0)); runCurrent()
        assertEquals(Phase.UNAVAILABLE,s.state.value.phase)
        assertEquals("expected ready",s.state.value.reason); assertEquals(1,g.closed)
    }
    @Test fun queueOverflowFailsExplicitlyInsteadOfSilentlyLosingReadings() = runTest {
        val g = Gateway().apply { opening = { callback ->
            callback(VehicleEvent.Ready)
            repeat(100) { callback(reading(30.0,it.toLong())) }
        } }
        val s = session(g,retries=0); s.start(); runCurrent()
        assertEquals("event queue overflow",s.state.value.reason)
        assertEquals(Phase.UNAVAILABLE,s.state.value.phase); assertEquals(1,g.closed)
    }
    @Test fun openingFailureUsesBoundedRetriesWithoutLeakingSensitiveExceptionText() = runTest {
        val g = Gateway().apply { opening = { error("private-path-or-message") } }
        val s = session(g,retries=1); s.start(); runCurrent(); advanceTimeBy(20); runCurrent()
        assertEquals(2,g.callbacks.size); assertEquals(Phase.UNAVAILABLE,s.state.value.phase)
        assertEquals("gateway failure",s.state.value.reason)
    }
    @Test fun stoppedSessionCanStartAgainWithoutOldCleanupChangingItsState() = runTest {
        val g = Gateway(); val s = session(g); s.start(); runCurrent()
        s.stop(); s.start(); runCurrent(); g.send(VehicleEvent.Ready); runCurrent()
        assertEquals(2,g.callbacks.size); assertEquals(1,g.closed); assertEquals(Phase.LIVE,s.state.value.phase)
    }
    @Test fun cleanupFailureStillUsesBoundedRecoveryAndClosesEachLeaseOnce() = runTest {
        val g = Gateway().apply { closing = { error("cleanup failure") } }
        val s = session(g,retries=0); s.start(); runCurrent(); g.send(VehicleEvent.Ready)
        g.send(VehicleEvent.Disconnected); runCurrent()
        assertEquals(1,g.closed); assertEquals("gateway failure",s.state.value.reason)
        assertEquals(Phase.UNAVAILABLE,s.state.value.phase)
    }
    @Test fun cleanupFailureCannotTurnOwnerCancellationIntoRetry() = runTest {
        val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val g = Gateway().apply { closing = { error("cleanup failure") } }
        val s = VehicleSession(owner,g); s.start(); runCurrent(); owner.cancel(); runCurrent()
        advanceTimeBy(5_000); runCurrent()
        assertEquals(1,g.callbacks.size); assertEquals(1,g.closed); assertEquals(SessionState(),s.state.value)
    }
}
