# Codex task: fix JSON route import parser to accept a route object instead of DirectionsResponse

## Goal

Update `ors-navigation-android` so the JSON import flow parses a **single route object** instead of expecting a top-level `DirectionsResponse`.

The current parser wrongly does this:

```kotlin
val response = DirectionsResponse.fromJson(jsonText)
return response.routes?.firstOrNull()
```

This fails because the imported file is **not** shaped like:

```json
{
  "code": "Ok",
  "routes": [ ... ]
}
```

Instead, the file is a **route object** with top-level fields like:

- `distance`
- `duration`
- `weight`
- `weight_name`
- `geometry`
- `legs`

So the parser must be changed to parse a `DirectionsRoute` directly.

## Important input fact

The imported `route.json` starts like this:

```json
{
  "distance": 8804.829251550735,
  "duration": 1056.5795101860883,
  "weight": 1056.5795101860883,
  "weight_name": "routability",
  "geometry": "...",
  "legs": [
    ...
  ]
}
```

That means this is a **route object**, not a full `DirectionsResponse`.

## Required change

Replace the current `DirectionsResponse` parsing path with direct `DirectionsRoute` parsing.

## Implementation instructions

### 1) Find the current parser

Locate the code path that currently tries to parse like this, or similarly:

```kotlin
private fun parseDirectionsRoute(jsonText: String): DirectionsRoute? {
    return try {
        val response = DirectionsResponse.fromJson(jsonString = jsonText)
        response.routes?.firstOrNull()
    } catch (responseError: Exception) {
        ...
    }
}
```

This logic is wrong for the current NavFromTrack output.

### 2) Parse the JSON as a route object

Change the parser so it parses the JSON into `DirectionsRoute` directly.

Preferred implementation:

```kotlin
private fun parseDirectionsRoute(jsonText: String): DirectionsRoute? {
    return try {
        DirectionsRoute.fromJson(jsonText)
    } catch (routeError: Exception) {
        Timber.e(routeError, "Failed to parse imported route JSON as DirectionsRoute")
        null
    }
}
```

If the exact API differs in this repo version, use the equivalent constructor / adapter / serialization entry point for `DirectionsRoute`.

Do **not** keep `DirectionsResponse.fromJson(...)` as the primary path for this file format.

### 3) Keep error reporting clear

If parsing fails:

- log the exception
- show a visible user-facing error
- do not crash
- do not activate partial route state

## Optional robustness improvement

If easy, support both formats in this order:

1. try parsing as `DirectionsRoute`
2. if that fails, try parsing as `DirectionsResponse` and take the first route

Recommended version:

```kotlin
private fun parseDirectionsRoute(jsonText: String): DirectionsRoute? {
    try {
        return DirectionsRoute.fromJson(jsonText)
    } catch (routeError: Exception) {
        Timber.w(routeError, "JSON is not a raw DirectionsRoute, trying DirectionsResponse")
    }

    return try {
        val response = DirectionsResponse.fromJson(jsonText)
        response.routes?.firstOrNull()
    } catch (responseError: Exception) {
        Timber.e(responseError, "Failed to parse imported route JSON")
        null
    }
}
```

However, the **raw route object** path must come first, because that is the actual format currently produced by NavFromTrack.

## 4) Verify compatibility of imported route fields

After parsing the `DirectionsRoute`, verify whether the imported object already contains what the current navigation flow needs.

Pay special attention to:

- `geometry`
- `legs`
- `steps`
- maneuver data
- route options, if required later by navigation launch

If the imported `DirectionsRoute` is missing runtime fields required by the app’s navigation launcher, add a normalization step after parsing.

For example:

```kotlin
private fun normalizeImportedRoute(route: DirectionsRoute): DirectionsRoute {
    if (route.routeOptions() != null) return route

    val routeOptions = RouteOptions.builder()
        .baseUrl("https://api.openrouteservice.org")
        .user("openrouteservice")
        .profile("driving")
        .coordinatesList(/* derive only if already available in route / legs / geometry */)
        .build()

    return route.toBuilder()
        .routeOptions(routeOptions)
        .build()
}
```

Only do this if needed by the current navigation flow.
Do not invent extra fields unless the app actually requires them.

## 5) Keep imported route activation unchanged

The rest of the import flow should stay the same after successful parsing:

- clear previous route state if necessary
- set imported route as active route
- render route on map
- enable existing start-navigation UI
- allow navigation to start without tapping on the map

## 6) Update documentation

Update repo documentation to clearly state that the current JSON import expects a **route object**.

Document that the file is shaped like:

```json
{
  "distance": ...,
  "duration": ...,
  "geometry": "...",
  "legs": [...]
}
```

and not like:

```json
{
  "code": "...",
  "routes": [...]
}
```

If both formats are supported after the fix, document both and state that raw `DirectionsRoute` JSON is the primary supported format.

## 7) Logging

Add concise logs for:

- parser chosen
- parse success
- parse failure
- whether normalization was needed
- route activation success

Example:

```kotlin
Timber.d("Attempting to parse imported JSON as DirectionsRoute")
Timber.d("Imported route parsed successfully")
Timber.e(error, "Imported route parsing failed")
```

## Acceptance criteria

This task is complete when all of the following are true:

1. The parser no longer requires top-level `code` and `routes` fields for the NavFromTrack file.
2. The provided `route.json` parses as a `DirectionsRoute`.
3. The app does not crash on import.
4. The imported route is rendered on the map.
5. The user can continue into the existing navigation flow with that imported route.
6. Failures still produce visible feedback.
7. Repo documentation reflects the real supported JSON format.

## Final output expected from Codex

Provide:

1. the code changes
2. a short explanation of what was changed
3. whether the final implementation supports:
   - route object only
   - or both route object and response object
4. any assumptions made about `DirectionsRoute` parsing APIs in this repo version
