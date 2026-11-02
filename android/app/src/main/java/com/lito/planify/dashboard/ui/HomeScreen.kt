package com.lito.planify.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Deck
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Upcoming
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.planify.dashboard.UiState
import com.lito.planify.dashboard.data.Awning
import com.lito.planify.dashboard.data.Climate
import com.lito.planify.dashboard.data.Music
import com.lito.planify.dashboard.data.RoomTemp
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type
import kotlin.math.roundToInt

private val Heat = Color(0xFFE8604C)
private val Sky = Color(0xFF3F7FD9)
private val Purple = Color(0xFF8E5BC8)

/**
 * Órdenes a la casa desde la pestaña Casa: consigna del termostato (grados), modo (preset de HA), toldo
 * («open»/«close»/«stop») y música (acción, altavoz).
 */
class HomeCallbacks(
    val onSetpoint: (Double) -> Unit = {},
    val onPreset: (String) -> Unit = {},
    val onAwning: (String) -> Unit = {},
    val onMusic: (String, String) -> Unit = { _, _ -> }
)

/**
 * Casa: a la izquierda la calefacción (temperatura grande, consigna con − / +, modos, humedad y pila del termostato) y
 * la temperatura de cada estancia; en el centro el toldo y la música de los Echo; a la derecha lo que aún no está en
 * Home Assistant (luces, lavavajillas, aire, cargador), para que se vea qué viene.
 */
