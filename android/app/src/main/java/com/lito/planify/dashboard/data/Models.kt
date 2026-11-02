package com.lito.planify.dashboard.data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// --- Planify (solo lo que lee el dashboard) ---

data class AuthRequest(val email: String, val password: String)

data class UserResponse(val id: Int, val name: String, val email: String, val token: String?)

data class Person(val id: Int, val name: String, val color: String)

/**
 * Evento o sesión de rutina (`routine_id` no nulo). `date_at` es hora local flotante. Los de los calendarios iCal
 * pueden ser de día completo (`all_day`, a las 00:00) y traen su fin (`end_at`); los de Planify, no.
 */
data class Event(
    val id: Int,
    val calendar_id: Int,
    val calendar_name: String?,
    val calendar_color: String?,
    val title: String,
    val date_at: String,
    val routine_id: Int? = null,
    val persons: List<Person>? = null,
    val all_day: Boolean = false,
    val end_at: String? = null,
    /** Lugar y descripción (solo los de iCal); se ven al tocar la cita. */
    val location: String? = null,
    val description: String? = null
) {
    val at: LocalDateTime get() = LocalDateTime.parse(date_at, SQL_DATE_TIME)
    val end: LocalDateTime? get() = end_at?.let { LocalDateTime.parse(it, SQL_DATE_TIME) }

    /** Ya terminado: los de día completo, al acabar su día; los de Planify (sin fin), al empezar. */
    fun isPast(now: LocalDateTime): Boolean = if (all_day) !(end ?: at.plusDays(1)).isAfter(now) else at.isBefore(now)
}

data class Task(
    val id: Int,
    val task_list_id: Int,
    val task_list_name: String?,
    val task_list_color: String?,
    val title: String,
    val order: Int,
    val completed_at: String?
)

/**
 * Contenido de Hoy (ver ContentRepository). `menu`: el menú del cole, solo con calendarios iCal (anulable: Gson lo
 * deja a null en las copias de disco anteriores).
 */
data class Content(
    val events: List<Event>,
    val sessions: List<Event>,
    val tasks: List<Task>,
    val menu: List<Event>? = null
)

/** Una semana del calendario (lunes a domingo), descargada al navegar hasta ella. */
data class Week(val events: List<Event>, val sessions: List<Event>)

val SQL_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

// --- Precio de la luz (PVPC, API pública de Red Eléctrica) ---

data class ReeResponse(val included: List<ReeSeries>? = null)
data class ReeSeries(val type: String, val attributes: ReeAttributes)
data class ReeAttributes(val values: List<ReeValue>)
/** `value` en €/MWh; `datetime` con zona («2026-10-20T00:00:00.000+02:00»). */
data class ReeValue(val value: Double, val datetime: String)

/** Precio de una hora: `at` en hora local del marco (formato SQL), `eur_kwh` en €/kWh. */
data class HourPrice(val at: String, val eur_kwh: Double) {
    val time: LocalDateTime get() = LocalDateTime.parse(at, SQL_DATE_TIME)
}

/** Precios por horas de hoy y, desde que REE los publica (sobre las 20:30), de mañana. */
data class Energy(val prices: List<HourPrice>)

// --- Lista de la compra (Bring! vía Home Assistant, compra.json en ruta secreta) ---

/** Un elemento de una lista. `vence`: fecha (AAAA-MM-DD, o con hora) de las tareas de Todoist; vacío si no tiene. */
data class ShoppingItem(val producto: String, val detalle: String? = null, val vence: String? = null) {
    val dueDate: java.time.LocalDate? get() = vence?.takeIf { it.length >= 10 }?.let { runCatching { java.time.LocalDate.parse(it.substring(0, 10)) }.getOrNull() }
}

data class FamilyList(val nombre: String, val color: String? = null, val items: List<ShoppingItem>? = null)

/**
 * El coche eléctrico (vía Home Assistant): batería en %, autonomía en km y estado de carga; cuentakilómetros, presión de las
 * ruedas en bar (delantera izq., delantera der., trasera izq., trasera der.) y si carga hasta el 100 %.
 */
data class CarStatus(
    val bateria: Double? = null,
    val autonomia_km: Double? = null,
    val cargando: Boolean = false,
    val enchufado: Boolean = false,
    val informado: String? = null,
    val km: Double? = null,
    val ruedas_bar: List<Double>? = null,
    val cargar_100: Boolean? = null,
    /** Potencia a la que está cargando, en kW (null si no carga o no se sabe). */
    val potencia_kw: Double? = null,
    /** Nombre que se enseña del coche; si no viene, «Coche eléctrico». */
    val modelo: String? = null
)

/** La casa (Home Assistant): termostato, temperatura de las estancias, toldo y la música de los Echo. */
data class HomeStatus(
    val clima: Climate? = null,
    val estancias: List<RoomTemp>? = null,
    val toldo: Awning? = null,
    val musica: Music? = null
)

