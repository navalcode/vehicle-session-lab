package dev.navalcode.vehicle

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val options = try {
        parseOptions(args)
    } catch (invalid: IllegalArgumentException) {
        System.err.println(invalid.message)
        exitProcess(2)
    }
    if (options.help) {
        println("Vehicle Session Lab\nUsage: ./gradlew -q run --args='--language en|es'\n--help  Show help without running the simulation.")
        return
    }
    runDemo(options.language)
}

fun runDemo(language: String) = runBlocking {
    val es = language == "es"
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
            println(renderSnapshot(state, language))
            println()
        }
    }
    println(if (es) "DATOS FICTICIOS · simulación de solo lectura · sin conexión a un vehículo" else "FICTIONAL DATA · read-only simulation · no vehicle connection")
    println(if (es) "Snapshots de StateFlow; no son un registro completo de eventos.\n" else "StateFlow snapshots; not a complete event journal.\n")
    session.start()
    delay(1_800)
    session.stop()
    yield()
    observation.cancelAndJoin()
    println(if (es) "Suscripciones: abiertas=$opened cerradas=$closed" else "Subscriptions: opened=$opened closed=$closed")
    check(opened == closed)
}
