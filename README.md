# Vehicle Session Lab

[Español](README.es.md) · [![CI](https://github.com/navalcode/vehicle-session-lab/actions/workflows/checks.yml/badge.svg)](https://github.com/navalcode/vehicle-session-lab/actions/workflows/checks.yml)

An original Kotlin/JVM lab for inspecting lifecycle ownership at an Automotive integration boundary. A fictional callback gateway emits read-only vehicle metrics. The consumer handles connection deadlines, bounded reconnection, late callbacks, invalid values and per-metric freshness.

**No vehicle, Android Car service, account or API key is required.** This is a host-side simulation with deliberately invented data and ranges. It does not control a vehicle.

## Run it

Requires JDK 17. The Gradle 8.14.3 wrapper includes the distribution SHA-256. Kotlin 2.2.10, Coroutines 1.10.2 and JUnit 4.13.2 are pinned.

```sh
./gradlew test
./gradlew run
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

## Verification and limits

Local JDK 17 validation: **20 JVM tests and the executable demo pass**. Tests cover real behavior rather than Android permission or vehicle compatibility. [CI passed on GitHub](https://github.com/navalcode/vehicle-session-lab/actions/runs/40877568376), including all tests and the executable demo.

This is **not an Android Car adapter**. There is no CarPropertyManager/VHAL integration, area mapping, Android lifecycle component, OEM permission, Android Auto projection, hardware validation or functional-safety claim. Freshness measures local receipt time, not the age of a vehicle sensor measurement. No command writes to a vehicle.

A real adapter would map platform lifecycle, property availability, areas, source timestamps and permissions explicitly, then require its own platform and hardware tests. Relevant official APIs are [Car lifecycle](https://developer.android.com/reference/android/car/Car) and [CarPropertyManager](https://developer.android.com/reference/android/car/hardware/property/CarPropertyManager). The lab contract is intentionally smaller and does not promise API equivalence.

## License

Original source and documentation: [MIT](LICENSE), Manuel Naval, 2026. Dependencies and Gradle wrapper retain their own licenses; see [third-party notices](THIRD_PARTY.md).