@Composable
fun HomeScreen(state: UiState, actions: HomeCallbacks) {
    val casa = state.shopping?.casa
    ScreenPadding {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.weight(1.15f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                casa?.clima?.let { ClimatePanel(it, casa.estancias.orEmpty(), actions, Modifier.fillMaxWidth().weight(1f)) }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                casa?.toldo?.let { AwningPanel(it, actions, Modifier.fillMaxWidth()) }
                MusicPanel(casa?.musica, actions, Modifier.fillMaxWidth().weight(1f))
            }
            Column(Modifier.weight(0.62f).fillMaxHeight()) {
                ComingSoonPanel(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
internal fun ClimatePanel(c: Climate, rooms: List<RoomTemp>, actions: HomeCallbacks, modifier: Modifier, compact: Boolean = false) {
    val heating = c.estado == "heat"
    Panel(modifier, compact = true) {
        CardHeader("Calefacción · ${c.nombre ?: "Salón"}", Icons.Outlined.Thermostat, Heat, compact = true)
        val now: @Composable () -> Unit = {
            Column {
                Text("Ahora", style = Type.Small.copy(color = Palette.InkSoft))
                Text(c.temp?.let { "${it.decimal()}°" } ?: "—", style = Type.Huge.copy(fontSize = if (compact) 96.sp else 124.sp, lineHeight = if (compact) 100.sp else 128.sp), maxLines = 1, softWrap = false)
                Text(
                    listOfNotNull(c.humedad?.let { "💧 ${it.roundToInt()} %" }, c.pila?.let { (if (it < 30) "🪫 " else "🔋 ") + "pila ${it.roundToInt()} %" }).joinToString("   "),
                    style = Type.Small.copy(color = if ((c.pila ?: 100.0) < 30) Red else Palette.InkSoft),
                    maxLines = 1
                )
            }
        }
        // Consigna con − / +, de medio en medio grado
        val setpoint: @Composable () -> Unit = {
            Column(horizontalAlignment = if (compact) Alignment.Start else Alignment.CenterHorizontally) {
                if (!compact) Text("Consigna", style = Type.Small.copy(color = Palette.InkSoft))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    if (compact) Text("Consigna", style = Type.Small.copy(color = Palette.InkSoft), modifier = Modifier.padding(end = 18.dp))
                    RoundButton("−", Heat, size = if (compact) 60 else 72) { c.consigna?.let { actions.onSetpoint(it - 0.5) } }
                    Text(c.consigna?.let { "${it.decimal()}°" } ?: "—", style = Type.Title.copy(fontWeight = FontWeight.SemiBold, fontSize = if (compact) 40.sp else 46.sp), modifier = Modifier.padding(horizontal = 16.dp))
                    RoundButton("+", Heat, size = if (compact) 60 else 72) { c.consigna?.let { actions.onSetpoint(it + 0.5) } }
                    if (compact) Text(if (heating) "🔥" else "", style = Type.Large, modifier = Modifier.padding(start = 14.dp))
                }
                if (!compact) Text(if (heating) "🔥 Calentando" else "En reposo", style = Type.Small.copy(color = if (heating) Heat else Palette.InkMuted, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 10.dp))
            }
        }
        if (compact) {
            Column(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                now()
                setpoint()
            }
        } else {
            Row(Modifier.fillMaxWidth().weight(1f).padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { now() }
                setpoint()
            }
        }
        // Modos del termostato (presets de HA)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
            listOf("home" to "🏠 Casa", "away" to "🚪 Fuera", "night" to "🌙 Noche", "freeze" to "❄️ Hielo").forEach { (preset, label) ->
                Chip(if (compact) label.substringAfter(' ') else label, selected = c.modo == preset, color = Heat, modifier = Modifier.weight(1f)) { actions.onPreset(preset) }
            }
        }
        if (rooms.isNotEmpty()) {
            Hairline(Modifier.padding(vertical = if (compact) 12.dp else 18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                rooms.forEach { r ->
                    Column(Modifier.weight(1f).background(Palette.Highlight, RoundedCornerShape(20.dp)).padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text(r.nombre, style = Type.Small.copy(color = Palette.InkSoft))
                        Text("${r.temp.decimal()}°", style = Type.Large.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}

@Composable
internal fun AwningPanel(a: Awning, actions: HomeCallbacks, modifier: Modifier) {
    val out = a.sacado ?: 0
    Panel(modifier, compact = true) {
        CardHeader(a.nombre ?: "Toldo", Icons.Outlined.Deck, Palette.Warm, compact = true)
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            // El toldo visto de lado: la lona sale tanto como esté sacado
            Box(Modifier.width(150.dp).height(70.dp)) {
                Box(Modifier.width(12.dp).fillMaxHeight().background(Palette.InkSoft, RoundedCornerShape(4.dp)))
                Box(Modifier.padding(start = 12.dp, top = 6.dp).width((14 + 120 * out / 100).dp).height(16.dp).background(Palette.Warm, RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)))
            }
            Column(Modifier.weight(1f).padding(start = 18.dp)) {
                Text(if (out <= 2) "Recogido" else if (out >= 98) "Sacado" else "Sacado al $out %", style = Type.Large.copy(fontWeight = FontWeight.SemiBold))
                if (!a.disponible) Text("Sin conexión", style = Type.Small.copy(color = Red))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Chip("⬇ Sacar", selected = false, color = Palette.Warm, modifier = Modifier.weight(1f)) { actions.onAwning("open") }
            Chip("⏸ Parar", selected = false, color = Palette.Warm, modifier = Modifier.weight(1f)) { actions.onAwning("stop") }
            Chip("⬆ Recoger", selected = false, color = Palette.Warm, modifier = Modifier.weight(1f)) { actions.onAwning("close") }
        }
    }
}

@Composable
internal fun MusicPanel(m: Music?, actions: HomeCallbacks, modifier: Modifier) {
    Panel(modifier, compact = true) {
        CardHeader("Música", Icons.Outlined.MusicNote, Purple, compact = true)
        if (m == null) {
            Text("No suena nada en los Echo", style = Type.Body.copy(color = Palette.InkMuted), modifier = Modifier.padding(top = 18.dp))
            Text("«Alexa, pon música en el salón»", style = Type.Small.copy(color = Palette.InkMuted), modifier = Modifier.padding(top = 6.dp))
            return@Panel
        }
        val speaker = m.entidad.orEmpty()
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp).background(Purple.copy(alpha = 0.14f), RoundedCornerShape(24.dp))) {
                Text("🎵", style = Type.Title)
            }
            Column(Modifier.weight(1f).padding(start = 22.dp)) {
                Text(m.titulo.orEmpty(), style = Type.Large.copy(fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(m.artista.orEmpty(), style = Type.Body.copy(color = Palette.InkSoft), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${if (m.sonando) "▶ Sonando" else "⏸ En pausa"} · ${m.altavoz.orEmpty()}",
                    style = Type.Small.copy(color = Purple, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            RoundButton("⏮", Purple, size = 84) { actions.onMusic("previous", speaker) }
            RoundButton(if (m.sonando) "⏸" else "▶", Purple, size = 108, filled = true, modifier = Modifier.padding(horizontal = 28.dp)) {
                actions.onMusic(if (m.sonando) "pause" else "play", speaker)
            }
            RoundButton("⏭", Purple, size = 84) { actions.onMusic("next", speaker) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundButton("🔉", Purple, size = 64) { actions.onMusic("volume_down", speaker) }
            Box(Modifier.weight(1f).padding(horizontal = 18.dp).height(12.dp).background(Palette.Line, RoundedCornerShape(6.dp))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(((m.volumen ?: 0.0).toFloat()).coerceIn(0.02f, 1f)).background(Purple, RoundedCornerShape(6.dp)))
            }
            RoundButton("🔊", Purple, size = 64) { actions.onMusic("volume_up", speaker) }
        }
    }
}

/** Lo que falta por integrar, en gris: así se ve qué vendrá y qué necesita. */
@Composable
internal fun ComingSoonPanel(modifier: Modifier) {
    Panel(modifier, compact = true) {
        CardHeader("Próximamente", Icons.Outlined.Upcoming, Palette.InkMuted, compact = true)
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                "💡" to ("Luces" to "Tuya / Smart Life"),
                "🍽️" to ("Lavavajillas" to "Home Connect"),
                "❄️" to ("Aire acondicionado" to "Daikin Onecta"),
                "🔌" to ("Cargador del coche" to "V2C (HA en casa)")
            ).forEach { (emoji, info) ->
                Row(
                    Modifier.fillMaxWidth().background(Palette.Highlight, RoundedCornerShape(20.dp)).padding(horizontal = 18.dp, vertical = 14.dp).alpha(0.75f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(emoji, style = Type.Large, modifier = Modifier.width(56.dp))
                    Column {
                        Text(info.first, style = Type.Body.copy(fontWeight = FontWeight.Medium))
                        Text(info.second, style = Type.Tiny.copy(color = Palette.InkMuted))
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundButton(text: String, color: Color, size: Int = 72, filled: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size.dp)
            .background(if (filled) color else color.copy(alpha = 0.14f), CircleShape)
            .clickable(onClick = onClick)
    ) {
        Text(text, style = Type.Large.copy(color = if (filled) Color.White else color, fontWeight = FontWeight.SemiBold, fontSize = (size * 0.42f).sp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(if (selected) color else color.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
    ) {
        Text(text, style = Type.Small.copy(color = if (selected) Color.White else Palette.Ink, fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

/**
 * Prueba: Casa y Energía en una sola pestaña mientras faltan dispositivos por integrar. A la
 * izquierda la luz (precio de hoy y mejores horas); en el centro la calefacción y el coche; a la derecha el toldo, la
 * música y lo que vendrá, en una línea.
 */
@Composable
fun HomeEnergyScreen(state: UiState, actions: HomeCallbacks) {
    val casa = state.shopping?.casa
    val now = state.now
    val prices = state.energy?.prices.orEmpty()
    val car = state.shopping?.coche
    ScreenPadding {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1.2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                // Con el precio de mañana publicado (≈20:30), las próximas 24 h; si no, el día de hoy
                val tomorrowKnown = prices.any { it.time.toLocalDate() == now.toLocalDate().plusDays(1) }
                PricePanel(if (tomorrowKnown) "Luz · próximas 24 h" else "Luz hoy", priceWindow(prices, now), now, Modifier.fillMaxWidth().weight(1f), big = true)
                BestHoursPanel(prices, now, Modifier.fillMaxWidth(), state.carCharge)
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                casa?.clima?.let { ClimatePanel(it, casa.estancias.orEmpty(), actions, Modifier.fillMaxWidth().weight(1f), compact = true) }
                car?.bateria?.let { CarPanel(car, Modifier.fillMaxWidth()) }
            }
            Column(Modifier.weight(0.95f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                casa?.toldo?.let { AwningPanel(it, actions, Modifier.fillMaxWidth()) }
                MusicPanel(casa?.musica, actions, Modifier.fillMaxWidth().weight(1f))
                ComingSoonRow(Modifier.fillMaxWidth())
            }
        }
    }
}

/** Lo que vendrá, en una sola fila de iconos: luces, lavavajillas, aire y cargador. */
@Composable
private fun ComingSoonRow(modifier: Modifier) {
    Row(
        modifier.background(Palette.Card, RoundedCornerShape(28.dp)).padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Pronto", style = Type.Small.copy(color = Palette.InkMuted, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(end = 12.dp))
        listOf("💡" to "Luces", "🍽️" to "Lavavajillas", "❄️" to "Aire", "🔌" to "Cargador").forEach { (emoji, name) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).alpha(0.7f)) {
                Text(emoji, style = Type.Large)
                Text(name, style = Type.Tiny.copy(color = Palette.InkMuted, fontSize = 15.sp), maxLines = 1)
            }
        }
    }
}
