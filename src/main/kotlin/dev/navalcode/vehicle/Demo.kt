package dev.navalcode.vehicle

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

fun main() = runBlocking {
    var opened = 0
    var closed = 0
    val gateway = VehicleGateway { callback ->
        val connection = ++opened
        val producer = launch {
            callback(VehicleEvent.Ready)
            callback(VehicleEvent.Reading(Metric.BATTERY, 72.0, 1))
            callback(VehicleEvent.Reading(Metric.CABIN, 21.0, 1))
            delay(200)
            callback(VehicleEvent.Reading(Metric.SPEED, 0.0, 1))
            callback(VehicleEvent.Reading(Metric.SPEED, 999.0, 2))
            delay(200)
            callback(VehicleEvent.Reading(Metric.CABIN, null, 2))
            if (connection == 1) callback(VehicleEvent.Disconnected)
        }
        AutoCloseable { producer.cancel(); closed++ }
    }
    val session = VehicleSession(this, gateway, staleAfterMs = 600)
    val observation = launch {
        session.state.collect { state ->
            val values = state.samples.entries.joinToString { (metric, sample) ->
                "${metric.name}=${sample.value ?: "unavailable"} ${metric.unit}${if (sample.stale) " (stale)" else ""}"
            }
            println("${state.phase} attempt=${state.attempt} rejected=${state.rejected} $values ${state.reason.orEmpty()}")
        }
    }
    println("FICTIONAL DATA · read-only simulation · no vehicle connection")
    session.start()
    delay(1_800)
    session.stop()
    yield()
    observation.cancelAndJoin()
    println("Resources: opened=$opened closed=$closed")
    check(opened == closed)
}
