package com.lito.planify.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ElectricCar
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.planify.dashboard.UiState
import com.lito.planify.dashboard.data.CarStatus
import com.lito.planify.dashboard.data.ChargeHours
import com.lito.planify.dashboard.data.HourPrice
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Energía: a la izquierda el precio de la luz (PVPC) de hoy hora a hora, grande, con lo que cuesta ahora, la media y
 * las horas más barata y más cara, y debajo el de mañana desde que REE lo publica (≈20:30). A la derecha, las mejores
 * horas para cargar el coche y poner el lavavajillas o la lavadora, y el coche eléctrico (vía Home Assistant) con su batería y sus ruedas.
 */
@Composable
fun EnergyScreen(state: UiState) {
    val now = state.now
    val today = now.toLocalDate()
    val prices = state.energy?.prices.orEmpty()
    val day = prices.filter { it.time.toLocalDate() == today }
    val tomorrow = prices.filter { it.time.toLocalDate() == today.plusDays(1) }
    val car = state.shopping?.coche
    ScreenPadding {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.weight(1.55f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                PricePanel("Precio de la luz hoy", day, now, Modifier.fillMaxWidth().weight(if (tomorrow.isEmpty()) 1f else 0.62f), big = true)
                if (tomorrow.isNotEmpty()) PricePanel("Mañana", tomorrow, now, Modifier.fillMaxWidth().weight(0.38f), big = false)
                else Text(
                    "El precio de mañana lo publica REE sobre las 20:30",
                    style = Type.Small.copy(color = Palette.InkMuted),
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                BestHoursPanel(prices, now, Modifier.fillMaxWidth(), state.carCharge)
                car?.bateria?.let { CarPanel(car, Modifier.fillMaxWidth().weight(1f)) }
            }
        }
    }
}

