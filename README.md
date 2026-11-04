# QDiag

Diagnostics for a **Nissan Qashqai+2 (J10) 2.0 dCi** through a **Wi-Fi ELM327** OBD-II adapter,
on Android. French by default, English in Settings.

- **Codes** — reads stored, pending and permanent fault codes, explains each one for the
  M9R 2.0 dCi (likely causes, severity) and links the Nissan part numbers to check. Clears them
  on request, with a warning about the contrôle technique.
- **Live** — every value the engine ECU offers (boost, rail pressure, MAF, EGR, DPF, temperatures…),
  recorded during a drive and exportable as CSV.
- **Check-up** — contrôle-technique readiness (light, codes, self-tests) and engine health checks
  from the drive: battery and charging, warm-up / thermostat, turbo boost, rail pressure, air
  flow, EGR. Thresholds are indicative.
- **Console** — raw ELM327 commands, and a read-only survey of what the ECU can tell.

## Install

Download `qdiag.apk` from the [latest release](https://github.com/kream0/qdiag/releases/latest)
on the phone and open it (allow installing from the browser the first time). The app then
updates itself from new releases.

## Use

1. Plug the adapter into the OBD socket (under the dashboard, left of the steering column).
2. Ignition on, or engine running for live data.
3. In the app, **Connect**: Android offers to join the adapter's Wi-Fi (`WiFi_OBDII`); mobile
   data stays on for the internet. Address and port (default `192.168.0.10:35000`) are in Settings.

The app only reads from the car, except clearing fault codes when you ask.

## Build

Builds run in GitHub Actions (`.github/workflows/build.yml`); bumping `VERSION` publishes a
release. Fonts: Doto, Space Grotesk, Space Mono (OFL, see `licenses/`).
