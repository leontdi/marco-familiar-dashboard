# Seguridad

Si encuentras un fallo de seguridad (por ejemplo, una forma de leer calendarios o tareas sin la ruta secreta y el token,
de saltarse el PIN de padres o de salir del modo quiosco), **no abras una incidencia pública**.

Avísame por privado con la opción **«Report a vulnerability»** de la pestaña *Security* de este repositorio. Cuenta qué
has encontrado y cómo reproducirlo, sin incluir datos reales de nadie.

Intentaré responder en unos días. Gracias por avisar con discreción.

## Recordatorios para quien instale el proyecto

- Las URLs iCal secretas, el token del marco y el PIN de padres van en `secrets/` y en la configuración del servidor,
  **nunca** en el repositorio.
- Home Assistant y los servicios del servidor escuchan solo en `127.0.0.1`; nginx expone únicamente rutas secretas que
  además exigen la cabecera `X-Marco`.
- El marco tiene ADB por Wi-Fi: déjalo solo en tu red de casa y cierra el puerto en el router.
