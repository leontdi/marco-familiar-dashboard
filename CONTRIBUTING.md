# Cómo contribuir

¡Gracias por querer mejorar el marco familiar! Es un proyecto personal que se mantiene en el tiempo libre, así que las
respuestas pueden tardar unos días.

## Antes de nada: privacidad

Este proyecto maneja calendarios, turnos, menús y tareas de una familia real. Por favor:

- **No pegues nunca** URLs secretas de calendarios iCal, tokens, contraseñas, PIN ni ficheros de `secrets/`, ni en
  incidencias ni en propuestas de cambio.
- Si compartes capturas, usa el **modo demo** (`demo.ficticio=true` en `dashboard.properties`, solo en el emulador) o
  difumina nombres, horarios y lugares.
- Si encuentras un problema de seguridad, **no abras una incidencia pública**: mira [SECURITY.md](SECURITY.md).

## Incidencias

- **Algo no funciona:** usa la plantilla «Algo no funciona» y di qué marco o tablet usas (modelo, versión de Android),
  qué esperabas y qué pasó. Si puedes, añade el registro (`adb logcat`) sin datos personales.
- **Ideas:** usa la plantilla «Idea o mejora». Cuenta para qué te serviría en tu casa; ayuda a decidir.

## Propuestas de cambio (pull requests)

1. Haz una copia (*fork*) y trabaja en una rama propia.
2. Cambios pequeños y de una sola cosa por propuesta: se revisan mucho mejor.
3. Sigue el estilo del código que tocas: Kotlin + Jetpack Compose en `android/`, Python de la biblioteca estándar en
   `servidor/scripts/`. Comentarios y textos de la app **en castellano**.
4. Antes de enviarla:
   ```bash
   cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug
   ```
   y, si tocas la pantalla, pruébala en el emulador ([docs/ANDROID.md](docs/ANDROID.md)) y adjunta capturas en modo demo.
5. Nada de secretos ni datos personales en el código, los ejemplos o el historial.

Toda propuesta se revisa a mano antes de entrar: el código acaba funcionando en el salón de una casa.

## Licencia

Al contribuir aceptas que tu aportación se publique con la misma licencia del proyecto (MIT, ver [LICENSE](LICENSE)).
Este proyecto parte de [Planify-Dashboard](https://github.com/eusonlito/Planify-Dashboard) de eusonlito; si tu idea
encaja mejor allí, considera proponérsela también.
