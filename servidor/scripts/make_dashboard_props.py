#!/usr/bin/env python3
"""Genera android/dashboard.properties (ignorado por git) para compilar la app del marco.

Junta las direcciones secretas iCal de secrets/calendarios.properties, los calendarios de «calendarios» en
config/familia.json y los colores de los turnos de config/turnos-colores.json. Las direcciones quedan dentro del
APK, que solo vive en el ordenador donde se compila y en el marco.

La lista de la compra y las tareas: `compra.json` y `tareas.url` de calendarios.properties o, si no están, a partir
de «publico.url_base» de la configuración. El token del marco (`tareas.token`) siempre de calendarios.properties.

Uso: python3 -I scripts/make_dashboard_props.py
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import familia  # noqa: E402
import gcal  # noqa: E402

cfg = familia.config()
# clave → (nombre en el marco, color del calendario, papel: events = citas, shifts = turnos, menu = menú del cole)
CALENDARS = {k: (v["nombre"], v["color"], v.get("papel", "events"))
             for k, v in cfg.get("calendarios", {}).items() if not k.startswith("_")}

p = gcal.props()
base = cfg.get("publico", {}).get("url_base", "")
if "<" in base:
    base = ""  # aún con el marcador <RUTA-SECRETA> del ejemplo
if base and not base.endswith("/"):
    base += "/"
compra = p.get("compra.json") or (base + "compra.json" if base else "")
tareas = p.get("tareas.url") or (base + "tareas/" if base else "")

lines = ["# Generado por servidor/scripts/make_dashboard_props.py: no editar a mano ni subir a git.", ""]
for key, (name, color, role) in CALENDARS.items():
    if not p.get(key + ".ics"):
        continue  # calendario opcional sin dirección todavía
    lines += [f"calendar.{key}.name={name}", f"calendar.{key}.color={color}",
              f"calendar.{key}.role={role}", f"calendar.{key}.url={p[key + '.ics']}", ""]
if compra:
    lines += ["# Lista de la compra y casa (Bring!, Todoist, coche… vía Home Assistant), ruta secreta", f"compra.url={compra}", ""]
carga = (cfg.get("coche") or {}).get("horas_carga") or {}
carga = {k: carga[c] for c, k in (("laborables", "weekdays"), ("fin_de_semana", "weekend")) if carga.get(c)}
if carga:
    lines += ["# Horas en que se puede cargar el coche: «Mejores horas» solo recomienda cargar ahí",
              *(f"car.charge.{k}={v}" for k, v in carga.items()), ""]
if tareas and p.get("tareas.token"):
    lines += ["# Tareas de los niños (TaskMate vía familia-tareas en el servidor), ruta secreta + token del marco",
              f"tareas.url={tareas}", f"tareas.token={p['tareas.token']}", ""]
with open(familia.ruta("turnos_colores", "config/turnos-colores.json"), encoding="utf-8") as f:
    for texto, t in json.load(f)["turnos"].items():
        lines.append(f"shift.color.{texto.replace(' ', '_')}={t['color']}")

out = familia.ruta("dashboard_properties", "../android/dashboard.properties")
with open(out, "w", encoding="utf-8") as f:
    f.write("\n".join(lines) + "\n")
os.chmod(out, 0o600)
print(f"{out}: {sum(1 for k in CALENDARS if p.get(k + '.ics'))} calendarios")
