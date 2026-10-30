# Vehicle Session Lab

[Español](README.es.md) · [![CI](https://github.com/navalcode/vehicle-session-lab/actions/workflows/checks.yml/badge.svg)](https://github.com/navalcode/vehicle-session-lab/actions/workflows/checks.yml)

A Kotlin/JVM simulation of callback-based vehicle data. Follow a connection through startup, invalid readings, disconnect, retry and cleanup. Each subscription belongs to an observer lifetime; callbacks from a replaced connection cannot change the active session.

The gateway, data and ranges are fictional. Run it on a host JDK without a vehicle, account or Android Car service. The program observes simulated values and sends no vehicle-control commands.

## Run it

Requires JDK 17. The Gradle 8.14.3 wrapper includes the distribution SHA-256. Kotlin 2.2.10, Coroutines 1.10.2 and JUnit 4.13.2 are pinned.

```sh
./gradlew test
./gradlew -q run
./gradlew -q run --args='--language es'
./gradlew -q run --args='--help'
./gradlew -q run --args='--language es --help'
```

The demo opens a simulated connection, receives battery/cabin/speed readings, rejects an invalid speed, reconnects after a disconnect and marks quiet metrics stale. It stops after 1.8 seconds and checks that every opened subscription was closed. StateFlow is conflated: the printed snapshots are observable state, not a complete event journal.

## Follow the code

[`VehicleSession.kt`](src/main/kotlin/dev/navalcode/vehicle/VehicleSession.kt) defines the gateway, typed events, state and resource owner. [`Demo.kt`](src/main/kotlin/dev/navalcode/vehicle/Demo.kt) implements the original scripted source. [Tests](src/test/kotlin/dev/navalcode/vehicle/VehicleSessionTest.kt) use virtual time and retain old callbacks to exercise session replacement.

| Situation | Result |
|---|---|
| Owner starts twice | One subscription; start is idempotent. |
| Ready is absent or is not the first event | Close the subscription, clear readings and use the bounded retry budget. |
| Source disconnects | Clear live values; retry up to two additional connections. A manual start resets the exhausted budget. |
| Owner stops or is cancelled | Close the lease and clear state; no automatic retry. Parent deadlines remain parent cancellation. |
| Old callback arrives after replacement | Its closed channel rejects it; it cannot change the current session. |
| Reading is non-finite, out of range or has a repeated/regressive sequence | Retain the accepted value and count the rejection. Sequences are per metric and connection. |
| Metric becomes unavailable | Store an explicit null sample; a later valid reading can recover. |
| One metric stops changing while others continue | Mark it stale using local monotonic receipt age. |
| Callback queue exceeds 64 pending events | Fail that connection explicitly and recover within the same budget. |

Each acquired lease is closed in `finally`. An adapter must provide a nonblocking `open`, return an idempotent/nonblocking cleanup handle and own cleanup if it throws before returning a handle. Calling start/stop belongs to one owner dispatcher; callbacks can come from other threads. [Architecture and limits](docs/ARCHITECTURE.md) record the contract and tradeoffs.

## Checks and platform limits

**23 JVM tests** cover session behavior, presentation and option validation. The executable demo closes every opened subscription; `--help` returns before opening one. [Verification](docs/VERIFICATION.md) records actual EN/ES runs, error exits, artifact commands and current CI. [DESIGN.md](DESIGN.md) explains the terminal output.

This host simulation includes no Android Car adapter, CarPropertyManager/VHAL integration, area mapping, OEM permissions or Android Auto projection. Freshness uses local receipt time and does not establish the age or accuracy of a sensor measurement. A real adapter needs its own platform lifecycle, property mapping, source timestamps, permissions and hardware tests. The [architecture](docs/ARCHITECTURE.md) describes those boundaries.

## License

Original source and documentation: [MIT](LICENSE), Manuel Naval, 2026. Dependencies and Gradle wrapper retain their own licenses; see [third-party notices](THIRD_PARTY.md).
