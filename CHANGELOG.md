# Changelog

## 0.3.0

- Recording continues with the screen off or another app in front: while connected to the car, QDiag shows a "Connecté à la voiture" notification and keeps the adapter's Wi-Fi awake. Allow notifications when asked.

## 0.2.3

- Keeps the link to the adapter busy (it asks for the battery voltage every few seconds) so it no longer drops while you move between screens; the voltage on the home screen stays up to date.
- Notices straight away when the phone leaves the adapter's Wi-Fi, and reconnects.

## 0.2.2

- A live session cut off by a lost connection now ends properly (its check-up is kept) instead of showing as running with nothing being read.

## 0.2.1

- Reconnects by itself when the adapter drops the link (it restarts when the engine is started), and a live session carries on instead of stopping.

## 0.2.0

- Each code reading, drive check-up and survey is sent to your car logbook (Carnet Qashqai+2), on a channel only your car's VIN unlocks. Settings → Carnet to turn it off.
- Codes seen on this car are now explained: upstream oxygen sensor (P0130, P0134, P0135, P2A00…), accelerator pedal sensors (P0225, P2120…), cruise control buttons (P0564).
- Live data shows the oxygen sensor's lambda and current when the engine ECU offers them.
- Parts without a Nissan number open an Autodoc search by vehicle.

## 0.1.1

- Finds the adapter's Wi-Fi whatever the case of its name (WiFi_OBDII, WIFI_OBDII…).
- Cancel now really stops a connection in progress.
- An unexpected reply from the adapter no longer stops the app: it's reported in the diagnostics.
- A failed code reading keeps the previous one instead of showing "no fault code".

## 0.1.0

- First version for the Qashqai+2 2.0 dCi and a Wi-Fi ELM327 adapter.
- Connects over the adapter's Wi-Fi while mobile data stays on for the internet.
- Fault codes: stored, pending and permanent, explained for the M9R engine, with the parts to
  check and their Nissan references; clearing with a warning.
- Live values recorded during a drive, CSV export.
- Check-up: contrôle-technique readiness and engine health checks (battery, charging, warm-up,
  turbo, rail pressure, air flow, EGR).
- Console with a read-only survey sent to the developer, diagnostics, in-app updates.
