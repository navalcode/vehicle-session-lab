package dev.navalcode.vehicle

data class DemoOptions(val language: String = "en", val help: Boolean = false)

fun parseOptions(args: Array<String>): DemoOptions {
    var language = "en"
    var languageSeen = false
    var help = false
    var index = 0
    while (index < args.size) {
        when (args[index++]) {
            "--help", "-h" -> help = true
            "--language" -> {
                require(!languageSeen && index < args.size) { "Use --language en|es once." }
                language = args[index++]
                require(language in setOf("en", "es")) { "Use --language en|es." }
                languageSeen = true
            }
            else -> throw IllegalArgumentException("Unknown option. Use --help.")
        }
    }
    return DemoOptions(language, help)
}

fun renderSnapshot(state: SessionState, language: String): String {
    val es = language == "es"
    val labels = if (es) listOf("Velocidad", "Batería", "Cabina") else listOf("Speed", "Battery", "Cabin")
    val lines = mutableListOf(
        "${if (es) "Estado" else "State"}: ${state.phase} | ${if (es) "Intento" else "Attempt"}: ${state.attempt} | " +
            "${if (es) "Rechazadas" else "Rejected"}: ${state.rejected}"
    )
    Metric.entries.forEachIndexed { index, metric ->
        val sample = state.samples[metric]
        val value = when {
            sample == null -> if (es) "Sin lectura" else "No reading"
            sample.value == null -> if (es) "No disponible" else "Unavailable"
            else -> "${sample.value} ${metric.unit} · " +
                if (sample.stale) (if (es) "obsoleta" else "stale") else (if (es) "vigente" else "current")
        }
        lines += "  ${labels[index]}: $value"
    }
    state.reason?.let { lines += "  ${if (es) "Motivo" else "Reason"}: $it" }
    return lines.joinToString("\n")
}
