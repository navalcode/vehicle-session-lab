# Terminal presentation

This is a Kotlin/JVM callback-session simulation, not a vehicle dashboard. It inherits the terminal's font, color, contrast and line wrapping. There is no ANSI, mandatory color, animation or graphical dependency.

The opening text names fictional/read-only data and clarifies that StateFlow snapshots are conflated. Each snapshot begins with stable phase, attempt and rejected-reading count. Metrics follow a fixed speed/battery/cabin order with explicit units and separate labels for no reading, unavailable, current and stale. Values are not padded with fake zeroes. Recovery reasons retain stable technical codes. The close reports opened/closed subscriptions and checks equality.

`--language en|es` changes human labels. Default is English. `--help` exits before acquiring a simulated lease; unknown/missing/duplicate options fail before running. `./gradlew -q run` keeps build progress separate. This demo output is intended for people, not a new telemetry protocol or a complete event journal.

Verification preserves the session's20 lifecycle/recovery tests and adds presentation/option behavior checks. Real EN/ES runs close every subscription. There is no vehicle connection, hardware validation or control command.
