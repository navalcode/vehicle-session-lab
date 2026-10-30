# Vehicle Session Lab

[English](README.md) · [![CI](https://github.com/navalcode/vehicle-session-lab/actions/workflows/checks.yml/badge.svg)](https://github.com/navalcode/vehicle-session-lab/actions/workflows/checks.yml)

Simulación Kotlin/JVM de datos de vehículo recibidos por callbacks. Permite seguir la conexión inicial, las lecturas inválidas, la desconexión, el reintento y la liberación de recursos. Cada suscripción dura lo que su observador; los callbacks de una conexión sustituida no pueden modificar la sesión activa.

El proveedor, los datos y los rangos son ficticios. Se ejecuta en un JDK del equipo, sin vehículo, cuenta ni servicio Android Car. El programa observa valores simulados y no envía órdenes de control.

## Ejecutar

JDK 17; wrapper Gradle 8.14.3 con SHA-256, Kotlin 2.2.10, Coroutines 1.10.2 y JUnit 4.13.2 fijados.

```sh
./gradlew test
./gradlew -q run
./gradlew -q run --args='--language es'
./gradlew -q run --args='--help'
./gradlew -q run --args='--language es --help'
```

La demo conecta, recibe batería/temperatura/velocidad, rechaza una velocidad inválida, reconecta tras una desconexión y marca datos obsoletos. Se detiene a los 1,8 segundos y comprueba que cada suscripción abierta se cerró. StateFlow combina estados rápidos; la salida no es un diario completo de eventos.

## Comportamiento

- Inicio idempotente y observación de solo lectura; cada conexión tiene su propia cola y suscripción.
- Ready debe ser el primer evento y llegar dentro del plazo. Si no llega o el protocolo falla, se cierra y se reintenta.
- Desconectar vacía datos en vivo. Hay dos reconexiones adicionales como máximo; un inicio manual renueva el presupuesto agotado.
- Parada y cancelación del propietario cierran la suscripción y limpian estado. Un deadline del padre sigue siendo cancelación del padre, no un fallo reintentable de la fuente.
- Los callbacks tardíos quedan en un canal cerrado y no modifican la sesión nueva.
- Secuencias repetidas/regresivas, valores no finitos o fuera de rango no sustituyen lecturas aceptadas. La secuencia pertenece a cada métrica/conexión.
- Un valor nulo representa indisponibilidad explícita y puede recuperarse. Una métrica que deja de actualizarse se marca obsoleta aunque otras sigan llegando.
- La cola admite 64 eventos pendientes. Saturarla falla la conexión de forma explícita y usa el mismo presupuesto de recuperación.

[`VehicleSession.kt`](src/main/kotlin/dev/navalcode/vehicle/VehicleSession.kt) contiene contratos y coordinador; [`Demo.kt`](src/main/kotlin/dev/navalcode/vehicle/Demo.kt), el simulador; los [tests](src/test/kotlin/dev/navalcode/vehicle/VehicleSessionTest.kt) usan tiempo virtual y callbacks antiguos retenidos. [Arquitectura](docs/ARCHITECTURE.md) detalla las decisiones.

El adaptador debe abrir sin bloquear, devolver un cierre idempotente/no bloqueante y limpiar sus propios recursos si falla antes de devolverlo. Start/stop pertenecen a un dispatcher propietario; los callbacks pueden venir de otros hilos. Cada handle adquirido se cierra en `finally`.

## Comprobaciones y límites de plataforma

**23 tests JVM** cubren la sesión, la presentación y la validación de opciones. La demo cierra todas las suscripciones que abre; `--help` termina antes de abrir ninguna. [Verificación](docs/VERIFICATION.md), en inglés, recoge ejecuciones EN/ES reales, errores, comandos de artefactos y CI actual. [DESIGN.md](DESIGN.md) explica la salida de terminal.

La simulación no incluye adaptador Android Car, integración CarPropertyManager/VHAL, áreas, permisos OEM ni proyección Android Auto. La antigüedad se mide desde la recepción local y no acredita la edad o exactitud de la medición del sensor. Un adaptador real necesita ciclo de vida de plataforma, mapeo de propiedades, timestamps de origen, permisos y pruebas de hardware propios. [Arquitectura](docs/ARCHITECTURE.md) explica esos límites.

## Licencia

Código y documentación originales: [MIT](LICENSE), Manuel Naval, 2026. Dependencias y wrapper conservan sus licencias; [avisos de terceros](THIRD_PARTY.md).
