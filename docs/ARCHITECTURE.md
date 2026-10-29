# Session ownership and recovery

The lab isolates one question: how should an observer own a connection that can fail independently of its UI owner? It is a Kotlin/JVM simulation, not an Android application.

## Boundaries

`VehicleGateway.open(callback)` returns the resource lease. The adapter owns subscription mechanics; the consumer owns state, validation, retry budget and lifetime. Opening and closing must not block the owner dispatcher. If opening throws before returning a lease, the adapter must clean up any partial acquisition. The fictional gateway does not expose vehicle controls.

The session uses a structured child of the supplied scope. It does not launch global jobs. Start/stop run on the same owner dispatcher. Incoming callbacks may use another thread; a bounded thread-safe channel transfers them to that owner. Each connection has a different channel. Closing a connection first closes its channel, then releases its lease; even cleanup callbacks cannot write into a later connection. An epoch guard prevents a cancelled owner job from publishing over an immediate stop/start replacement.

StateFlow exposes current state through a read-only interface; it intentionally conflates intermediate values. Data classes use read-only map types. This is not a durable event log or a hostile-code isolation boundary.

## Failure policy

Ready must be the first event within 1000ms by default. The session owns that timeout and can recover from it. Cancellation of the supplied scope, including its deadline, propagates and clears state without reconnecting. Source disconnect, protocol failure, queue overflow or adapter exception closes the acquired lease before retrying. Adapter exception text is not published.

There are at most three connection attempts per start, with a fixed 250ms gap; the budget does not reset after one successful reading. This keeps the demo deterministic and prevents an unstable source from retrying forever. Manual start after exhaustion begins a new episode. A production policy would need product-specific backoff, jitter and lifecycle/connectivity decisions.

Live values are discarded on disconnect. Retaining them without an explicit stale/offline presentation would misrepresent current availability. Retry reason and attempt are observable; accepted reading/rejection counts belong to each connection.

## Data and time

Each metric has a fictional range and unit. Sequence numbers must be nonnegative and strictly increase for that metric within one connection. Null means explicitly unavailable; absence means no accepted reading yet. Invalid data does not refresh age or replace the last accepted sequence. No conversion from Android property IDs or source clock domains is attempted.

Freshness uses the monotonic local receipt clock. The session checks it on events and at half the stale threshold during silence, so idle expiry is observed within an additional half-threshold interval. Continuous updates of one metric do not keep another metric fresh. This does not prove the source measurement is recent, accurate or trustworthy.

A 64-event buffer bounds memory. Saturation invalidates the session rather than silently losing transitions. This conservative policy is useful for inspection; a real adapter might coalesce selected telemetry with an explicitly different contract.

## Platform work still required

An AAOS adapter would need supported SDK/API versions, lifecycle callback handling, per-property availability and permissions, area IDs, source timestamps, error callbacks and service loss. The official Car documentation calls for disconnecting the instance before its Context is released. Current CarPropertyManager documentation exposes subscribe/unsubscribe APIs and deprecates older registration APIs; API selection must be validated for the actual target.

Primary references checked on 29 October 2026: [Car](https://developer.android.com/reference/android/car/Car), [CarPropertyManager](https://developer.android.com/reference/android/car/hardware/property/CarPropertyManager). No platform implementation or permission validation is inferred from this host test suite.
