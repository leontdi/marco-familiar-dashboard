# Servidor del marco familiar

Scripts y servicios que corren en un servidor propio (un VPS o un ordenador siempre encendido) y alimentan el
dashboard del marco de fotos: calendarios de Google (turnos, menú del cole, partidos de fútbol), la lista de la
compra y las tareas de casa (Home Assistant), el coche, el termostato, el toldo, la música y las tareas y recompensas
de los niños (TaskMate).

Todo lo concreto de cada familia (entidades de Home Assistant, nombres y colores de los niños, calendarios, rutas,
equipos…) está en **un único fichero de configuración**, `config/familia.json`, que se crea a partir de
[`config/familia.example.json`](config/familia.example.json). Los valores del ejemplo son inventados: la familia de
ejemplo tiene dos niños, Leo y Hugo, y Ana es la persona con turnos.

## Contenido

```
servidor/
├── scripts/
│   ├── familia.py              módulo común: lee config/familia.json (o FAMILIA_CONFIG)
│   ├── gcal.py                 acceso a Google Calendar con la cuenta de servicio (y `check`)
│   ├── shifter2ics.py          .Shifter (app de turnos) → .ics
│   ├── shifter_sync.py         .Shifter → calendario de turnos de Google
│   ├── menu_sync.py            menus/menu-AAAA-MM.json → calendario del menú del cole
│   ├── rffm_sync.py            partidos de la RFFM → calendario de fútbol
│   ├── drive_watch.py          vigila la carpeta de Drive: turnos y menús (PDF → JSON con Claude Code)
│   ├── compra_sync.py          espejo Alexa ⇄ Bring! de la lista de la compra
│   ├── compra_export.py        Home Assistant → compra.json (compra, listas, coche, casa, habituales)
│   ├── tareas_api.py           API local para el marco: TaskMate, compra y casa (token + PIN de padres)
│   └── make_dashboard_props.py genera android/dashboard.properties para compilar la app
├── config/
│   ├── familia.example.json         configuración de ejemplo (claves «_nota» = comentarios)
│   ├── turnos-colores.example.json  colores de cada tipo de turno
│   └── calendarios.properties.example  plantilla de los secretos de los calendarios
├── menus/menu-ejemplo.json     modelo de formato del menú (inventado)
└── despliegue/
    ├── systemd/                familia-compra.service/.timer, familia-drive.service/.timer, familia-tareas.service
    ├── nginx-ejemplo.conf      rutas secretas de compra.json y /tareas/
    └── home-assistant/         compose.yaml, configuration.example.yaml, automations.example.yaml
```

## Qué hace cada pieza

