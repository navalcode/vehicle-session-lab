# Vehicle Session Lab

[English](README.md) · [![CI](https://github.com/navalcode/vehicle-session-lab/actions/workflows/checks.yml/badge.svg)](https://github.com/navalcode/vehicle-session-lab/actions/workflows/checks.yml)

Laboratorio Kotlin/JVM original para observar la propiedad de recursos en una frontera de integración Automotive. Un proveedor ficticio entrega métricas por callbacks. La sesión gestiona plazos de conexión, reconexiones acotadas, callbacks tardíos, valores inválidos y antigüedad por métrica.

**No necesita vehículo, servicio Android Car, cuenta o clave.** Los datos y rangos son inventados. No controla un vehículo.

## Ejecutar

JDK 17; wrapper Gradle 8.14.3 con SHA-256, Kotlin 2.2.10, Coroutines 1.10.2 y JUnit 4.13.2 fijados.

```sh
./gradlew test
./gradlew run
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

## Evidencia y límites

Validación local JDK 17: **20 tests JVM y demo ejecutable correctos**. [CI correcta en GitHub](https://github.com/navalcode/vehicle-session-lab/actions/runs/40877568376), incluidos los tests y la demo ejecutable.

No implementa CarPropertyManager/VHAL, áreas, componente de ciclo de vida Android, permisos OEM, Android Auto, validación de hardware o seguridad funcional. La antigüedad usa el reloj monotónico de recepción local, no un timestamp del sensor. Los rangos son ejemplos y no especificaciones de vehículos.

Una integración real exigiría adaptar permisos, disponibilidad, áreas, timestamps y ciclo de vida de plataforma, con pruebas propias de plataforma/hardware. Referencias oficiales: [Car](https://developer.android.com/reference/android/car/Car) y [CarPropertyManager](https://developer.android.com/reference/android/car/hardware/property/CarPropertyManager). Este contrato no promete equivalencia con esas APIs.

## Licencia

Código y documentación originales: [MIT](LICENSE), Manuel Naval, 2026. Dependencias y wrapper conservan sus licencias; [avisos de terceros](THIRD_PARTY.md).
