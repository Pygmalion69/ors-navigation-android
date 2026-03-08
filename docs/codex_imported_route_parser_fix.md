# Codex task: fix ImportedRouteParser normalization so navigation accepts imported routes

## Problem

Imported routes render correctly but navigation start fails with:

IllegalStateException: Using the default milestones requires the directions route to be requested with voice instructions enabled.

This occurs when MapLibreNavigation.startNavigation(...) validates the route and determines that the route was not requested with voiceInstructions enabled.

However, the imported JSON already contains:

- routeOptions.voiceInstructions = true
- routeOptions.bannerInstructions = true
- step-level voiceInstructions
- step-level bannerInstructions

So the issue is not the generator (NavFromTrack).

The issue is how the Android app handles routeOptions during import.

---

# Root cause

ImportedRouteParser currently short-circuits normalization if routeOptions is present:

    if (parsedRoute.routeOptions != null) {
        Timber.d("Imported route already contains routeOptions; no normalization needed")
        return ImportedRouteResult.Success(parsedRoute)
    }

This assumes that any routeOptions object inside the JSON is already valid for the navigation SDK.

That assumption is incorrect.

After deserialization:
- fields may be null
- fields may not match SDK expectations
- required flags such as voiceInstructions may not be set internally

As a result the navigation SDK rejects the route.

---

# Correct design

Imported routes must always be normalized into a known-good RouteOptions instance that satisfies MapLibre Navigation requirements.

Do not trust incoming JSON routeOptions blindly.

Instead:
1. read existing routeOptions if present
2. merge values where possible
3. enforce required navigation flags
4. rebuild the route using toBuilder()

---

# Required changes

## 1 Remove early return

Remove this block:

    if (parsedRoute.routeOptions != null) {
        Timber.d("Imported route already contains routeOptions; no normalization needed")
        return ImportedRouteResult.Success(parsedRoute)
    }

Normalization must always run.

---

# 2 Build normalized RouteOptions

Create a new RouteOptions instance based on the parsed route.

Example implementation:

    val existing = parsedRoute.routeOptions

    val coordinates = try {
        val decoded = PolylineUtils.decode(parsedRoute.geometry, 6)
        listOf(decoded.first(), decoded.last())
    } catch (e: Exception) {
        Timber.e(e, "Failed to decode route geometry for route options normalization")
        null
    }

    if (coordinates.isNullOrEmpty()) {
        return ImportedRouteResult.Error("Imported route is missing required route options metadata.")
    }

    val normalizedRouteOptions = RouteOptions(
        baseUrl = existing?.baseUrl ?: "https://api.mapbox.com",
        profile = existing?.profile ?: "driving",
        user = existing?.user ?: "mapbox",
        accessToken = existing?.accessToken ?: "mapbox",
        voiceInstructions = true,
        voiceUnits = existing?.voiceUnits ?: UnitType.METRIC,
        bannerInstructions = true,
        steps = true,
        geometries = existing?.geometries ?: "polyline6",
        language = existing?.language ?: language,
        coordinates = existing?.coordinates ?: coordinates,
        requestUuid = existing?.requestUuid ?: UUID.randomUUID().toString(),
    )

Important: force these fields to true

    voiceInstructions = true
    bannerInstructions = true
    steps = true

These are required for Navigation UI default milestones.

---

# 3 Rebuild the route

Replace the route's options using the builder.

    val routeWithOptions = parsedRoute.toBuilder()
        .withRouteOptions(normalizedRouteOptions)
        .build()

    return ImportedRouteResult.Success(routeWithOptions)

---

# 4 Add validation logging

Before returning the normalized route add debug logs:

    Timber.d("Normalized routeOptions.voiceInstructions=%s",
        routeWithOptions.routeOptions?.voiceInstructions)

    Timber.d("Normalized routeOptions.bannerInstructions=%s",
        routeWithOptions.routeOptions?.bannerInstructions)

    Timber.d("Normalized routeOptions.steps=%s",
        routeWithOptions.routeOptions?.steps)

    Timber.d("Normalized routeOptions.geometries=%s",
        routeWithOptions.routeOptions?.geometries)

This confirms that the navigation SDK receives the correct metadata.

---

# 5 Add logging before navigation start

Inside MainActivity before calling NavigationLauncher.startNavigation(...) log:

    Timber.d("startNavigation voiceInstructions=%s",
        route?.routeOptions?.voiceInstructions)

    Timber.d("startNavigation bannerInstructions=%s",
        route?.routeOptions?.bannerInstructions)

    Timber.d("startNavigation steps=%s",
        route?.routeOptions?.steps)

This verifies the route state passed to the navigation engine.

---

# Expected result

After these changes:

- imported route still parses
- route renders correctly
- routeOptions always satisfies Navigation SDK requirements
- navigation starts successfully
- crash no longer occurs

---

# Acceptance criteria

1. ImportedRouteParser no longer returns early when routeOptions exists.
2. A normalized RouteOptions object is always created.
3. voiceInstructions, bannerInstructions, and steps are always true.
4. Debug logs confirm normalized values.
5. Navigation starts successfully with imported routes.
