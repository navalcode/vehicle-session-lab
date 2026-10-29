package dev.navalcode.vehicle

import org.junit.Assert.*
import org.junit.Test

class PresentationTest {
    @Test fun distinguishesMissingUnavailableStaleAndCurrentInBothLanguages() {
        val state = SessionState(Phase.LIVE, 2, mapOf(
            Metric.BATTERY to Sample(72.0, 1, 0, stale = true),
            Metric.CABIN to Sample(null, 2, 0),
        ), rejected = 1)
        for ((language, missing, stale, unavailable) in listOf(
            listOf("en", "No reading", "stale", "Unavailable"),
            listOf("es", "Sin lectura", "obsoleta", "No disponible"),
        )) {
            val output = renderSnapshot(state, language)
            assertTrue(output.contains(missing)); assertTrue(output.contains("72.0 % · $stale"))
            assertTrue(output.contains(unavailable)); assertFalse(output.contains("null"))
            assertTrue(output.contains("LIVE")); assertTrue(output.contains(": 2"))
        }
        val recovered = state.copy(samples = mapOf(Metric.CABIN to Sample(21.0, 3, 100)))
        assertTrue(renderSnapshot(recovered, "en").contains("21.0 °C · current"))
        assertTrue(renderSnapshot(recovered, "es").contains("21.0 °C · vigente"))
    }

    @Test fun optionsSupportHelpAndLanguageWithoutChangingTheDefault() {
        assertEquals(DemoOptions(), parseOptions(emptyArray()))
        assertEquals(DemoOptions("es"), parseOptions(arrayOf("--language", "es")))
        assertTrue(parseOptions(arrayOf("--help")).help)
    }

    @Test fun invalidOptionsAreRejectedInsteadOfStartingWithAnUnintendedConfiguration() {
        for (args in listOf(arrayOf("--language"), arrayOf("--language", "fr"), arrayOf("--speed", "100"),
            arrayOf("--language", "es", "--language", "en"))) {
            assertThrows(IllegalArgumentException::class.java) { parseOptions(args) }
        }
    }
}
