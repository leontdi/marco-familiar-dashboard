package com.lito.planify.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.planify.dashboard.UiState
import com.lito.planify.dashboard.data.Event
import com.lito.planify.dashboard.data.alert
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import kotlin.math.roundToInt

/** Días que enseña la franja de la semana (hoy incluido). */
private const val WEEK_DAYS = 7

/**
 * Portada de la familia (calendarios iCal), pensada para verse de lejos:
 * - arriba, un bloque ancho con la hora, la fecha y el tiempo de hoy y de los próximos días, y a la derecha lo
 *   siguiente que toca (con cuánto falta);
 * - en el centro, la semana: hoy y los 6 días siguientes, cada uno con los turnos (Shifter) en una pastilla, todas las
 *   citas (familia, fútbol, Barça…), como la pestaña Calendario, y abajo el menú del cole de ese día;
 * - abajo, tres resúmenes que llevan a su pestaña: tareas de los niños, compra, y luz y coche.
 */
@Composable
fun FamilyTodayScreen(state: UiState, onOpen: (Page) -> Unit) {
    val now = state.now
    val today = now.toLocalDate()
    val events = state.content?.events.orEmpty()
    val shifts = state.content?.sessions.orEmpty()
    val menu = state.content?.menu.orEmpty()
    // Lo que está pasando ahora (con hora, empezado y sin terminar) va antes que lo próximo
    val live = events.firstOrNull { !it.all_day && !it.at.isAfter(now) && (it.end ?: it.at.plusHours(1)).isAfter(now) }
    val next = live ?: events.firstOrNull { !it.at.isBefore(now) && it.at.toLocalDate() < today.plusDays(WEEK_DAYS.toLong()) }
    Column(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth().weight(0.25f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            // Hora, fecha y tiempo en un solo bloque ancho (dos tercios); lo siguiente, a la derecha
            HeaderBlock(state, Modifier.weight(2f).fillMaxHeight())
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (next != null) EventTapTarget(next, Modifier.fillMaxSize()) { NextCompact(next, now, live = next === live) }
                else Box(Modifier.fillMaxSize().background(Palette.Card, RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
                    Text("Semana sin citas", style = Type.Body.copy(color = Palette.InkMuted))
                }
            }
        }
        Row(Modifier.fillMaxWidth().weight(0.53f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            (0 until WEEK_DAYS).forEach { i ->
                val day = today.plusDays(i.toLong())
                WeekDay(
                    day, today, now,
                    events.filter { it.at.toLocalDate() == day },
                    shifts.filter { it.at.toLocalDate() == day },
                    menu.firstOrNull { it.at.toLocalDate() == day },
                    Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
        Row(Modifier.fillMaxWidth().weight(0.22f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            KidsTile(state, Modifier.weight(1f).fillMaxHeight()) { onOpen(Page.Tasks) }
            ShoppingTile(state, Modifier.weight(1f).fillMaxHeight()) { onOpen(Page.Tasks) }
            EnergyTile(state, Modifier.weight(1.25f).fillMaxHeight()) { onOpen(Page.Home) }
            // Solo si pasa algo (alarma, cerradura atascada o sin cerrar de noche, batería baja)
            state.shopping?.seguridad?.alert(now)?.let { SecurityAlertTile(it, Modifier.weight(0.8f).fillMaxHeight()) { onOpen(Page.Home) } }
        }
    }
}

// --- Arriba ---

/** Lo próximo (o, mientras dura, lo de ahora), del color de su calendario: cuánto falta o hasta cuándo, título y cuándo. */
@Composable
private fun NextCompact(event: Event, now: LocalDateTime, live: Boolean) {
    val color = parseColor(event.calendar_color, Palette.Accent)
    val ink = onColor(color)
    val left = countdown(now, event.at, maxMinutes = 24 * 60)?.removePrefix("en ") ?: relativeBadge(now, event.at).lowercase(Spanish)
    Column(
        Modifier.fillMaxSize().background(color, RoundedCornerShape(28.dp)).padding(horizontal = 30.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (live) "● AHORA" else "LO PRÓXIMO", style = Type.Label.copy(color = ink.copy(alpha = 0.9f), fontSize = 17.sp), modifier = Modifier.weight(1f))
            Text(
                if (live) "hasta ${(event.end ?: event.at.plusHours(1)).format(HourMinute)}" else "en $left",
                style = Type.Small.copy(color = ink, fontWeight = FontWeight.Bold),
                modifier = Modifier.background(ink.copy(alpha = 0.18f), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 2.dp)
            )
        }
        Text(event.title, style = Type.Large.copy(color = ink, fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Text(
            "${relativeDay(event.at.toLocalDate(), now.toLocalDate())} · ${event.rangeLabel() ?: event.timeLabel()}",
            style = Type.Small.copy(color = ink.copy(alpha = 0.85f)),
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// --- Centro: la semana ---

/** Un día de la semana: cabecera (hoy resaltado), el turno en una pastilla y las citas con su color. */
@Composable
internal fun WeekDay(
    day: LocalDate, today: LocalDate, now: LocalDateTime, events: List<Event>, shifts: List<Event>, menu: Event?, modifier: Modifier,
    forecast: com.lito.planify.dashboard.data.Forecast? = null,
    small: Boolean = false
) {
    val isToday = day == today
    Column(modifier.background(if (isToday) Palette.Card else Palette.Card.copy(alpha = 0.6f), RoundedCornerShape(24.dp)).padding(horizontal = 14.dp, vertical = 14.dp).alpha(if (day < today) 0.55f else 1f)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (isToday) "Hoy" else day.dayOfWeek.getDisplayName(TextStyle.SHORT, Spanish).trimEnd('.').capitalized(),
                style = Type.Small.copy(color = if (isToday) Palette.Accent else Palette.InkSoft, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f)
            )
            // Calendario: el mes en el lunes de cada semana y, resaltado, en el día 1 (donde cambia de mes)
            if (small && (day.dayOfWeek == DayOfWeek.MONDAY || day.dayOfMonth == 1)) Text(
                day.month.getDisplayName(TextStyle.SHORT, Spanish).trimEnd('.'),
                style = Type.Small.copy(color = if (day.dayOfMonth == 1) Palette.Accent else Palette.InkSoft, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(end = 8.dp, bottom = 3.dp)
            )
            Text("${day.dayOfMonth}", style = Type.Large.copy(color = if (isToday) Palette.Accent else Palette.Ink, fontWeight = FontWeight.SemiBold))
        }
        // Calendario: el tiempo del día (icono, máx/mín y lluvia) bajo la cabecera, si hay previsión
        forecast?.dayIndex(day)?.takeIf { it >= 0 }?.let { k ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                DayWeatherIcon(forecast, day, today, 38)
                Text(
                    "${forecast.daily.temperature_2m_max[k].degrees()} ${forecast.daily.temperature_2m_min[k].degrees()}",
                    style = Type.Tiny.copy(color = Palette.InkSoft, fontSize = 17.sp),
                    modifier = Modifier.padding(start = 6.dp).weight(1f)
                )
                Text("💧${forecast.daily.precipitation_probability_max[k] ?: 0}%", style = Type.Tiny.copy(color = if ((forecast.daily.precipitation_probability_max[k] ?: 0) >= 40) Palette.Rain else Palette.InkMuted, fontSize = 15.sp))
            }
        }
        shifts.forEach { s ->
            val c = parseColor(s.calendar_color)
            EventTapTarget(s, Modifier.padding(top = 8.dp)) {
                Text(
                    "${s.title} ${s.rangeLabel().orEmpty()}",
                    style = Type.Tiny.copy(color = onColor(c), fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().background(c, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }
        Hairline(Modifier.padding(vertical = 10.dp))
        Column(Modifier.weight(1f).fadingScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (events.isEmpty()) Text("—", style = Type.Small.copy(color = Palette.InkMuted))
            events.forEach { e -> EventTapTarget(e) { WeekItem(e, past = e.isPast(now), small = small) } }
        }
        menu?.let { MenuPill(it, Modifier.padding(top = 8.dp), small = small) }
    }
}

private val MenuGreen = Color(0xFF7CB342)

/** El menú del cole de un día: «🍽️ primero · segundo» y, si un niño come otra cosa, «Nombre: …» debajo. */
@Composable
private fun MenuPill(menu: Event, modifier: Modifier, small: Boolean = false) {
    val other = Regex("""\s*\(([^():]+):\s*([^()]+)\)\s*$""").find(menu.title)
    EventTapTarget(menu, modifier) {
        Column(Modifier.fillMaxWidth().background(MenuGreen.copy(alpha = 0.14f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                "🍽️ " + (other?.let { menu.title.substring(0, it.range.first) } ?: menu.title),
                style = Type.Tiny.copy(color = Palette.Ink, fontSize = if (small) 17.sp else 19.sp, lineHeight = if (small) 21.sp else 24.sp),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            other?.let {
                Text("${it.groupValues[1]}: ${it.groupValues[2]}", style = Type.Tiny.copy(color = Palette.InkSoft, fontSize = 17.sp, lineHeight = 21.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun WeekItem(e: Event, past: Boolean, small: Boolean = false) {
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min).alpha(if (past) 0.4f else 1f)) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(parseColor(e.calendar_color), RoundedCornerShape(3.dp)))
        Column(Modifier.padding(start = 8.dp)) {
            Text(if (e.all_day) "Todo el día" else e.at.format(HourMinute), style = Type.Tiny.copy(color = Palette.InkSoft, fontSize = if (small) 17.sp else 19.sp, fontWeight = FontWeight.SemiBold))
            Text(e.title, style = Type.Small.copy(color = Palette.Ink, fontSize = if (small) 20.sp else 23.sp, lineHeight = if (small) 25.sp else 28.sp), maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

// --- Abajo: resúmenes ---

@Composable
private fun Tile(title: String, emoji: String, tint: Color, modifier: Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .background(Palette.Card, RoundedCornerShape(28.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = Type.Body)
            Text(title.uppercase(Spanish), style = Type.Label.copy(color = tint, fontSize = 18.sp), modifier = Modifier.padding(start = 10.dp))
        }
        content()
    }
}

@Composable
private fun KidsTile(state: UiState, modifier: Modifier, onClick: () -> Unit) {
    val chores = state.chores
    Tile("Niños", "🐾", Palette.Warm, modifier, onClick) {
        if (chores == null) return@Tile
        val left = chores.pending()
        Text(if (left == 0) "🎉 Todo hecho" else "$left ${if (left == 1) "tarea" else "tareas"} por hacer", style = Type.Body.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            chores.ninos.orEmpty().forEach { k ->
                Text(
                    "${k.nombre} ⭐ ${k.estrellas}",
                    style = Type.Small.copy(color = Palette.Ink, fontWeight = FontWeight.Medium),
                    modifier = Modifier.background(parseColor(k.color, Palette.Warm).copy(alpha = 0.14f), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }
        }
        chores.pedidas?.size?.takeIf { it > 0 }?.let { Text("🎁 $it ${if (it == 1) "canje" else "canjes"} por aprobar", style = Type.Tiny.copy(color = Palette.Accent, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 6.dp)) }
    }
}

@Composable
private fun ShoppingTile(state: UiState, modifier: Modifier, onClick: () -> Unit) {
    val items = state.shopping?.shopping().orEmpty()
    Tile("Compra", "🛒", Palette.Online, modifier, onClick) {
        Text(if (items.isEmpty()) "Nada que comprar" else "${items.size} ${if (items.size == 1) "cosa" else "cosas"}", style = Type.Body.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 8.dp))
        Text(items.take(4).joinToString(" · ") { it.producto }, style = Type.Small.copy(color = Palette.InkSoft), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun EnergyTile(state: UiState, modifier: Modifier, onClick: () -> Unit) {
    val now = state.now
    val window = priceWindow(state.energy?.prices.orEmpty(), now)
    val current = window.lastOrNull { !it.time.isAfter(now) }
    val min = window.minOfOrNull { it.eur_kwh } ?: 0.0
    val max = window.maxOfOrNull { it.eur_kwh } ?: 0.0
    val car = state.shopping?.coche
    Tile("Luz y coche", "⚡", Amber, modifier, onClick) {
        Row(Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp)) {
            car?.bateria?.let { BatteryGauge(it, car, Modifier.width(170.dp).fillMaxHeight().padding(end = 18.dp)) }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                current?.let {
                    Text(
                        "${euros(it.eur_kwh)} €/kWh",
                        style = Type.Small.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.background(priceColor(it.eur_kwh, min, max), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }
                if (window.isNotEmpty()) {
                    PriceBars(window, now, min, max, Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp, bottom = 4.dp))
                    PriceHours(window, fontSize = 13)
                }
            }
        }
    }
}
