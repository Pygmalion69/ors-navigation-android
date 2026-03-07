# JSON route import via Android intent

The app supports importing a Mapbox/MapLibre Directions JSON payload through `ACTION_VIEW` intents.

## Supported intent format

- Action: `android.intent.action.VIEW`
- MIME type: `application/json`
- URI schemes:
  - `content://`
  - `file://`

## Expected JSON shape

The importer accepts either:

1. A full Directions response JSON object (`code`, `routes`, ...), using the first route in `routes`.
2. A single `DirectionsRoute` JSON object directly.

If `routeOptions` metadata is missing, the app derives minimal compatible `routeOptions` from route geometry and uses the same defaults as in-app ORS route creation where possible.

## ADB usage

Use explicit component targeting.

### Important Android storage note

On modern Android, these common ADB patterns are often blocked:

- `content://com.android.externalstorage.documents/...` from `adb shell am start` can fail with a `SecurityException` because the shell UID does not hold a document grant.
- `file:///sdcard/Download/route.json` can fail with `EACCES` if the app has no storage permission.

### Android 12 and lower (`targetSdk` still allows `READ_EXTERNAL_STORAGE`)

For `file:///sdcard/...` imports, grant storage permission to the app:

```bash
adb shell pm grant org.nitri.orsnavigation android.permission.READ_EXTERNAL_STORAGE
```

Then start with file URI:

```bash
adb shell am start \
  -n org.nitri.orsnavigation/.MainActivity \
  -a android.intent.action.VIEW \
  -d "file:///sdcard/Download/route.json" \
  -t "application/json"
```

The app also requests this permission at runtime (Android 12 and below) when needed and retries the import after grant.

### Reliable debug fallback (app-internal file)

If external storage is still blocked on your device, use app-internal storage for debug builds:

1) Push JSON to a temporary shell-readable location:

```bash
adb push route.json /data/local/tmp/route.json
```

2) Copy into the app's internal files directory (debuggable app required):

```bash
adb shell run-as org.nitri.orsnavigation cp /data/local/tmp/route.json files/route.json
```

3) Start the app with a file URI pointing to app-internal storage:

```bash
adb shell am start \
  -n org.nitri.orsnavigation/.MainActivity \
  -a android.intent.action.VIEW \
  -d "file:///data/user/0/org.nitri.orsnavigation/files/route.json" \
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
- For production sharing flows, prefer document-picker/content-URI based sharing where the sender grants URI read permission.