/** Seguridad de la casa (compra.json «seguridad»), solo para mirar: el marco no abre ni desarma nada. */
data class Security(val cerradura: DoorLock? = null, val alarma: Alarm? = null)

/**
 * Cerradura: `estado` de HA (locked, unlocked, open, jammed, locking…; null si aún no ha informado), batería en %,
 * si está crítica o cargando y, con sensor de puerta, `puerta` «abierta» o «cerrada».
 */
data class DoorLock(
    val nombre: String? = null,
    val estado: String? = null,
    val bateria: Double? = null,
    val bateria_critica: Boolean? = null,
    val cargando: Boolean? = null,
    val puerta: String? = null
)

/** Alarma: `estado` de HA (disarmed, armed_home, armed_away, armed_night, arming, pending, triggered…). */
data class Alarm(val nombre: String? = null, val estado: String? = null)

/** Termostato (Somfy u otro, vía HA): `modo` home/away/night/freeze/none (preset de HA); `pila` en %. */
data class Climate(
    val nombre: String? = null,
    val temp: Double? = null,
    val consigna: Double? = null,
    val modo: String? = null,
    val estado: String? = null,
    val humedad: Double? = null,
    val pila: Double? = null
)

data class RoomTemp(val nombre: String, val temp: Double)

/** Toldo: `sacado` en % (0 = recogido). */
data class Awning(val nombre: String? = null, val sacado: Int? = null, val disponible: Boolean = true)

/** Lo que suena (o lo último pausado) en un Echo. */
data class Music(
    val altavoz: String? = null,
    val entidad: String? = null,
    val sonando: Boolean = false,
    val titulo: String? = null,
    val artista: String? = null,
    val volumen: Double? = null
)

/**
 * Listas de la familia (compra.json). `listas`: todas (compra de Bring! y tareas de Todoist «Casa»); los ficheros
 * antiguos solo traen `items` (la compra).
 */
data class ShoppingList(
    val actualizado: String? = null,
    val items: List<ShoppingItem>? = null,
    val listas: List<FamilyList>? = null,
    val coche: CarStatus? = null,
    val casa: HomeStatus? = null,
    /** Los 3 productos más pedidos («habituales») y si ya están en la lista. */
    val frecuentes: List<Frequent>? = null,
    /** Cerradura y alarma, solo lectura; null si no están configuradas. */
    val seguridad: Security? = null
) {
    fun all(): List<FamilyList> = listas ?: listOf(FamilyList(SHOPPING_NAME, SHOPPING_COLOR, items))
    fun shopping(): List<ShoppingItem> = all().firstOrNull { it.nombre == SHOPPING_NAME }?.items.orEmpty()

    /**
     * Con lo tocado en el marco hasta que el servidor publique la lista nueva (hasta un minuto): sin los productos de
     * [hidden] (quitados) y con los de [added] (habituales añadidos), que ya salen «en la lista».
     */
    fun adjusted(hidden: Set<String>, added: Set<String>): ShoppingList {
        val base = without(hidden)
        if (added.isEmpty()) return base
        val present = base.shopping().map { it.producto.lowercase() }.toSet()
        val extra = added.filter { it !in present }.map { name -> ShoppingItem(base.frecuentes?.firstOrNull { it.producto.lowercase() == name }?.producto ?: name) }
        return base.copy(
            items = base.items?.plus(extra),
            listas = base.listas?.map { l -> if (l.nombre == SHOPPING_NAME) l.copy(items = l.items.orEmpty() + extra) else l },
            frecuentes = base.frecuentes?.map { if (it.producto.lowercase() in added) it.copy(en_lista = true) else it }
        )
    }

    /** Sin los productos de [hidden] en la compra: los quitados en el marco hasta que el servidor publique la lista nueva. */
    fun without(hidden: Set<String>): ShoppingList {
        if (hidden.isEmpty()) return this
        fun keep(i: ShoppingItem) = i.producto.lowercase() !in hidden
        return copy(
            items = items?.filter(::keep),
            listas = listas?.map { l -> if (l.nombre == SHOPPING_NAME) l.copy(items = l.items?.filter(::keep)) else l }
        )
    }

    companion object {
        const val SHOPPING_NAME = "Lista de la compra"
        const val SHOPPING_COLOR = "#2E9E5B"
    }
}

/**
 * Tareas de casa de los niños de hoy (TaskMate en Home Assistant, a través de familia-tareas en el servidor).
 * `para`: niños que aún pueden hacerla (vacío si ya está hecha y era de «el primero que la haga»).
 */
data class Chores(
    val fecha: String? = null,
    val ninos: List<Kid>? = null,
    val tareas: List<Chore>? = null,
    val recompensas: List<Reward>? = null,
    /** Canjes pedidos por los niños que esperan a que un padre los apruebe. */
    val pedidas: List<RewardClaim>? = null
) {
    /** Tareas que alguien todavía tiene por hacer hoy. */
    fun pending(): Int = tareas.orEmpty().count { !it.para.isNullOrEmpty() }
}

