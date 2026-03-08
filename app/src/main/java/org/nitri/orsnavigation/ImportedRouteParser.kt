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

        Timber.d("Normalizing imported route options for navigation compatibility")
        val sourceRouteOptions = parsedRoute.routeOptions
        val coordinates = sourceRouteOptions?.coordinates?.takeIf { it.size >= 2 }
            ?: decodeRouteEndpoints(parsedRoute.geometry)

        if (coordinates.isNullOrEmpty()) {
            return ImportedRouteResult.Error("Imported route is missing required route options metadata.")
        }

        val normalizedProfile = normalizeProfile(sourceRouteOptions?.profile)
        val routeWithOptions = parsedRoute.toBuilder().withRouteOptions(
            RouteOptions(
                baseUrl = sourceRouteOptions?.baseUrl ?: "https://api.openrouteservice.org",
                profile = normalizedProfile,
                user = sourceRouteOptions?.user ?: "openrouteservice",
                accessToken = sourceRouteOptions?.accessToken ?: "openrouteservice",
                voiceInstructions = sourceRouteOptions?.voiceInstructions ?: true,
                voiceUnits = sourceRouteOptions?.voiceUnits ?: UnitType.METRIC,
                bannerInstructions = sourceRouteOptions?.bannerInstructions ?: true,
                steps = sourceRouteOptions?.steps ?: true,
                geometries = sourceRouteOptions?.geometries ?: "polyline6",
                language = language,
                coordinates = coordinates,
                requestUuid = sourceRouteOptions?.requestUuid ?: UUID.randomUUID().toString(),
            )
        ).build()

        return ImportedRouteResult.Success(routeWithOptions)
    }

    private fun decodeRouteEndpoints(geometry: String?): List<org.maplibre.geojson.model.Point>? {
        if (geometry.isNullOrBlank()) {
            Timber.e("Imported route geometry is empty; cannot derive coordinates")
            return null
        }

        val decoded = tryDecodeEndpoints(geometry, 6) ?: tryDecodeEndpoints(geometry, 5)
        if (decoded == null) {
            Timber.e("Failed to decode route geometry for route options normalization")
        }
        return decoded
    }

    private fun tryDecodeEndpoints(geometry: String, precision: Int): List<org.maplibre.geojson.model.Point>? {
        return try {
            val decoded = PolylineUtils.decode(geometry, precision)
            if (decoded.size < 2) {
                null
            } else {
                listOf(decoded.first(), decoded.last())
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeProfile(profile: String?): String {
        return when (profile?.lowercase()) {
            null, "" -> "driving"
            "driving-car", "driving-hgv" -> "driving"
            "foot-walking", "foot-hiking", "wheelchair" -> "walking"
            "cycling-regular", "cycling-road", "cycling-mountain", "cycling-electric" -> "cycling"
            else -> profile
        }
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
