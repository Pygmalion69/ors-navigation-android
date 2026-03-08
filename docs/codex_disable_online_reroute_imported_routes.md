# Codex task: disable online rerouting for imported routes

## Goal

Update `ors-navigation-android` so navigation sessions started from an imported JSON route do not trigger online rerouting.

Current problem:

- imported route parsing works
- imported route rendering works
- navigation start works
- when the user goes off-route, the Navigation UI tries to reroute online
- that reroute path uses Mapbox services and crashes with:

```text
com.mapbox.core.exceptions.ServicesException:
Using Mapbox Services requires setting a valid access token.
```

This is not a tile-server problem.
This is an off-route rerouting problem.

## Desired behavior

For imported routes:

- navigation should continue normally
- off-route detection may still happen
- but the app must not call the built-in online reroute path
- instead, it should ignore rerouting or show a simple user message such as:
  - "Off route — automatic rerouting is disabled for imported routes"

For non-imported routes:

- existing behavior should remain unchanged

## Root cause

The current Navigation UI still behaves like a normal online navigation session.

When off-route detection fires, it eventually reaches code like:

- `NavigationViewModel.handleOffRouteEvent(...)`
- `NavigationViewRouter.findOnlineRouteWith(...)`
- `MapLibreRouteFetcher.findRouteWith(...)`

That path uses `MapboxDirections` / `NavigationRoute.Builder`, which requires a valid Mapbox access token.

Imported routes should not enter that path.

## Required design

Introduce an explicit app-level flag for imported-route navigation, and use it to disable online rerouting.

Recommended approach:

1. Track whether the current route session came from imported JSON.
2. Pass that information into the navigation flow.
3. When off-route occurs during imported-route navigation:
   - do not call the online rerouter
   - do not call MapboxDirections / NavigationRoute.Builder
   - optionally show a toast/snackbar/log message
4. Keep normal rerouting behavior for routes calculated in the normal online flow.

## Suggested implementation

## 1) Add session state flag

Add a boolean flag, for example:

```kotlin
var isImportedRouteNavigation: Boolean = false
```

Possible places:

- `OrsNavigationApplication`
- a shared singleton/session holder
- `MainActivity`
- a ViewModel field passed into the navigation activity
- intent extra passed into `MapLibreNavigationActivity`

Use whichever fits the current architecture best.

Preferred: pass it explicitly via the navigation start intent instead of relying only on global mutable state.

## 2) Set the flag when launching imported navigation

Where navigation is launched from an imported route, set the imported-route marker.

For example, in `MainActivity` when starting navigation from the imported `DirectionsRoute`, include an extra such as:

```kotlin
intent.putExtra("is_imported_route_navigation", true)
```

For non-imported routes:

```kotlin
intent.putExtra("is_imported_route_navigation", false)
```

If the current navigation launch path is wrapped in a helper, put the flag there.

## 3) Read the flag inside navigation UI flow

In the navigation activity / navigation view model / router layer, read the flag and store it.

Example:

```kotlin
val isImportedRouteNavigation =
    intent?.getBooleanExtra("is_imported_route_navigation", false) ?: false
```

Make this available wherever off-route rerouting is triggered.

## 4) Short-circuit off-route rerouting

Find the code path where off-route events result in online rerouting.

The stack trace points to:

- `NavigationViewModel.handleOffRouteEvent(...)`
- `NavigationViewRouter.findOnlineRouteWith(...)`

Add a guard before online reroute is triggered.

Conceptually:

```kotlin
private fun handleOffRouteEvent(location: Location) {
    if (isImportedRouteNavigation) {
        Timber.w("Off-route detected during imported-route navigation; online rerouting disabled")
        showOffRouteDisabledMessageOnce()
        return
    }

    // existing reroute logic
    ...
}
```

Or if the reroute call is lower in the stack:

```kotlin
fun findOnlineRouteWith(...) {
    if (isImportedRouteNavigation) {
        Timber.w("Skipping online reroute for imported route navigation")
        return
    }

    // existing online reroute logic
}
```

The key requirement is:
the code must never reach `NavigationRoute.Builder.build()` for imported routes.

## 5) Optional user feedback

Show a one-time message when off-route occurs during imported-route navigation.

Examples:

- Snackbar
- Toast
- log-only if you want the UI silent

Recommended behavior:
- show only once per navigation session to avoid spam

Example text:

- "Off route — automatic rerouting is disabled for imported routes."

## 6) Keep off-route detection if useful

You do not need to disable off-route detection itself unless that is easier.

It is enough to disable the reroute action.

That means the app can still know the driver is off the imported route without crashing.

## 7) Add logging

Add logs in three places:

### A. When navigation starts
Log whether the session is imported-route navigation:

```kotlin
Timber.d("Starting navigation; importedRoute=%s", isImportedRouteNavigation)
```

### B. When off-route is detected
Log the event:

```kotlin
Timber.w("Off-route detected; importedRoute=%s", isImportedRouteNavigation)
```

### C. When online reroute is skipped
Log the skip:

```kotlin
Timber.w("Skipping online reroute because current session uses an imported route")
```

These logs will make future debugging much easier.

## 8) Do not break normal routes

The fix must be scoped only to imported routes.

Normal online-calculated navigation must continue to reroute exactly as before.

## 9) Optional improvement

If easy, centralize this in one place rather than scattering checks.

A small helper is fine, e.g.:

```kotlin
object NavigationSessionFlags {
    var isImportedRouteNavigation: Boolean = false
}
```

Or a more structured session state holder.

Still, explicit intent extras are preferred where possible.

## Acceptance criteria

This task is complete when all of the following are true:

1. Imported-route navigation starts successfully.
2. Going off-route during imported-route navigation does not crash the app.
3. No Mapbox token error occurs for imported routes.
4. The app does not call the online reroute path for imported routes.
5. Normal online-calculated routes still reroute as before.
6. Logs clearly show when rerouting is skipped because the route was imported.

## Final output expected from Codex

Provide:

1. the code changes
2. a short explanation of where the imported-route flag is stored and read
3. the exact place where online rerouting is bypassed
4. confirmation that normal non-imported rerouting behavior remains intact
