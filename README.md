# Marco familiar · dashboard 🖼️🏠

Un marco de fotos digital de unos 80 € convertido en **el centro de la casa**: calendario de toda la familia con los turnos de trabajo, el menú del cole y los partidos de los niños; lista de la compra que se llena por voz con Alexa; tareas de los niños con estrellas y recompensas; precio de la luz, batería del coche, calefacción, toldo y música. Colgado en la pared, sin la app del fabricante y sin suscripciones.

Es una evolución de **[eusonlito/Planify-Dashboard](https://github.com/eusonlito/Planify-Dashboard)** (Lito), que a su vez partió de **[wesbos/photo-frame-dashboard](https://github.com/wesbos/photo-frame-dashboard)**. Lo hicimos igual que ellos: **con un agente (Claude Code) siguiendo la guía de Lito** y añadiendo poco a poco lo que nuestra familia necesitaba. Este repositorio es, como el suyo, **una guía para agentes y personas** que quieran montar el suyo: qué hicimos, en qué orden y qué aprendimos.

> Todos los datos de las capturas son de ejemplo (la app tiene un modo demostración que cambia nombres, equipos, lugares y turnos). Ninguna dirección, token ni dato real de la familia está en el repositorio.

<p align="center"><img src="docs/capturas/1-hoy.png" width="90%" alt="Portada: hora y tiempo, lo próximo, la semana con turnos, citas y menús, y los resúmenes de niños, compra y luz" /></p>

---

## Índice

1. [Qué añade respecto a Planify-Dashboard](#qué-añade)
2. [Capturas](#capturas)
3. [Arquitectura](#arquitectura)
4. [Para agentes: empieza aquí](#para-agentes-empieza-aquí)
5. [Las fases, paso a paso](#las-fases)
6. [Lo que aprendimos](#lo-que-aprendimos)
7. [Seguridad y privacidad](#seguridad-y-privacidad)
8. [Estructura, licencia y créditos](#estructura-licencia-y-créditos)

---

## Qué añade

La app de Lito lee calendarios, rutinas y tareas de su propio backend (Planify). Nosotros no usamos Planify: **todo sale de servicios que la familia ya usaba**, y Home Assistant hace de pegamento.

| Qué | De dónde | Cómo llega al marco |
|---|---|---|
| Citas de la familia, fútbol, Barça… | Google Calendar (direcciones iCal secretas) | La app lee el iCal directamente (`biweekly`, con RRULE, EXDATE y RECURRENCE-ID) |
| Turnos de trabajo variables | App **Shifter** («Calendario de Turnos»): se exporta el `.Shifter` (SQLite) a una carpeta de Drive | Script en el servidor → Google Calendar, con el color de cada tipo de turno |
| Menú del comedor escolar | PDF mensual en la misma carpeta de Drive | Script → JSON → Google Calendar (un evento por día, con el menú alternativo de un niño, si lo tiene) |
| Partidos de fútbol de los niños | Web de la federación (RFFM: `__NEXT_DATA__` trae todas las jornadas) | Script → Google Calendar |
| Precio de la luz (PVPC) | API pública de REE, sin clave | La app directamente; con el precio de mañana (≈20:30) enseña las próximas 24 h |
| Lista de la compra | **Bring!** + lista de la compra de **Alexa** (por voz) | Espejo Alexa ⇄ Bring! en el servidor; `compra.json` en una ruta secreta |
| Tareas de casa | **Todoist** (proyecto compartido) + «Alexa, añade… a mis tareas» | Home Assistant → `compra.json` |
| Tareas de los niños, estrellas y recompensas | **TaskMate** (integración de Home Assistant) | Un pequeño servicio en el servidor: ver, marcar «hecha por X», deshacer, pedir canje y aprobarlo con PIN de padres |
| Coche eléctrico (batería, autonomía, carga y potencia, ruedas) | Integración de Home Assistant del fabricante | `compra.json` |
| Calefacción, toldo, música de los Echo | Home Assistant (Overkiz/Somfy, Alexa Media Player) | `compra.json` + órdenes por el mismo servicio |

Y en la pantalla:

- **Hoy:** bloque «cielo» (cambia de color con el sol) con la hora, la fecha y el tiempo de 6 días con su % de lluvia; **lo próximo** (o **ahora**, mientras dura) con cuenta atrás; **la semana** con el turno de cada día en una pastilla, todas las citas y el menú del cole; abajo, niños, compra y luz/coche (cada bloque lleva a su pestaña).
- **Calendario de pared:** unos 3 meses (13 semanas) de lunes a domingo en vertical, con el mes en cada semana, el día 1 resaltado y el tiempo de cada día.
- **Casa y energía:** luz de las próximas 24 h y **mejores horas** para cargar el coche o poner el lavavajillas; calefacción con consigna y modos; coche (mientras carga, la batería en azul parpadea y enseña la potencia en kW); toldo; música.
- **Tareas:** tareas de los niños (cada uno marca las suyas), recompensas con canje y PIN, tareas de casa y la compra con **habituales** (los 3 productos más pedidos que faltan, a un toque).
- Tocar cualquier cita abre un aviso con el título entero, lugar y descripción.

---

## Capturas

| | |
|---|---|
| ![Hoy](docs/capturas/1-hoy.png) **Hoy** | ![Calendario](docs/capturas/2-calendario.png) **Calendario de pared** |
| ![Casa y energía](docs/capturas/4-casa-y-energia.png) **Casa y energía** | ![Tareas](docs/capturas/5-tareas.png) **Tareas, recompensas y compra** |
| ![Tiempo](docs/capturas/3-tiempo.png) **Tiempo** (de Lito) | ![Aviso de una cita](docs/capturas/7-aviso-cita.png) **Aviso de una cita** |

---

## Arquitectura

```
 Móviles / voz                       Servidor (VPS o mini PC)                         Marco (Android 10)
 ─────────────                       ────────────────────────                         ──────────────────
 Alexa (compra, tareas) ─┐           Home Assistant (solo 127.0.0.1)
 Bring!, Todoist ────────┼──────▶     ├─ Bring!, Alexa Devices, Todoist, PVPC
 App del coche ──────────┘            ├─ coche, Overkiz (Somfy), Alexa Media Player
                                      └─ TaskMate (niños, tareas, estrellas)
                                             │
 Drive «Marco/» ─────────▶  drive_watch ─────┤                 compra.json (ruta secreta) ─────▶  Lista, coche, casa,
  (.Shifter, PDF menú)      shifter_sync     │   compra_export ─┘                                  habituales
                            menu_sync ───────┼──▶ Google Calendar ── iCal secreto ─────────────▶  Calendario, Hoy
 Web de la federación ───▶  rffm_sync ───────┘
                                             compra_sync (Alexa ⇄ Bring!, cuenta habituales)
                                             tareas_api (token + PIN) ◀────── órdenes ────────  Tareas, canjes, casa
 REE (precio de la luz) ───────────────────────────────────────────────────────────────────▶  Luz
```

- **Home Assistant nunca se expone a Internet.** El marco solo ve un JSON estático en una ruta secreta y un servicio mínimo que acepta un puñado de órdenes, con token y, para lo delicado, PIN.
- **Sin dependencias raras:** los scripts son Python de la biblioteca estándar (más `openssl` para firmar el JWT de la cuenta de servicio de Google).
- Lo que va por la nube (Bring!, Todoist, Alexa, coche, Somfy) funciona con Home Assistant en un VPS; lo local (cargador del coche, Roomba…) necesitará Home Assistant en casa.

Más detalle: [`servidor/README.md`](servidor/README.md) y [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) (de Lito, la app).

---

## Para agentes: empieza aquí

Las reglas de Lito siguen valiendo y son innegociables ([docs/README-original-Lito.md](docs/README-original-Lito.md#reglas)): **identifica el marco antes de tocar nada, copia de seguridad verificada de la eMMC antes de escribir, permiso explícito para cada cambio en el dispositivo, y para en la primera diferencia.** Añadimos:

1. **Los secretos, fuera del repositorio:** direcciones iCal, tokens, claves de la cuenta de servicio y PIN en ficheros `chmod 600` ignorados por git. La app recibe su configuración por `android/dashboard.properties` (generado, nunca subido).
2. **Separa la familia de cualquier otra cosa** (cuentas de trabajo, otros proyectos del servidor): carpeta, usuarios y servicios propios.
3. **Home Assistant solo en 127.0.0.1.** Compruébalo tras cada reinicio (`ss -ltn | grep 8123`).
4. **Prueba en el emulador antes que en el marco** (un AVD de 1920×1200 con Android 10) y enseña capturas antes de instalar.
5. **Nunca borres datos de los servicios de la familia** (historial de Alexa, listas, calendarios) sin preguntar.

---

## Las fases

Las fases 1–10 de Lito preparan el marco (identificarlo, copia de la eMMC, compilar, quiosco, cortafuegos, ADB por Wi-Fi, arranque…). Síguelas tal cual: [docs/README-original-Lito.md](docs/README-original-Lito.md#las-fases-paso-a-paso) y [docs/SETUP.md](docs/SETUP.md). Después:

### Fase 11: calendarios de la familia (sin Planify)
1. Crea en Google Calendar los calendarios: familia, turnos, menú del cole, fútbol de los niños (y, si quieres, uno público como el de tu equipo).
2. Copia su **dirección secreta en formato iCal** a `secrets/calendarios.properties` (plantilla en [`servidor/config/calendarios.properties.example`](servidor/config/calendarios.properties.example)) y genera `android/dashboard.properties`. Con calendarios iCal la app no pide sesión y cambia a las pantallas de la familia.
3. Para escribir en Google Calendar desde el servidor, crea una **cuenta de servicio** y comparte con ella los calendarios (y la carpeta de Drive) con permiso de edición o lectura.

### Fase 12: servidor y Home Assistant
1. Home Assistant en Docker con `network_mode: host`, escuchando solo en 127.0.0.1 ([`servidor/despliegue/`](servidor/despliegue/)).
2. Integraciones: PVPC, Bring!, Alexa Devices, Todoist, la del coche, Overkiz, Alexa Media Player (HACS), TaskMate (HACS).
3. Scripts y servicios de [`servidor/`](servidor/) con tu `config/familia.json`.
4. nginx: `compra.json` y `/tareas/` en una **ruta secreta**, sin registro de accesos.

### Fase 13: turnos y menús automáticos
Una carpeta de Drive con subcarpetas `Turnos` y `Menús`: quien trabaja a turnos exporta el `.Shifter` y quien recibe el PDF del comedor lo deja ahí. Cada 15 minutos el servidor los lee, los pasa al calendario y avisa en Todoist. El menú se convierte con un lector (o con `claude -p` si el formato cambia).

### Fase 14: tareas y recompensas de los niños
TaskMate con los niños, tareas («el primero que la haga» o «cada uno la suya»), estrellas y recompensas. El marco escribe a través de `tareas_api` (token del marco), y los canjes se aprueban en el propio marco con un **PIN de padres** (bloqueo tras 5 fallos).

### Fase 15: diseño
Rehicimos la portada con lo que más mira la familia: la semana entera, los turnos y los menús de un vistazo. Probamos siempre en el emulador, con capturas, antes de instalar.

---

## Lo que aprendimos

Además de lo que cuenta Lito ([docs/README-original-Lito.md](docs/README-original-Lito.md#lo-que-aprendimos)):

- **Los cambios bruscos de brillo apagaban el marco.** Con el registro persistente (`persist.logd.logpersistd=logcatd`) vimos que el registro se cortaba en seco justo al pasar del reloj nocturno al día (y al revés) o al terminar de arrancar con el brillo a tope. Solución: brillo máximo 180/255 y cambios graduales en 3 s. Si tu marco «se apaga solo», activa el registro persistente antes de nada.
- **El reloj nocturno a 1/255 parece un marco apagado.** 10/255 se lee y no deslumbra.
- **`ro.hxzl.power_off_not_dialog=true`** (en `/vendor/build.prop` de este firmware): una pulsación algo larga del botón apaga sin preguntar. Lo pusimos a `false` (overlayfs tras `adb remount`).
- **Tras un corte de luz justo después de `adb install`**, el marco se quedó en el logo con dos copias de la APK. Desde entonces: `adb install -r` → `adb shell sync` → comprobar `pm path` (una sola copia).
- **El servicio de apagado (accesibilidad) dejaba de funcionar tras reiniciar** («Ignoring non-encryption-aware service»): `android:directBootAware="true"` y reescribir el ajuste en el script de arranque.
- **Espejo Alexa ⇄ Bring!:** si pasas lo de Alexa a Bring! y lo borras de Alexa, «Alexa, quita…» deja de funcionar. Mejor que Alexa sea una copia exacta de Bring! y que el script recuerde qué estaba sincronizado para distinguir «nuevo» de «quitado».
- **El historial de Alexa está lleno de bromas y errores de reconocimiento.** Para los «habituales», cuenta solo lo que se pide en días distintos y ten una lista de ignorados.
- **Home Assistant en Docker volvía a escuchar en 0.0.0.0** tras reiniciar: fija `server_host` en `.storage/http`.
- **El toldo Somfy llega invertido** a Home Assistant: «abierto 100 %» es recogido. Invierte posición y órdenes.
- **Alexa Media Player sirve sin Spotify Premium** (pausa, siguiente, volumen, qué suena), con 5–15 s de retraso por la nube de Amazon. En el marco, pinta el cambio al pulsar y deja que HA confirme.
- **Los «calendarios familiares» grandes (Dragon Touch, Skylight…) suelen ser tablets Android 8.1 cerradas**, sin ADB. Para ir a más pulgadas, busca la misma plataforma que el XORO (Allwinner A133, Android 10, root de fábrica), como el LOOFII C-3 de 21,5" de la lista de Wes.
- **El marco no tiene pila de reloj:** arranca con la fecha de fábrica hasta que llega la hora por NTP; nada que dependa de la hora debe fiarse antes.

---

## Seguridad y privacidad

- Nada de este repositorio contiene datos reales: nombres, equipos, turnos y lugares de las capturas son inventados (modo demostración `demo.replace.N` en `dashboard.properties`; ver [`Demo.kt`](android/app/src/main/java/com/lito/planify/dashboard/data/Demo.kt)).
- El marco no habla con Home Assistant: solo lee un JSON en una ruta secreta y manda órdenes concretas a un servicio con token. Lo delicado (aprobar canjes) pide PIN, que comprueba el servidor.
- El cortafuegos del marco (de Lito) limita qué puede salir del dispositivo; ADB por Wi-Fi solo desde la IP de tu ordenador.

---

## Contribuir

Las correcciones e ideas son bienvenidas: mira [CONTRIBUTING.md](CONTRIBUTING.md) antes de abrir una incidencia o una
propuesta de cambio. Los fallos de seguridad, en privado: [SECURITY.md](SECURITY.md).

## Estructura, licencia y créditos

```
android/          App del marco (Kotlin + Jetpack Compose), basada en la de Planify-Dashboard
scripts/          Scripts del marco de Lito (instalación, quiosco, cortafuegos, arranque)
servidor/         Scripts y despliegue del servidor (Home Assistant, Google, Drive, compra, tareas)
docs/             Documentación de Lito (marco, instalación, arquitectura) + capturas
```

- **Licencia:** MIT, como el proyecto original ([LICENSE](LICENSE)); las de terceros, en [`licenses/`](licenses/) y [`third_party/`](third_party/).
- **Créditos:** [Lito (eusonlito)](https://github.com/eusonlito/Planify-Dashboard), por el marco convertido, la app y la guía; [Wes Bos](https://github.com/wesbos/photo-frame-dashboard), por la idea y la lista de marcos; y los proyectos que usamos: Home Assistant, TaskMate, Alexa Media Player, biweekly, Open-Meteo, REE.
- Hecho con [Claude Code](https://claude.com/claude-code), igual que el original.
