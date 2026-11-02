package com.lito.planify.dashboard.data

import com.lito.planify.dashboard.ui.cheapestWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.util.Properties

class ChargeHoursTest {
    private val hours = ChargeHours(0 until 8, 0 until 24)

    // 2026-11-02 es lunes; el 7, sábado
    private fun at(day: Int, hour: Int) = LocalDateTime.of(2026, 11, day, hour, 0)

    @Test
    fun weekdaysOnlyAtNightWeekendAllDay() {
        assertTrue(hours.allows(at(2, 0)))
        assertTrue(hours.allows(at(2, 7)))
        assertFalse(hours.allows(at(2, 8)))
        assertFalse(hours.allows(at(6, 23)))  // viernes por la noche
        assertTrue(hours.allows(at(7, 12)))   // sábado
        assertTrue(hours.allows(at(8, 23)))   // domingo
    }

    @Test
    fun readsPropertiesAndIgnoresTypos() {
        val props = Properties().apply { setProperty("car.charge.weekdays", "0-8"); setProperty("car.charge.weekend", "0-24") }
        assertEquals(hours, ChargeHours.from(props))
        assertNull(ChargeHours.from(Properties()))
        assertNull(ChargeHours.parse("8"))
        assertNull(ChargeHours.parse("9-3"))
        assertEquals(0 until 0, ChargeHours.parse("0-0"))
        assertEquals("L–V 0–8 h · S–D todo el día", hours.label())
    }

    /** Lunes y martes: el mediodía es lo más barato, pero el coche solo puede cargar de 0 a 8. */
    private val prices = (2..3).flatMap { day ->
        (0..23).map { h -> HourPrice(at(day, h).format(SQL_DATE_TIME), if (h in 10..15) 0.01 else 0.10 + h / 1000.0) }
    }

    @Test
    fun carWindowStaysInsideChargeHours() {
        val now = at(2, 9)
        val until = at(4, 0)
        val free = cheapestWindow(prices, now, 4, until)!!
        assertEquals(at(2, 10), free.first)
        val car = cheapestWindow(prices, now, 4, until, hours)!!
        assertEquals(at(3, 0), car.first)
        assertEquals(at(3, 4), car.second)
    }

    @Test
    fun noCarWindowWhenItsHoursHaveNoPriceYet() {
        val today = prices.filter { it.time.dayOfMonth == 2 }
        assertNull(cheapestWindow(today, at(2, 9), 4, at(4, 0), hours))
    }
}