| Script | Lo lanza | Qué hace |
|---|---|---|
| `compra_sync.py` | `familia-compra.service` (cada minuto, antes del exportador) | Mantiene la lista de la compra de Alexa como espejo de la de Bring!. Recuerda lo que ya estaba en las dos para distinguir «nuevo» de «quitado»: lo dicho a Alexa pasa a Bring!, lo comprado en Bring! (o en el marco) se quita de Alexa y «Alexa, quita…» lo marca comprado en Bring! (sigue en «recientes» por si fue un error). **No borra el historial** de tachados de Alexa. Cuenta lo que se pide (una vez por día y producto) para los «habituales». |
| `compra_export.py` | `familia-compra.service` | Lee Home Assistant (solo lectura) y escribe `compra.json`: la lista de la compra, las listas del marco (Bring!, Todoist con fechas), el coche (modelo, batería, autonomía, carga y su potencia, kilómetros, ruedas), la casa (termostato, temperatura de las estancias, toldo, música en un Echo) y los 3 habituales que no están en la lista. Solo escribe si algo cambia. |
| `tareas_api.py` | `familia-tareas.service` (siempre) | Servidor HTTP en `127.0.0.1:<tareas.puerto>` detrás de nginx. Para el marco: tareas de hoy de TaskMate (marcar hecha, deshacer), recompensas (pedir; aprobar o rechazar con el PIN de padres), quitar o añadir a la compra, consigna y modo del termostato, toldo y música. Solo esas operaciones y con los valores comprobados; Home Assistant nunca se expone. |
| `drive_watch.py` | `familia-drive.timer` (cada 15 min) | Vigila la carpeta compartida de Google Drive: cada `.Shifter` nuevo de la subcarpeta de turnos va a `shifter_sync.py`; cada PDF de la de menús lo pasa a JSON Claude Code (`claude -p`), se comprueba (mes reciente, 12–23 días laborables, sin alérgenos en los platos…) y va a `menu_sync.py`. Avisa en Todoist de lo cargado o de lo que ha fallado. Cada versión de fichero se procesa una vez. |
| `shifter_sync.py` | `drive_watch.py` o a mano | Turnos del `.Shifter` → calendario de turnos, con ID fijo por día y franja (repetir actualiza, no duplica); borra los que ya no están, solo los creados por el script. |
| `shifter2ics.py` | a mano | Lo mismo pero a un fichero `.ics`. |
| `menu_sync.py` | `drive_watch.py` o a mano | Un evento de día completo por día de menú: 1.º · 2.º plato y, si cambia, el del menú alternativo de uno de los niños (una dieta especial). |
| `rffm_sync.py` | a mano o con un temporizador propio | Lee el calendario de cada equipo en la web de la RFFM (Real Federación de Fútbol de Madrid) y crea un evento por partido, con campo (o el probable), hora o «por confirmar» y resultado. `--print` solo enseña la tabla. |
| `gcal.py` | los anteriores | Token de la cuenta de servicio (JWT firmado con `openssl`) y llamadas a la API de Calendar. `python3 -I scripts/gcal.py check` comprueba los iCal y los permisos sin enseñar secretos. |
| `make_dashboard_props.py` | a mano, al compilar la app | Junta las direcciones iCal, las URL de compra y tareas, el token del marco y los colores de los turnos en `android/dashboard.properties` (chmod 600, ignorado por git). |

## Flujo de datos

```
  Alexa (voz) ──┐                                  ┌──────────────────────────────┐
  Bring! ───────┤                                  │ compra_sync.py  (cada minuto)│
  Todoist ──────┼──► Home Assistant (127.0.0.1) ◄──┤ compra_export.py ─► compra.json ──┐
  TaskMate ─────┤        ▲                         │ tareas_api.py (127.0.0.1:8124) ◄──┼──┐
  Termostato,   │        │                         └──────────────────────────────┘   │  │
  toldo, coche, ┘        │ avisos (Todoist)                                           │  │
  Echo                   │                                                            ▼  │
                         │                                     nginx (HTTPS, ruta secreta,│
  Google Drive ──► drive_watch.py ──► shifter_sync.py ─┐       X-Marco en /tareas/)       │
   (.Shifter, PDF)       │   └─ claude -p ─► menu_sync.py ─┼──► Google Calendar           │
  RFFM (web) ──────────► rffm_sync.py ─────────────────┘          │ iCal secreto          │
                                                                  ▼                       │
                                                         ┌──────────────────┐             │
                                                         │ Marco (app)      │◄────────────┘
                                                         │ calendarios,     │  compra.json (lectura)
                                                         │ compra, tareas…  │  /tareas/ (lectura y órdenes)
                                                         └──────────────────┘
```

## Requisitos

- **Python 3.9 o posterior** (usa `zoneinfo`), solo biblioteca estándar: sin `pip install`.
- **openssl** en el PATH para `gcal.py` (firma el JWT de la cuenta de servicio de Google). Lo usan `shifter_sync.py`,
  `menu_sync.py`, `rffm_sync.py`, `drive_watch.py` y `make_dashboard_props.py`.
- **Claude Code** (`claude` CLI, con sesión iniciada en el usuario que lo ejecuta) solo para pasar los PDF de los
  menús a JSON en `drive_watch.py` (ruta del ejecutable en `menu.claude`). Sin él, los turnos siguen funcionando.
