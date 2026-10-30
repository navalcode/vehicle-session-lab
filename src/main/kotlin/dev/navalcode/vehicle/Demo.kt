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
        println(renderHelp(options.language))
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
    println(if (es) "Datos ficticios · simulación de solo lectura · sin conexión a un vehículo" else "Fictional data · read-only simulation · no vehicle connection")
    println(if (es) "StateFlow muestra el estado más reciente y puede omitir estados intermedios.\n" else "StateFlow shows the latest state and may omit intermediate states.\n")
    session.start()
    delay(1_800)
    session.stop()
    yield()
    observation.cancelAndJoin()
    println(if (es) "Suscripciones: abiertas=$opened cerradas=$closed" else "Subscriptions: opened=$opened closed=$closed")
    check(opened == closed)
}
