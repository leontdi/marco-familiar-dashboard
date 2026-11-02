package com.lito.planify.dashboard.data

import java.time.LocalDateTime

/** De noche (23:00–7:00) la puerta sin cerrar merece aviso; de día es lo normal con gente en casa. */
internal fun isNight(now: LocalDateTime) = now.hour >= 23 || now.hour < 7

/** La batería de la cerradura pide cambio o carga: crítica según la cerradura o al 20 % o menos. */
fun DoorLock.lowBattery(): Boolean = bateria_critica == true || (bateria != null && bateria <= LOW_BATTERY)

/** La cerradura sin echar: sin llave o con el pestillo abierto. */
fun DoorLock.isUnlocked(): Boolean = estado == "unlocked" || estado == "open"

fun DoorLock.stateText(): String = when (estado) {
    null -> "Sin datos todavía"
    "locked" -> "Cerrada con llave"
    "unlocked" -> "Sin echar la llave"
    "open" -> "Pestillo abierto"
    "opening" -> "Abriendo…"
    "locking" -> "Cerrando…"
    "unlocking" -> "Quitando la llave…"
    "jammed" -> "Atascada"
    else -> estado
}

fun Alarm.isArmed(): Boolean = estado?.startsWith("armed_") == true

fun Alarm.stateText(): String = when (estado) {
    null -> "Sin datos todavía"
    "disarmed" -> "Desconectada"
    "armed_home" -> "Conectada · en casa"
    "armed_away" -> "Conectada · fuera"
    "armed_night" -> "Conectada · noche"
    "armed_vacation" -> "Conectada · vacaciones"
    "armed_custom_bypass" -> "Conectada · parcial"
    "arming" -> "Conectándose…"
    "disarming" -> "Desconectándose…"
    "pending" -> "Entrada detectada…"
    "triggered" -> "¡Disparada!"
    else -> estado
}

/**
 * El aviso de la portada, el más grave primero: alarma disparada, cerradura atascada, puerta abierta o sin cerrar de
 * noche y batería baja (si no está cargando). null si todo va bien o aún no hay datos: sin datos no se alarma a nadie.
 */
fun Security.alert(now: LocalDateTime): String? {
    val lock = cerradura
    val name = lock?.nombre ?: "La puerta"
    return when {
        alarma?.estado == "triggered" -> "🚨 ¡Alarma disparada!"
        lock?.estado == "jammed" -> "⚠️ $name: cerradura atascada"
        lock != null && isNight(now) && lock.puerta == "abierta" -> "🚪 $name abierta"
        lock != null && isNight(now) && lock.isUnlocked() -> "🔓 $name sin cerrar"
        lock != null && lock.lowBattery() && lock.cargando != true ->
            "🔋 Cerradura al ${lock.bateria?.toInt()?.let { "$it %" } ?: "mínimo"}"
        else -> null
    }
}

private const val LOW_BATTERY = 20.0