/** Un niño: `estrellas` acumuladas, las ganadas esta semana y las que puede gastar (sin las de canjes pendientes). */
data class Kid(
    val id: String,
    val nombre: String,
    val color: String? = null,
    val estrellas: Int = 0,
    val semana: Int = 0,
    val disponibles: Int = 0
)

/** Recompensa de TaskMate: `costes` por niño (si TaskMate lo ajusta), `para` a quién se ofrece. */
data class Reward(
    val id: String,
    val nombre: String,
    val emoji: String? = null,
    val coste: Int = 0,
    val costes: Map<String, Int>? = null,
    val para: List<String>? = null,
    val disponible: Boolean = true
) {
    fun costFor(kid: String): Int = costes?.get(kid) ?: coste
}

/** Un canje pendiente: qué pidió quién y cuándo («20/10 18:30»). */
data class RewardClaim(
    val id: String,
    val recompensa: String,
    val nino: String,
    val nombre: String? = null,
    val titulo: String? = null,
    val emoji: String? = null,
    val coste: Int = 0,
    val cuando: String? = null
)

data class RewardClaimRequest(val recompensa: String, val nino: String)

data class ShoppingRemoveRequest(val producto: String)

/** Un habitual de la compra: cuántos días se ha pedido y si ya está en la lista. */
data class Frequent(val producto: String, val veces: Int = 0, val en_lista: Boolean = false)

/** Aprobar o rechazar un canje: el PIN de padres lo comprueba el servidor. */
data class RewardDecisionRequest(val pedida: String, val pin: String)

data class Chore(
    val id: String,
    val nombre: String,
    val emoji: String? = null,
    val estrellas: Int = 0,
    val para: List<String>? = null,
    val hechas: List<ChoreDone>? = null
)

/** Quién la hizo y a qué hora («17:48»). */
data class ChoreDone(val id: String, val nino: String, val nombre: String? = null, val hora: String? = null)

data class ChoreDoneRequest(val tarea: String, val nino: String)

data class ChoreUndoRequest(val hecha: String)

// --- Open-Meteo ---

data class Location(val name: String, val detail: String, val latitude: Double, val longitude: Double)

data class GeoResponse(val results: List<GeoResult>? = null)

data class GeoResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null
)

data class Forecast(
    val current: Current,
    val hourly: Hourly,
    val daily: Daily
)

/**
 * La máxima y la mínima de cada día, sacadas de sus horas. Open-Meteo calcula las diarias con más detalle que la
 * hora en punto (cada 15 minutos los primeros días, y a partir del octavo con otro modelo), así que no coincidían
 * con la gráfica por horas: 28,5 en la gráfica y 28,6 en el resumen (28° y 29° una vez redondeados), o hasta 2–3°
 * de diferencia la segunda semana. Se hace una vez al recibir la previsión, antes de guardarla.
 * Los días sin todas sus horas (23 el día del cambio de hora) se quedan con los valores de Open-Meteo.
 */
fun Forecast.withDailyFromHourly(): Forecast {
    val max = HashMap<String, Double>()
    val min = HashMap<String, Double>()
    val hours = HashMap<String, Int>()
    hourly.time.forEachIndexed { i, time ->
        val t = hourly.temperature_2m.getOrNull(i) ?: return@forEachIndexed
        val date = time.substring(0, 10)
        max[date] = maxOf(max[date] ?: t, t)
        min[date] = minOf(min[date] ?: t, t)
        hours[date] = (hours[date] ?: 0) + 1
    }
    fun complete(date: String) = (hours[date] ?: 0) >= 23
    return copy(
        daily = daily.copy(
            temperature_2m_max = daily.time.mapIndexed { i, date -> if (complete(date)) max[date] else daily.temperature_2m_max.getOrNull(i) },
            temperature_2m_min = daily.time.mapIndexed { i, date -> if (complete(date)) min[date] else daily.temperature_2m_min.getOrNull(i) }
        )
    )
}

data class Current(
    val time: String,
    val temperature_2m: Double,
    val relative_humidity_2m: Int?,
    val weather_code: Int,
    val wind_speed_10m: Double,
    val wind_direction_10m: Double,
    val is_day: Int
)

data class Hourly(
    val time: List<String>,
    val temperature_2m: List<Double?>,
    val precipitation_probability: List<Int?>,
    val precipitation: List<Double?>,
    val weather_code: List<Int?>,
    val is_day: List<Int?>
)

data class Daily(
    val time: List<String>,
    val weather_code: List<Int?>,
    val temperature_2m_max: List<Double?>,
    val temperature_2m_min: List<Double?>,
    val precipitation_probability_max: List<Int?>,
    val precipitation_sum: List<Double?>,
    val wind_speed_10m_max: List<Double?>,
    val sunrise: List<String>,
    val sunset: List<String>
)
