# Terminal presentation

The demo starts by identifying its fictional, read-only source and explaining that StateFlow can omit intermediate snapshots. Each snapshot shows the stable phase code, connection attempt and rejected-reading count. Metrics follow speed, battery and cabin-temperature order, with units and distinct labels for no reading, unavailable, current and stale. Missing values are never replaced by zero.

Phase and recovery-reason identifiers remain unchanged so the output can be compared with the session code. Human labels and help support `--language en|es`; option-validation diagnostics use English. Help exits before opening a subscription. The final line reports opened/closed subscriptions and the demo checks their equality.

Use `./gradlew -q run` to keep Gradle progress separate. The simulation stops after its scripted duration; its printed snapshots are not a complete event log or a machine telemetry protocol. Freshness describes local receipt age, with the threshold defined in the session contract. It does not establish a sensor’s physical measurement age.

[Verification](docs/VERIFICATION.md) records the session tests, executable/help/error cases and subscription closure. The terminal controls font, contrast and wrapping. The project has no vehicle connection or vehicle-control command.
