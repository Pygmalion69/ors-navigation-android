package org.nitri.orsnavigation

import org.maplibre.navigation.core.models.DirectionsResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ImportedRouteParserTest {
    private val parser = ImportedRouteParser(language = "en")

    @Test
    fun `parses directions response payload`() {
        val json = readAssetDirectionsJson()

        val result = parser.parseAndNormalize(json)

        assertTrue(result is ImportedRouteResult.Success)
        val route = (result as ImportedRouteResult.Success).route
        assertNotNull(route.routeOptions)
        assertEquals("polyline6", route.routeOptions?.geometries)
    }

    @Test
    fun `parses direct route payload`() {
        val response = DirectionsResponse.fromJson(readAssetDirectionsJson())
        val routeJson = response.routes.first().toJson()

        val result = parser.parseAndNormalize(routeJson)

        assertTrue(result is ImportedRouteResult.Success)
        val route = (result as ImportedRouteResult.Success).route
        assertNotNull(route.routeOptions)
    }

    @Test
    fun `rejects malformed payload`() {
        val result = parser.parseAndNormalize("{ bad json")

        assertTrue(result is ImportedRouteResult.Error)
    }

    private fun readAssetDirectionsJson(): String {
        return File("src/main/assets/directions-route.json").readText()
    }
}