@Composable
internal fun PricePanel(title: String, day: List<HourPrice>, now: LocalDateTime, modifier: Modifier, big: Boolean) {
    val min = day.minOfOrNull { it.eur_kwh } ?: 0.0
    val max = day.maxOfOrNull { it.eur_kwh } ?: 0.0
    val current = day.lastOrNull { !it.time.isAfter(now) && it.time.plusHours(1).isAfter(now) }
    Panel(modifier, compact = true) {
        CardHeader(title, Icons.Outlined.Bolt, Amber, compact = true)
        if (day.isEmpty()) {
            Text("Sin precios todavía", style = Type.Body.copy(color = Palette.InkMuted), modifier = Modifier.padding(top = 20.dp))
            return@Panel
        }
        if (big) {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                current?.let { PriceStat("Ahora", it, min, max, highlight = true, modifier = Modifier.weight(1f)) }
                PriceStat("Media", null, min, max, value = day.map { it.eur_kwh }.average(), modifier = Modifier.weight(1f))
                day.minByOrNull { it.eur_kwh }?.let { PriceStat("Mín ${it.time.hour}h", it, min, max, modifier = Modifier.weight(1f)) }
                day.maxByOrNull { it.eur_kwh }?.let { PriceStat("Máx ${it.time.hour}h", it, min, max, modifier = Modifier.weight(1f)) }
            }
        } else {
            Text(
                "Más barata ${day.minBy { it.eur_kwh }.let { "${it.time.format(HourMinute)} · ${euros(it.eur_kwh)}" }}   ·   " +
                    "más cara ${day.maxBy { it.eur_kwh }.let { "${it.time.format(HourMinute)} · ${euros(it.eur_kwh)}" }}",
                style = Type.Small.copy(color = Palette.InkSoft),
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        PriceBars(day, now, min, max, Modifier.fillMaxWidth().weight(1f).padding(top = 18.dp, bottom = 8.dp))
        PriceHours(day)
    }
}

/** Las horas debajo de las barras del precio, cada 3, alineadas con su barra. */
@Composable
internal fun PriceHours(hours: List<HourPrice>, fontSize: Int = 16) {
    Row(Modifier.fillMaxWidth()) {
        hours.forEachIndexed { i, p ->
            Text(
                if (p.time.hour % 3 == 0) p.time.hour.toString() else "",
                style = Type.Tiny.copy(color = Palette.InkMuted, fontSize = fontSize.sp),
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PriceStat(label: String, price: HourPrice?, min: Double, max: Double, highlight: Boolean = false, value: Double? = null, modifier: Modifier = Modifier) {
    val v = value ?: price!!.eur_kwh
    val color = priceColor(v, min, max)
    Column(
        modifier
            .background(if (highlight) color else Palette.Highlight, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(label, style = Type.Tiny.copy(color = if (highlight) Color.White else Palette.InkSoft, fontWeight = FontWeight.SemiBold), maxLines = 1, softWrap = false)
        Text(
            euros(v),
            style = Type.Body.copy(color = if (highlight) Color.White else Palette.Ink, fontWeight = FontWeight.SemiBold),
            maxLines = 1
        )
    }
}

/**
 * Las horas que se enseñan del precio: con el de mañana ya publicado (REE, ≈20:30), desde hace 2 h hasta 24 h por
 * delante; si no, el día de hoy entero.
 */
internal fun priceWindow(prices: List<HourPrice>, now: LocalDateTime): List<HourPrice> {
    val today = now.toLocalDate()
    return if (prices.any { it.time.toLocalDate() == today.plusDays(1) }) {
        val from = now.withMinute(0).withSecond(0).withNano(0).minusHours(2)
        prices.filter { !it.time.isBefore(from) && it.time.isBefore(from.plusHours(26)) }.sortedBy { it.time }
    } else prices.filter { it.time.toLocalDate() == today }
}

/** El tramo de [hours] horas seguidas más barato que empieza desde esta hora (hoy o mañana). */
/**
 * El tramo de [hours] horas seguidas más barato desde esta hora (hoy o mañana). Solo si es barato de verdad, por debajo
 * de la media de su día: por la noche, sin el precio de mañana, lo que queda de hoy suele ser caro y no se recomienda.
 * Con [allowed] (horario de carga del coche), solo tramos enteros dentro de él y sin mirar la media: no hay otras horas.
 */
internal fun cheapestWindow(
    prices: List<HourPrice>, now: LocalDateTime, hours: Int, until: LocalDateTime, allowed: ChargeHours? = null
): Triple<LocalDateTime, LocalDateTime, Double>? {
    val from = now.withMinute(0).withSecond(0).withNano(0)
    val average = prices.groupBy { it.time.toLocalDate() }.mapValues { (_, d) -> d.map { it.eur_kwh }.average() }
    val list = prices.filter { !it.time.isBefore(from) && it.time.isBefore(until) && (allowed == null || allowed.allows(it.time)) }
        .sortedBy { it.time }
    return list.windowed(hours)
        .filter { w -> w.zipWithNext().all { (a, b) -> a.time.plusHours(1) == b.time } }
        .minByOrNull { w -> w.sumOf { it.eur_kwh } }
        ?.let { w -> Triple(w.first().time, w.last().time.plusHours(1), w.sumOf { it.eur_kwh } / hours) }
        ?.takeIf { (start, _, avg) -> allowed != null || avg <= (average[start.toLocalDate()] ?: Double.MAX_VALUE) }
}

/**
 * «Mejores horas»: cargar el coche (4 h, dentro de [chargeHours] si lo hay), lavavajillas o lavadora (2 h) y el tramo
 * caro que conviene evitar hoy.
 */
@Composable
internal fun BestHoursPanel(prices: List<HourPrice>, now: LocalDateTime, modifier: Modifier, chargeHours: ChargeHours? = null) {
    val until = now.toLocalDate().plusDays(2).atStartOfDay()
    val todayEnd = now.toLocalDate().plusDays(1).atStartOfDay()
    val car = cheapestWindow(prices, now, 4, until, chargeHours)
    val wash = cheapestWindow(prices, now, 2, until)
    val avoid = prices.filter { !it.time.isBefore(now.withMinute(0)) && it.time.isBefore(todayEnd) }
        .sortedBy { it.time }.windowed(3).maxByOrNull { w -> w.sumOf { it.eur_kwh } }
    val tomorrowKnown = prices.any { it.time.toLocalDate() == now.toLocalDate().plusDays(1) }
    Panel(modifier, compact = true) {
        CardHeader("Mejores horas", Icons.Outlined.Schedule, Palette.Online, compact = true)
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (prices.isEmpty()) Text("Sin precios todavía", style = Type.Body.copy(color = Palette.InkMuted))
            car?.let { BestRow("🚗", "Cargar el coche", it, now) }
            if (car == null && chargeHours != null && prices.isNotEmpty()) {
                // Con horario de carga no se recomienda otra hora: se dice cuál es y que falta su precio
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🚗", style = Type.Large, modifier = Modifier.width(60.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Cargar el coche · ${chargeHours.label()}", style = Type.Small.copy(color = Palette.InkSoft))
                        Text(
                            if (tomorrowKnown) "Sin 4 h seguidas en tu horario" else "Falta el precio de tu horario (REE, ≈20:30)",
                            style = Type.Body.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
            wash?.let { BestRow("🍽️", "Lavavajillas o lavadora", it, now) }
            if (((car == null && chargeHours == null) || wash == null) && prices.isNotEmpty() && !tomorrowKnown) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⏳", style = Type.Large, modifier = Modifier.width(60.dp))
                    Text("Lo que queda de hoy es caro: mejor esperar al precio de mañana (REE, ≈20:30)", style = Type.Small.copy(color = Palette.InkSoft))
                }
            }
            avoid?.let { w ->
                BestRow("⛔", "Mejor evitar", Triple(w.first().time, w.last().time.plusHours(1), w.sumOf { it.eur_kwh } / 3), now, warn = true)
            }
        }
    }
}

@Composable
private fun BestRow(emoji: String, what: String, window: Triple<LocalDateTime, LocalDateTime, Double>, now: LocalDateTime, warn: Boolean = false) {
    val (from, to, avg) = window
    val day = when (from.toLocalDate()) {
        now.toLocalDate() -> "hoy"
        now.toLocalDate().plusDays(1) -> "mañana"
        else -> from.toLocalDate().shortLabel().lowercase(Spanish)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, style = Type.Large, modifier = Modifier.width(60.dp))
        Column(Modifier.weight(1f)) {
            Text(what, style = Type.Small.copy(color = Palette.InkSoft))
            Text("$day ${from.format(HourMinute)}–${to.format(HourMinute)}", style = Type.Body.copy(fontWeight = FontWeight.SemiBold))
        }
        Text(
            "${euros(avg)} €",
            style = Type.Small.copy(color = if (warn) Red else Palette.Online, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.background((if (warn) Red else Palette.Online).copy(alpha = 0.12f), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 4.dp)
        )
    }
}

/** El coche: batería grande, autonomía, si está enchufado o cargando, kilómetros y la presión de cada rueda. */
@Composable
internal fun CarPanel(car: CarStatus, modifier: Modifier) {
    Panel(modifier, compact = true) {
        CardHeader(car.modelo ?: "Coche eléctrico", Icons.Outlined.ElectricCar, Green, compact = true)
        BatteryPile(car, Modifier.fillMaxWidth().height(120.dp).padding(top = 16.dp))
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CarFact(if (car.cargando) "⚡" else if (car.enchufado) "🔌" else "🅿️", if (car.cargando) "Cargando" else if (car.enchufado) "Enchufado" else "Sin enchufar", Modifier.weight(1f))
            car.km?.let { CarFact("🛣️", "${String.format(Spanish, "%,d", it.toLong())} km", Modifier.weight(1f)) }
        }
        car.ruedas_bar?.takeIf { it.size == 4 }?.let { r ->
            // Las cuatro ruedas como se ven desde arriba: delanteras arriba
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Ruedas\n(bar)", style = Type.Tiny.copy(color = Palette.InkSoft), modifier = Modifier.width(76.dp))
                listOf("DI" to r[0], "DD" to r[1], "TI" to r[2], "TD" to r[3]).forEach { (name, bar) ->
                    val low = bar < 2.2
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).background(if (low) Red.copy(alpha = 0.12f) else Palette.Highlight, RoundedCornerShape(16.dp)).padding(vertical = 8.dp)
                    ) {
                        Text(String.format(Spanish, "%.1f", bar), style = Type.Small.copy(color = if (low) Red else Palette.Ink, fontWeight = FontWeight.SemiBold), maxLines = 1)
                        Text(name, style = Type.Tiny.copy(color = Palette.InkMuted, fontSize = 14.sp), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun CarFact(emoji: String, text: String, modifier: Modifier = Modifier) {
    Row(modifier.background(Palette.Highlight, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, style = Type.Small)
        Text(text, style = Type.Small.copy(color = Palette.Ink), maxLines = 1, modifier = Modifier.padding(start = 8.dp))
    }
}

/**
 * La batería del coche como una pila tumbada: el relleno hasta el % del color del nivel (verde > 50 %, ámbar 20–50 %,
 * rojo < 20 %; azul mientras carga, y entonces el relleno parpadea suave), con el % grande dentro y la autonomía al lado. Mientras carga, un rayo junto
 * al % y el tramo que se está llenando late por delante del nivel; enchufado sin cargar (la carga va a tramos) lo dice.
 */
@Composable
private fun BatteryPile(car: CarStatus, modifier: Modifier) {
    val level = car.bateria ?: return
    val pct = (level / 100.0).toFloat().coerceIn(0f, 1f)
    val color = when {
        car.cargando -> Charging
        level > 50 -> Green
        level >= 20 -> Amber
        else -> Red
    }
    val pulse = chargePulse(car.cargando)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1.25f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val stroke = 6.dp.toPx()
                val nub = 14.dp.toPx()
                val body = androidx.compose.ui.geometry.Size(size.width - nub - stroke, size.height - stroke)
                val r = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx())
                val o = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2)
                // Relleno con margen dentro del contorno
                val pad = stroke + 6.dp.toPx()
                drawRoundRect(color.copy(alpha = 0.18f), o, body, r)
                val inner = androidx.compose.ui.geometry.Size(body.width - 2 * pad + stroke, body.height - 2 * pad + stroke)
                drawRoundRect(color.copy(alpha = 0.75f * chargeFillAlpha(pulse)), androidx.compose.ui.geometry.Offset(pad, pad), androidx.compose.ui.geometry.Size(inner.width * pct, inner.height), androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()))
                if (pulse > 0f) drawRoundRect(color.copy(alpha = pulse * 0.6f), androidx.compose.ui.geometry.Offset(pad + inner.width * pct, pad), androidx.compose.ui.geometry.Size(inner.width * chargeSpan(pct), inner.height), androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()))
                drawRoundRect(Palette.InkSoft, o, body, r, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                drawRoundRect(Palette.InkSoft, androidx.compose.ui.geometry.Offset(size.width - nub, size.height * 0.32f), androidx.compose.ui.geometry.Size(nub, size.height * 0.36f), androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (car.cargando) androidx.compose.material3.Icon(Icons.Filled.Bolt, null, Modifier.size(44.dp), tint = Palette.Ink)
                Text("${level.toInt()} %", style = Type.Title.copy(color = Palette.Ink, fontWeight = FontWeight.Bold))
            }
        }
        Column(Modifier.weight(1f).padding(start = 20.dp), horizontalAlignment = Alignment.End) {
            Text("${car.autonomia_km?.toInt() ?: "—"} km", style = Type.Title.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
            // La ficha de debajo ya dice «Cargando»/«Enchufado»: aquí, la potencia o que la carga está en pausa
            val status = when {
                car.cargando -> "⚡ " + (car.potencia_kw?.let(::kilowatts) ?: "cargando")
                car.enchufado -> "🔌 en pausa"
                else -> "de autonomía"
            }
            Text(status, style = Type.Small.copy(color = if (car.cargando) Charging else Palette.InkSoft, fontWeight = FontWeight.SemiBold), maxLines = 1)
        }
    }
}