- **Home Assistant** en el mismo servidor, con las integraciones que uses: Alexa Media Player, Bring!, Todoist,
  TaskMate, la del termostato, la del toldo y la del coche.
- **systemd** y **nginx** (o equivalentes) para los servicios y la publicación.
- Una **cuenta de servicio de Google** con la API de Calendar (y la de Drive, para `drive_watch.py`) activada; los
  calendarios en los que escriben los scripts se comparten con su correo con permiso para hacer cambios, y la
  carpeta de Drive, en solo lectura.

## Instalación

Rutas de ejemplo: `/srv/familia/marco` (scripts, configuración, secretos y estado), `/srv/familia/publico`
(compra.json), `/srv/familia/tareas` (el servicio de tareas) y `/srv/familia/homeassistant`.

1. **Copiar los ficheros**

   ```sh
   sudo mkdir -p /srv/familia/{marco,publico,tareas,homeassistant}
   sudo cp -r servidor/scripts servidor/config servidor/menus /srv/familia/marco/
   sudo mkdir -p /srv/familia/marco/{secrets,backups/turnos}
   sudo cp servidor/scripts/{tareas_api.py,familia.py} /srv/familia/tareas/
   sudo chmod 700 /srv/familia/marco
   ```

2. **Crear la configuración**: `config/familia.json` a partir de `familia.example.json` (y
   `config/turnos-colores.json` a partir del suyo). Rellena las entidades de Home Assistant (Herramientas para
   desarrolladores → Estados), los niños, los calendarios, la carpeta de Drive y los equipos. En las URL de la RFFM,
   sustituye `<TEMPORADA>`, `<ID-COMPETICION>` e `<ID-GRUPO>` por los de la página de calendario del grupo; mientras
   tengan marcadores, `rffm_sync.py` se niega a seguir.

3. **Secretos, cada uno en su fichero con chmod 600** (nunca en git ni en `familia.json`):

   ```sh
   cd /srv/familia/marco
   umask 077
   printf '%s' '<TOKEN-DE-LARGA-DURACIÓN-DE-HA>' > ha.token   # HA → Perfil → Seguridad → Tokens de acceso de larga duración
   openssl rand -hex 32 > marco.token                          # el mismo valor va en tareas.token del APK
   printf '%s' '<PIN-DE-PADRES>' > padres.pin
   cp config/calendarios.properties.example secrets/calendarios.properties   # y rellénalo
   cp ~/clave-cuenta-de-servicio.json secrets/                 # el único *.json de secrets/
   chmod 600 ha.token marco.token padres.pin secrets/*
   ```

   Comprueba los calendarios con `python3 -I scripts/gcal.py check`.

4. **Home Assistant**: copia `despliegue/home-assistant/compose.yaml` en `/srv/familia/homeassistant/`, deja HA
   escuchando solo en 127.0.0.1 (en las versiones recientes, con `server_host` en `config/.storage/http` y **no** en
   `configuration.yaml`: ver `configuration.example.yaml` y «Lo que aprendimos» en el README principal; compruébalo
   con `ss -ltn | grep 8123`) y, si quieres pasar por voz tareas de Alexa a Todoist, la automatización de
   `automations.example.yaml`. La lista de la compra **no** necesita automatización:
   la mantiene `compra_sync.py` (quita cualquier automatización antigua «Alexa → Bring!», que borraría de Alexa lo
   que pasa a Bring!).

5. **systemd**: copia `despliegue/systemd/*` en `/etc/systemd/system/` y:

   ```sh
   sudo systemctl daemon-reload
   sudo systemctl enable --now familia-compra.timer familia-drive.timer familia-tareas.service
   ```

   Si cambias `tareas.puerto`, cámbialo también en nginx. Si `familia.json` cambia, reinicia `familia-tareas`
   (lo recibe como credencial al arrancar).

6. **nginx**: añade las `location` de `despliegue/nginx-ejemplo.conf` al bloque HTTPS de tu dominio, con una
   `<RUTA-SECRETA>` aleatoria (`openssl rand -hex 16`), y `nginx -t && systemctl reload nginx`.

