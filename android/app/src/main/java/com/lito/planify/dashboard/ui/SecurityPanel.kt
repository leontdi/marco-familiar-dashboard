package com.lito.planify.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.planify.dashboard.data.Alarm
import com.lito.planify.dashboard.data.DoorLock
import com.lito.planify.dashboard.data.Security
import com.lito.planify.dashboard.data.alert
import com.lito.planify.dashboard.data.isArmed
import com.lito.planify.dashboard.data.isNight
import com.lito.planify.dashboard.data.isUnlocked
import com.lito.planify.dashboard.data.lowBattery
import com.lito.planify.dashboard.data.stateText
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type
import java.time.LocalDateTime

/** Pestaña Casa: la cerradura (estado, batería y, con sensor, la puerta) y la alarma. Solo para mirar: sin botones. */
@Composable
internal fun SecurityPanel(security: Security, now: LocalDateTime, modifier: Modifier) {
    val alert = security.alert(now)
    Panel(modifier, compact = true) {
        CardHeader("Seguridad", Icons.Outlined.Shield, if (alert != null) Red else Green, compact = true)
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            security.cerradura?.let { LockRow(it, now) }
            security.alarma?.let { AlarmRow(it) }
        }
    }
}

@Composable
private fun LockRow(lock: DoorLock, now: LocalDateTime) {
    val bad = lock.estado == "jammed" || (isNight(now) && lock.isUnlocked())
    val emoji = when {
        lock.estado == null -> "🔐"
        lock.estado == "locked" -> "🔒"
        lock.estado == "jammed" -> "⚠️"
        else -> "🔓"
    }
    StatusRow(emoji, lock.nombre ?: "Puerta", lock.stateText(), when {
        bad -> Red
        lock.estado == null -> Palette.InkMuted
        else -> Palette.Ink
    }) {
        lock.bateria?.let { b ->
            val low = lock.lowBattery()
            Pill("${if (lock.cargando == true) "⚡" else "🔋"} ${b.toInt()} %", if (low && lock.cargando != true) Red else Palette.InkSoft)
        }
    }
    lock.puerta?.let { door ->
        StatusRow("🚪", "Puerta", if (door == "abierta") "Abierta" else "Cerrada", if (door == "abierta" && isNight(now)) Red else Palette.Ink)
    }
}

@Composable
private fun AlarmRow(alarm: Alarm) {
    val color = when {
        alarm.estado == "triggered" -> Red
        alarm.isArmed() -> Green
        alarm.estado == null -> Palette.InkMuted
        else -> Palette.Ink
    }
    StatusRow(if (alarm.estado == "triggered") "🚨" else "🛡️", alarm.nombre ?: "Alarma", alarm.stateText(), color)
}

@Composable
private fun StatusRow(emoji: String, what: String, state: String, color: Color, extra: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, style = Type.Large, modifier = Modifier.width(60.dp))
        Column(Modifier.weight(1f)) {
            Text(what, style = Type.Small.copy(color = Palette.InkSoft), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(state, style = Type.Body.copy(color = color, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        extra()
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(
        text,
        style = Type.Small.copy(color = color, fontWeight = FontWeight.SemiBold),
        modifier = Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 4.dp)
    )
}

/** Portada: aviso rojo y pequeño, solo cuando pasa algo; lleva a la pestaña Casa. */
@Composable
internal fun SecurityAlertTile(text: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .background(Red.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        Text("SEGURIDAD", style = Type.Label.copy(color = Red, fontSize = 18.sp))
        Text(text, style = Type.Body.copy(color = Red, fontWeight = FontWeight.SemiBold), maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
    }
}
