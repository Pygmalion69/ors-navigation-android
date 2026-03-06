# JSON route import via Android intent

The app supports importing a Mapbox/MapLibre Directions JSON payload through `ACTION_VIEW` intents.

## Supported intent format

- Action: `android.intent.action.VIEW`
- MIME type: `application/json`
- URI schemes:
  - `content://` (recommended)
  - `file://` (best-effort, depends on Android version and sender permissions)

## Expected JSON shape

The importer accepts either:

1. A full Directions response JSON object (`code`, `routes`, ...), using the first route in `routes`.
2. A single `DirectionsRoute` JSON object directly.

If `routeOptions` metadata is missing, the app derives minimal compatible `routeOptions` from route geometry and uses the same defaults as in-app ORS route creation where possible.

## ADB examples

Use explicit component targeting:

```bash
adb push route.json /sdcard/Download/route.json
```

Preferred (`content://`):

```bash
adb shell am start \
  -n org.nitri.orsnavigation/.MainActivity \
  -a android.intent.action.VIEW \
  -d "content://com.android.externalstorage.documents/document/primary:Download/route.json" \
  -t "application/json" \
  --grant-read-uri-permission
```

Optional (`file://`, less reliable on modern Android):

```bash
adb shell am start \
  -n org.nitri.orsnavigation/.MainActivity \
  -a android.intent.action.VIEW \
  -d "file:///sdcard/Download/route.json" \
  -t "application/json"
```

## UI behavior

On successful import:

- the route is activated as the current route,
- the route is rendered on the map,
- start navigation controls are shown immediately,
- importing another route replaces the previous route.

On failure:

- a visible snackbar explains the failure,
- the app avoids partial route state and does not crash.

## Limitations and error handling

- Invalid JSON or unsupported schema is rejected with a snackbar error.
- If file access fails (missing permissions, bad URI, unreadable content), import fails with a visible error.
- `content://` with granted read permission is the most reliable delivery mechanism.