7. **La app del marco**: en el ordenador donde compilas, con una copia de `familia.json` **sin** `rutas.base` (o con
   `FAMILIA_CONFIG` apuntando a ella) y los mismos `secrets/`, ejecuta `python3 -I servidor/scripts/make_dashboard_props.py`
   y compila: genera `android/dashboard.properties` con las URL y el token.

Para probar sin tocar nada: `compra_sync.py --dry-run`, `drive_watch.py --dry-run`, `shifter_sync.py … --dry-run`,
`menu_sync.py … --dry-run`, `rffm_sync.py --print` o `--dry-run`.

## Seguridad

- **Home Assistant solo en 127.0.0.1**, con la red del host y sin publicar puertos; el cortafuegos no abre el 8123.
  Nada de HA se publica: el marco solo ve `compra.json` y las operaciones concretas de `tareas_api.py`.
- **Rutas secretas + token**: nginx sirve `compra.json` (solo lectura) y `/tareas/` en una ruta aleatoria, sin
  registro de accesos; además, cada petición a `/tareas/` lleva la cabecera `X-Marco` con el token del marco
  (comparado en tiempo constante). Cuerpo máximo de 4 KB en nginx y 2 KB en el servicio.
- **PIN de padres** para aprobar o rechazar canjes: lo comprueba el servidor, no el marco; tras 5 fallos seguidos
  (`tareas.pin_fallos`) se bloquea 15 minutos (`tareas.pin_bloqueo_min`).
- **Validación de órdenes**: consigna dentro de su rango y de medio en medio grado, solo los modos configurados, solo
  sacar/recoger/parar el toldo y solo los reproductores de Alexa Media Player configurados; las tareas solo sobre las
  de hoy y del niño correspondiente.
- **Sandbox de `familia-tareas`**: `DynamicUser`, `LoadCredential` para los secretos y la configuración,
  `ProtectSystem=strict`, `ProtectHome`, `PrivateTmp`, `PrivateDevices`, `NoNewPrivileges` y red limitada a
  `localhost` (`IPAddressAllow=localhost`, `IPAddressDeny=any`).
- **Google**: la clave privada de la cuenta de servicio pasa a `openssl` por un descriptor, nunca a disco ni a la
  línea de comandos. Drive en solo lectura. Los scripts solo tocan eventos creados por ellos (prefijo del ID y
  propiedad privada `origen`), nunca los añadidos a mano.
- Las direcciones iCal secretas y el token del marco quedan dentro del APK: trátalo como un secreto.

## Limitaciones conocidas

- La **RFFM** no tiene API: `rffm_sync.py` lee el `__NEXT_DATA__` de su web y dejará de funcionar si cambia la página
  (avisa con un error claro). Solo sirve para competiciones de la RFFM.
- Los nombres de las entidades del **coche** (`<prefijo>_nivel_de_bateria`, `_autonomia`…) son los de una integración
  concreta con HA en castellano; con otra integración hay que adaptar `compra_export.py`.
- El **menú** depende de que Claude Code interprete bien el PDF; la comprobación es estricta y, si algo no cuadra, no
  se carga nada (y avisa en Todoist). Pensado para un menú general y, como mucho, uno alternativo.
- **Shifter**: solo se leen los tipos de turno y los días; las notas de cada día no se exportan.
- Google solo admite 11 colores por evento: el calendario usa el más parecido; el marco pinta el color exacto.
- `tareas_api.py` guarda los fallos del PIN en memoria: un reinicio del servicio los pone a cero.
- `compra_sync.py` compara por nombre normalizado (sin tildes ni mayúsculas): dos productos que solo se diferencian
  en eso son el mismo.
- `familia-compra.service` y `familia-drive.service` corren como root en el ejemplo (Claude Code usa la sesión de
  `/root`); con un usuario propio, ajusta `User=`, `HOME` y los permisos de `/srv/familia`.
