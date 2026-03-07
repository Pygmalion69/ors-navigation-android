package org.nitri.orsnavigation

import org.maplibre.geojson.utils.PolylineUtils
import org.maplibre.navigation.core.models.DirectionsResponse
import org.maplibre.navigation.core.models.DirectionsRoute
import org.maplibre.navigation.core.models.RouteOptions
import org.maplibre.navigation.core.models.UnitType
import timber.log.Timber
import java.util.UUID

class ImportedRouteParser(
    private val language: String,
) {
    fun parseAndNormalize(jsonText: String): ImportedRouteResult {
        if (jsonText.isBlank()) {
            return ImportedRouteResult.Error("Route JSON file is empty.")
        }

        val parsedRoute = parseDirectionsRoute(jsonText)
            ?: return ImportedRouteResult.Error("Unsupported route JSON format.")

        Timber.d("Imported route parsed successfully")

        if (parsedRoute.routeOptions != null) {
            Timber.d("Imported route already contains routeOptions; no normalization needed")
            return ImportedRouteResult.Success(parsedRoute)
        }

        Timber.d("Imported route missing routeOptions; normalizing from geometry")
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

        val routeWithOptions = parsedRoute.toBuilder().withRouteOptions(
            RouteOptions(
                baseUrl = "https://api.openrouteservice.org",
                profile = "driving",
                user = "openrouteservice",
                accessToken = "openrouteservice",
                voiceInstructions = true,
                voiceUnits = UnitType.METRIC,
                bannerInstructions = true,
                steps = true,
                geometries = "polyline6",
                language = language,
                coordinates = coordinates,
                requestUuid = UUID.randomUUID().toString(),
            )
        ).build()

        return ImportedRouteResult.Success(routeWithOptions)
    }

    private fun parseDirectionsRoute(jsonText: String): DirectionsRoute? {
        Timber.d("Attempting to parse imported JSON as DirectionsRoute")
        try {
            return DirectionsRoute.fromJson(jsonText)
        } catch (routeError: Exception) {
            Timber.w(routeError, "JSON is not a raw DirectionsRoute, trying DirectionsResponse")
        }

        Timber.d("Attempting to parse imported JSON as DirectionsResponse")
        return try {
            val response = DirectionsResponse.fromJson(jsonText)
            response.routes.firstOrNull().also {
                if (it == null) {
                    Timber.e("DirectionsResponse parsed but contained no routes")
                }
            }
        } catch (responseError: Exception) {
            Timber.e(responseError, "Failed to parse imported route JSON")
            null
        }
    }
}
