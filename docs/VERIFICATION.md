# Verification

Checked on 30 October 2026 on macOS with JDK 17.

```sh
./gradlew test installDist --rerun-tasks
build/install/vehicle-session-lab/bin/vehicle-session-lab
build/install/vehicle-session-lab/bin/vehicle-session-lab --language es
build/install/vehicle-session-lab/bin/vehicle-session-lab --language es --help
```

All 23 JVM tests pass: 20 session behavior cases and three presentation/options cases. The installed executable runs in English and Spanish, opens two subscriptions and closes both. StateFlow can conflate intermediate states, so transcript timing and line count are not a golden event log.

Help exits 0 without starting a session. An unknown option, unsupported language or repeated language option exits 2 before simulation and gives an actionable option/help message. Argument validation uses the English fallback; report labels and help support English and Spanish. Phase and reason identifiers are unchanged.

The [checks workflow](https://github.com/navalcode/vehicle-session-lab/actions/workflows/checks.yml) runs JVM tests and the scripted demo on JDK 17. Its run history is the hosted evidence. This project has no Android build, Car service adapter, emulator, physical vehicle or OEM permission test. The [architecture](ARCHITECTURE.md) describes what a platform adapter would still need.
