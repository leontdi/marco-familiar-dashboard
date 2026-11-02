package com.lito.planify.dashboard.data

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.util.Properties

/**
 * Horas en las que se puede cargar el coche (p. ej. por la potencia contratada): de lunes a viernes [weekdays] y el fin
 * de semana [weekend], horas de inicio de 0 a 23. «Mejores horas» solo recomienda cargar dentro de ellas.
 * De `car.charge.weekdays` y `car.charge.weekend` en dashboard.properties («desde-hasta», p. ej. `0-8` o `0-24`).
 */
data class ChargeHours(val weekdays: IntRange, val weekend: IntRange) {
    fun allows(time: LocalDateTime): Boolean =
        time.hour in if (time.dayOfWeek == DayOfWeek.SATURDAY || time.dayOfWeek == DayOfWeek.SUNDAY) weekend else weekdays

    /** «L–V 0–8 h · S–D todo el día», para explicar en pantalla por qué no hay recomendación. */
    fun label(): String = "L–V ${label(weekdays)} · S–D ${label(weekend)}"

    companion object {
        private val ALL_DAY = 0 until 24

        /** null si no hay ninguna de las dos líneas: se puede cargar a cualquier hora. La que falte, todo el día. */
        fun from(props: Properties): ChargeHours? {
            val weekdays = props.getProperty("car.charge.weekdays")?.let(::parse)
            val weekend = props.getProperty("car.charge.weekend")?.let(::parse)
            if (weekdays == null && weekend == null) return null
            return ChargeHours(weekdays ?: ALL_DAY, weekend ?: ALL_DAY)
        }

        /** «0-8» → 0..7 (de 0:00 a 8:00); «0-0» → nunca; mal escrito → null (se ignora). */
        internal fun parse(text: String): IntRange? {
            val parts = text.split('-').map { it.trim().toIntOrNull() }
            val (from, to) = parts.takeIf { it.size == 2 }?.let { it[0] to it[1] } ?: return null
            if (from == null || to == null || from !in 0..24 || to !in 0..24 || from > to) return null
            return from until to
        }

        private fun label(hours: IntRange): String = when {
            hours.isEmpty() -> "nunca"
            hours == ALL_DAY -> "todo el día"
            else -> "${hours.first}–${hours.last + 1} h"
        }
    }
}
