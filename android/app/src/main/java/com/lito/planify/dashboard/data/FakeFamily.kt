package com.lito.planify.dashboard.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Familia inventada para capturas que se puedan publicar (`demo.ficticio=true` en dashboard.properties, solo en el
 * emulador): sustituye las citas, los turnos, los menús y las listas por unos de ejemplo alrededor de hoy, para que no
 * se vea ningún horario, menú ni compra de verdad. El tiempo, el precio de la luz y los datos de la casa siguen reales.
 */
object FakeFamily {
    private const val FAMILY = "#E8604C"
    private const val SPORTS = "#2E9E5B"
    private const val PUBLIC = "#A50044"

    private val shiftCycle = listOf(
        Triple("Mañana", "#F49AC2", 7 to 15), Triple("Mañana", "#F49AC2", 7 to 15),
        Triple("Tarde", "#7BCDC8", 15 to 23), Triple("Tarde", "#7BCDC8", 15 to 23),
        Triple("Noche", "#8882BE", 23 to 7), Triple("Saliente", "#F26A2E", -1 to -1),
        Triple("Día libre", "#C5E1A5", -1 to -1), Triple("Día libre", "#C5E1A5", -1 to -1)
    )

    private val menus = listOf(
        "Crema de calabaza · Pollo asado con patatas",
        "Macarrones con tomate · Merluza al horno con ensalada",
        "Lentejas con verduras · Tortilla de patata",
        "Arroz a la cubana · Filete de pavo con zanahoria",
        "Sopa de fideos · Albóndigas con guisantes",
        "Garbanzos con espinacas · Salmón con arroz blanco",
        "Puré de verduras · Lomo a la plancha con champiñones",
        "Espaguetis boloñesa · Pescadilla rebozada",
        "Judías verdes · Hamburguesa de ternera con patatas",
        "Ensalada de pasta · Pollo al curry con arroz"
    )

    private var nextId = 900_000

    private fun event(day: LocalDate, title: String, color: String, calendar: String, at: Int, minutes: Int = 60, allDay: Boolean = false, shift: Boolean = false): Event {
        val start = if (allDay) day.atStartOfDay() else day.atTime(LocalTime.of(at / 100, at % 100))
        val end = if (allDay) day.plusDays(1).atStartOfDay() else start.plusMinutes(minutes.toLong())
        return Event(
            id = nextId++, calendar_id = calendar.hashCode(), calendar_name = calendar, calendar_color = color, title = title,
            date_at = start.format(SQL_DATE_TIME), routine_id = if (shift) 1 else null, all_day = allDay, end_at = end.format(SQL_DATE_TIME)
        )
    }

    fun apply(c: Content?, today: LocalDate): Content {
        val monday = today.with(DayOfWeek.MONDAY)
        val days = (0L until 35L).map { monday.plusDays(it) }
        val events = mutableListOf<Event>()
        days.forEachIndexed { i, d ->
            when (d.dayOfWeek) {
                DayOfWeek.MONDAY -> { events += event(d, "Natación Leo", SPORTS, "Actividades", 1730); events += event(d, "Inglés Hugo", FAMILY, "Familia", 1830) }
                DayOfWeek.TUESDAY -> events += event(d, "Baloncesto Hugo", SPORTS, "Actividades", 1700, 90)
                DayOfWeek.WEDNESDAY -> { events += event(d, "Natación Leo", SPORTS, "Actividades", 1730); events += event(d, "Reunión del cole", FAMILY, "Familia", 1900) }
                DayOfWeek.THURSDAY -> events += event(d, "Baloncesto Hugo", SPORTS, "Actividades", 1700, 90)
                DayOfWeek.FRIDAY -> events += event(d, "Música Leo", FAMILY, "Familia", 1800)
                DayOfWeek.SATURDAY -> {
                    events += event(d, if (i % 14 < 7) "🏀 Hugo vs Club Ejemplo" else "🏀 Hugo vs Escuela Norte", SPORTS, "Actividades", 1030, 90)
                    if (i % 14 >= 7) events += event(d, "Cumpleaños de Sara", FAMILY, "Familia", 1700, 120)
                }
                DayOfWeek.SUNDAY -> {
                    events += event(d, "Comida con los abuelos", FAMILY, "Familia", 1400, 180)
                    events += event(d, "⚽ Partido de liga", PUBLIC, "Fútbol", 2100, 120)
                }
                else -> {}
            }
            if (i == 9) events += event(d, "Dentista Leo", FAMILY, "Familia", 1615, 30)
            if (i == 12) events += event(d, "Excursión del cole", FAMILY, "Familia", 0, allDay = true)
        }
        val sessions = days.mapIndexed { i, d ->
            val (title, color, hours) = shiftCycle[i % shiftCycle.size]
            if (hours.first < 0) event(d, title, color, "Turnos Ana", 0, allDay = true, shift = true)
            else event(d, title, color, "Turnos Ana", hours.first * 100, ((hours.second - hours.first + 24) % 24) * 60, shift = true)
        }
        val menu = days.filter { it.dayOfWeek.value <= 5 }.mapIndexed { i, d ->
            val title = menus[i % menus.size] + if (i % 4 == 1) " (Hugo: Tortilla francesa)" else ""
            event(d, title, "#7CB342", "Menú cole", 0, allDay = true)
        }
        return Content(events = events.sortedBy { it.date_at }, sessions = sessions, tasks = c?.tasks.orEmpty(), menu = menu)
    }

    fun shopping(real: ShoppingList?): ShoppingList {
        val compra = listOf("Leche", "Pan de molde", "Plátanos", "Café", "Tomates").map { ShoppingItem(it) }
        val casa = listOf(
            ShoppingItem("Llamar al fontanero", vence = LocalDate.now().plusDays(1).toString()),
            ShoppingItem("Renovar el seguro del coche", vence = LocalDate.now().plusDays(9).toString()),
            ShoppingItem("Preparar la mochila de la excursión")
        )
        return ShoppingList(
            items = compra,
            listas = listOf(
                FamilyList(ShoppingList.SHOPPING_NAME, ShoppingList.SHOPPING_COLOR, compra),
                FamilyList("Tareas", "#3F7FD9", casa)
            ),
            coche = real?.coche,
            casa = real?.casa,
            seguridad = real?.seguridad,
            frecuentes = listOf(Frequent("Huevos", 6), Frequent("Yogures", 5), Frequent("Aceite de oliva", 4))
        )
    }
}
