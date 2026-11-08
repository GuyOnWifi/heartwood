package dev.easonhuang.heartwood.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class UnitsTest {

    @Test
    fun imperialConvertsTheMetricsThatHaveImperialUnits() {
        assertEquals(3.107f, Metric.DISTANCE.fromBase(5f, UnitSystem.IMPERIAL), 0.001f)
        assertEquals(67.63f, Metric.HYDRATION.fromBase(2f, UnitSystem.IMPERIAL), 0.01f)
        assertEquals(154.32f, Metric.WEIGHT.fromBase(70f, UnitSystem.IMPERIAL), 0.01f)
        assertEquals(70.87f, Metric.HEIGHT.fromBase(180f, UnitSystem.IMPERIAL), 0.01f)
    }

    @Test
    fun everythingElseIsUntouched() {
        Metric.entries
            .filter { it !in setOf(Metric.DISTANCE, Metric.HYDRATION, Metric.WEIGHT, Metric.HEIGHT) }
            .forEach { metric ->
                assertEquals(metric.name, 123.4f, metric.fromBase(123.4f, UnitSystem.IMPERIAL))
                assertEquals(metric.name, metric.unitRes, metric.unitRes(UnitSystem.IMPERIAL))
            }
    }

    @Test
    fun toBaseInvertsFromBase() {
        Metric.entries.forEach { metric ->
            val shown = metric.fromBase(42f, UnitSystem.IMPERIAL)
            assertEquals(metric.name, 42f, metric.toBase(shown, UnitSystem.IMPERIAL), 0.0001f)
        }
    }

    @Test
    fun metricIsTheIdentity() {
        Metric.entries.forEach { metric ->
            assertEquals(7.5f, metric.fromBase(7.5f, UnitSystem.METRIC))
            assertEquals(metric.unitRes, metric.unitRes(UnitSystem.METRIC))
        }
    }

    @Test
    fun defaultFollowsTheLocalesCountry() {
        assertEquals(UnitSystem.IMPERIAL, UnitSystem.defaultFor(Locale.US))
        assertEquals(UnitSystem.METRIC, UnitSystem.defaultFor(Locale.UK))
        assertEquals(UnitSystem.METRIC, UnitSystem.defaultFor(Locale.CANADA))
        assertEquals(UnitSystem.METRIC, UnitSystem.defaultFor(Locale.ENGLISH))
    }

    @Test
    fun imperialHydrationIsWholeOunces() {
        assertEquals("68", Metric.HYDRATION.formatValue(67.63f, UnitSystem.IMPERIAL))
        assertEquals("2.00", Metric.HYDRATION.formatValue(2f, UnitSystem.METRIC))
    }
}
