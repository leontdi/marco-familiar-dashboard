package com.lito.planify.dashboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class SecurityStatusTest {
    private val day = LocalDateTime.of(2026, 11, 2, 12, 0)
    private val night = LocalDateTime.of(2026, 11, 2, 23, 30)
    private fun lock(estado: String?, bateria: Double? = 70.0, critica: Boolean? = false, cargando: Boolean? = false, puerta: String? = null) =
        Security(DoorLock("Puerta de entrada", estado, bateria, critica, cargando, puerta))

    @Test
    fun noDataNoAlarm() {
        // Tal como está ahora: montada a final de mes, sin estado todavía
        assertNull(lock(null).alert(night))
        assertNull(Security().alert(night))
        assertEquals("Sin datos todavía", DoorLock().stateText())
    }

    @Test
    fun unlockedOnlyWarnsAtNight() {
        assertNull(lock("unlocked").alert(day))
        assertEquals("🔓 Puerta de entrada sin cerrar", lock("unlocked").alert(night))
        assertNull(lock("locked").alert(night))
        assertEquals("🚪 Puerta de entrada abierta", lock("locked", puerta = "abierta").alert(night))
    }

    @Test
    fun jammedAndLowBattery() {
        assertEquals("⚠️ Puerta de entrada: cerradura atascada", lock("jammed").alert(day))
        assertEquals("🔋 Cerradura al 15 %", lock("locked", bateria = 15.0).alert(day))
        assertEquals("🔋 Cerradura al 40 %", lock("locked", bateria = 40.0, critica = true).alert(day))
        assertNull(lock("locked", bateria = 15.0, cargando = true).alert(day))
    }

    @Test
    fun triggeredAlarmComesFirst() {
        val s = Security(DoorLock("Puerta", "jammed"), Alarm("Alarma", "triggered"))
        assertEquals("🚨 ¡Alarma disparada!", s.alert(day))
        assertNull(Security(alarma = Alarm("Alarma", "armed_away")).alert(night))
        assertEquals("Conectada · fuera", Alarm(estado = "armed_away").stateText())
    }
}
