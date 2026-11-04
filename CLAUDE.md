# QDiag — notes for Claude

Android app (Kotlin, Jetpack Compose) that talks to the owner's Wi-Fi ELM327 adapter (Audew
"ELM327 Version II" clone) plugged into a 2008 Nissan Qashqai+2 J10, 2.0 dCi 150 (M9R), 4WD.
Phone: Nothing Phone (4a) Pro. Sideloaded; no store.

## Working with the owner
- The owner does not do manual steps (GitHub UI, git commands, CI buttons). Do everything
  yourself; if an action truly needs their authorization, send them the exact link instead of
  instructions.
- They install and update from their phone: the in-app updater follows published releases.
- The UI is French by default (English in Settings): every string goes through `tr(en, fr)`,
  core texts through `Text(en, fr)`.

## Releasing
- Bump `VERSION` (e.g. `0.1.0` → `0.1.1`), add a section to `CHANGELOG.md`, push. CI tags the
  commit `vX.Y.Z` and publishes the GitHub release with `qdiag.apk` + `version.json`.
- Plain pushes only build a test APK (Actions artifact); the app never offers those.
- Release when a user-facing change is ready, and say which version went out.

## Building and testing
- This sandbox can't reach Google's Maven/SDK hosts, so Android builds only run in GitHub
  Actions (`.github/workflows/build.yml`, which also runs `:core:test`). Push, then read the
  run; the "Show compiler errors" step prints Kotlin errors compactly.
- `:core` is pure Kotlin (ELM327 parsing, OBD decoding, fault-code table, health checks): test it
  locally on the JVM with a throwaway Gradle build that includes `core/` and uses the Maven
  Central mirror `https://maven-central.storage-download.googleapis.com/maven2`, with system
  gradle (`gradle -q --no-daemon :core:test`). Tests use the fake VIN `SJNFAAJ10U1234567`.

## Diagnostics (the developer has no car)
- The app posts JSON lines to ntfy.sh (`diag/Diag.kt`): ELM exchanges, init, supported PIDs,
  codes, a live summary every 5 s, the read-only discovery survey (Console → "Relevé complet"),
  errors and crashes. Lines queue on disk while the phone is on the adapter's Wi-Fi.
- Read them: write the time into `.github/diag.trigger`, push, read the Diagnostics run's log
  (topic kept 12 h). Unknown PIDs, manufacturer codes (P1xxx) and the KWP/UDS replies there
  are what to add to `core/` next.

## Car logbook readings
- `diag/Reports.kt` posts one compact JSON per code reading / drive / survey (kind `codes`,
  `drive`, `survey`, ≤ 3.8 KB) to `ntfy.sh/qdiag-car-<first 24 hex of SHA-256("qdiag-car:" + VIN)>`;
  before the VIN is known, to the diagnostics topic as event `report`. A scheduled Claude task
  (owner's account) polls it and updates the private "Carnet Qashqai+2" artifact. Never write the
  VIN or the derived topic name in this repo.

## Privacy and safety (public repo)
- Never commit the car's VIN, plate, invoices, CT reports or real logs. Diagnostics mask the VIN
  serial (`Discovery.mask`); keep it that way for anything new that's logged.
- Nothing the app sends by itself writes to the car. The only write is mode 04 (clear codes),
  behind a confirmation on the Codes screen. The console blocks write/session/routine/security
  services unless the owner turns on Expert console; discovery probes are read-only.
- Health thresholds are indicative and say so in the UI; part numbers are Nissan OEM references.
