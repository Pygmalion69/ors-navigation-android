# Codex task: rebuild ors-navigation-android with JSON route import support

## Goal

Rebuild `ors-navigation-android` so the app can be started with an Android `ACTION_VIEW` intent that points to a pushed `route.json` file in public `Download/`, deserialize that JSON into the active navigation route, render it on the map, and allow the user to start navigation immediately.

The JSON file is produced by **NavFromTrack** and represents a Mapbox/MapLibre Directions payload. The implementation must be added to the repository and must be usable from ADB.

## Context

The app already accepts launch and `geo:` intents.
We now want this additional flow:

1. Host machine creates `route.json`.
2. Host pushes `route.json` to the device, for example to `/sdcard/Download/route.json`.
3. Host sends an `ACTION_VIEW` intent with MIME type `application/json`.
4. `ors-navigation-android` receives the intent.
5. The app reads the file from the received URI.
6. The app deserializes the route JSON into a navigation route object.
7. The route becomes the active route in the app.
8. The route is shown on the map.
9. The existing start-navigation flow works with that imported route.

## Deliverables

Implement the feature directly in the repo and make sure the app builds.

Also add concise developer documentation to the repository, for example in `README.md` or a dedicated markdown file such as `docs/json-route-intent.md`, covering:

- supported intent format
- expected JSON shape
- ADB push command
- ADB intent command
- limitations and error handling

## Requirements

### 1) Manifest support for JSON file intents

Add an additional intent filter for the activity that should receive imported routes.

Requirements:

- action: `android.intent.action.VIEW`
- category: `android.intent.category.DEFAULT`
- support `content://` URIs
- optionally support `file://` URIs if still practical
- MIME type: `application/json`
- keep existing `geo:` handling intact

Use explicit component launching in examples, but the app itself must also be discoverable through the intent filter.

## 2) Intent handling

Extend the existing intent handling so that:

- `geo:` continues to work as before
- JSON route intents are detected separately
- `onCreate()` handles the launch intent
- `onNewIntent()` handles subsequent intents when the activity is already alive
- failures are logged and surfaced to the user with a snackbar, dialog, or equivalent visible feedback

The code should not silently ignore malformed JSON route intents.

## 3) Route file reading

Implement robust route loading from the incoming URI.

Requirements:

- use `contentResolver.openInputStream(uri)` for `content://`
- support reading UTF-8 JSON text
- close streams safely with `use`
- handle null streams
- handle permission or file access failures
- keep parsing and file IO off the main thread where appropriate

## 4) JSON deserialization

Deserialize the JSON into the route model used by the app for navigation.

Implementation guidance:

- prefer the exact model type already used by the app’s navigation flow
- if the JSON is a full Directions response, extract the first route
- if the JSON is a route object directly, use it directly
- support whichever of the two is easiest and most stable in the current codebase, but document the expected input format clearly

Important:

- make the parser tolerant enough for NavFromTrack output, but do not create a loose untyped parser if a typed model is already available
- if the imported route lacks fields required by the navigation UI, either:
  - reconstruct the missing fields in a controlled way, or
  - reject the file with a clear user-facing error message

## 5) Activate the imported route

After successful parsing:

- set the route as the active route in the same state holder used by normal routing
- render the route on the map
- make the existing start navigation button or flow available
- ensure imported routes and calculated routes do not conflict in stale state
- clearing a route should also clear an imported route cleanly

## 6) Route options compatibility

The navigation launcher usually expects a route that is compatible with the current navigation stack.

Verify whether the imported JSON already contains the required route options and metadata.

If not, add a normalization step that builds missing route options in a controlled way, consistent with the app’s current navigation usage.

Do not hardcode dummy values unless they are actually required and safe. Prefer reusing the same values and conventions already used when the app calculates a route itself.

## 7) UI behavior

Expected behavior after a successful import:

- the route is visible on the map
- the user can start navigation without tapping a destination on the map
- the UI communicates that a route was imported
- importing a second route replaces the first one cleanly

Expected behavior on failure:

- show a visible error
- do not crash
- do not leave partial route state behind

## 8) Logging

Add useful debug logging around:

- incoming intent action
- incoming URI
- detected MIME type
- file read success or failure
- parse success or failure
- route activation success or failure

Keep logs concise and practical.

## 9) Testability

Add at least lightweight automated coverage where practical.

Preferred options:

- unit tests for parsing / normalization logic
- unit tests for intent classification logic
- instrumentation tests only if necessary

At minimum, structure the implementation so parsing and normalization are testable outside the activity.

## Suggested implementation approach

Create a small focused helper instead of putting everything into the activity.

Recommended structure:

- `ImportedRouteLoader`
  - reads from URI
  - parses JSON
  - returns a normalized route or a typed failure
- `ImportedRouteResult`
  - success / error model
- activity layer
  - receives intent
  - delegates to loader
  - updates UI and route state

This is preferred over embedding all file/parsing logic directly into `MainActivity`.

## Suggested tasks

### Task A: inspect current navigation flow

Identify:

- the activity that currently receives `geo:` intents
- how the app stores the active route
- how the route is rendered on the map
- how navigation is started
- where route options are constructed today

### Task B: add manifest support

Add the JSON `ACTION_VIEW` intent filter.

### Task C: add intent classification

Introduce a small helper that decides whether an intent is:

- geo intent
- json route intent
- unsupported intent

### Task D: implement imported route loader

Implement reading + parsing + normalization.

### Task E: wire imported route into existing route state

Ensure the imported route behaves the same way as a calculated route from the point where the UI offers “start navigation”.

### Task F: documentation

Add repo documentation with exact ADB examples.

## ADB examples to support

Document commands like these, adjusted to the real package/activity names found in the repo.

### Push the file

```bash
adb push route.json /sdcard/Download/route.json
```

### Start the app with a content URI

```bash
adb shell am start   -n org.nitri.orsnavigation/.MainActivity   -a android.intent.action.VIEW   -d "content://com.android.externalstorage.documents/document/primary:Download/route.json"   -t "application/json"   --grant-read-uri-permission
```

### Optional file URI example

```bash
adb shell am start   -n org.nitri.orsnavigation/.MainActivity   -a android.intent.action.VIEW   -d "file:///sdcard/Download/route.json"   -t "application/json"
```

If `file://` is not reliable on the current target Android versions, document that and prefer `content://`.

## Acceptance criteria

The task is complete when all of the following are true:

1. The app builds successfully.
2. Existing `geo:` intent behavior still works.
3. A pushed `route.json` can be opened through ADB using `ACTION_VIEW`.
4. The app reads and parses the file without crashing.
5. The imported route is displayed on the map.
6. The user can start navigation using the imported route.
7. Import failures are visible to the user.
8. The repo contains concise documentation with exact ADB commands.
9. The implementation is reasonably modular and not entirely embedded in the activity.

## Notes for implementation

- Prefer minimal, clean changes over broad refactors.
- Keep the app’s current architecture intact.
- Reuse existing route rendering and navigation launch code wherever possible.
- If NavFromTrack JSON does not exactly match the current route model, add a narrow compatibility layer and document the supported schema.
- Do not introduce network calls for imported routes.
- Do not require the user to tap the map after importing a route.
- Do not remove the current manual routing flow.

## Final output expected from Codex

Provide:

1. the code changes in the repo
2. a short summary of changed files
3. any assumptions about the JSON schema
4. the exact ADB command that should work after the implementation
