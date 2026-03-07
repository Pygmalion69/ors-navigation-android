package org.nitri.orsnavigation

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.Style
import org.maplibre.geojson.model.Point
import org.maplibre.geojson.turf.TurfMeasurement
import org.maplibre.geojson.turf.TurfUnit
import org.nitri.orsnavigation.databinding.ActivityMainBinding
import org.nitri.orsnavigation.ors.OrsRouteAdapter
import org.maplibre.navigation.android.navigation.ui.v5.NavigationLauncher
import org.maplibre.navigation.android.navigation.ui.v5.NavigationLauncherOptions
import org.maplibre.navigation.android.navigation.ui.v5.route.NavigationMapRoute
import org.maplibre.navigation.core.models.DirectionsRoute
import org.maplibre.navigation.core.models.RouteOptions
import org.maplibre.navigation.core.models.UnitType
import org.nitri.ors.Ors
import org.nitri.ors.OrsClient
import org.nitri.ors.Profile
import org.nitri.ors.domain.route.RouteRequest
import timber.log.Timber
import java.util.Locale
import java.util.UUID

class MainActivity :
    AppCompatActivity(),
    OnMapReadyCallback,
    MapLibreMap.OnMapClickListener {
    private lateinit var mapLibreMap: MapLibreMap

    private var language = Locale.getDefault().language
    private var route: DirectionsRoute? = null
    private var navigationMapRoute: NavigationMapRoute? = null
    private var destination: Point? = null
    private var locationComponent: LocationComponent? = null
    private lateinit var orsClient: OrsClient

    private lateinit var binding: ActivityMainBinding

    private var simulateRoute = false
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var importedRouteLoader: ImportedRouteLoader

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (true) { // DEBUG
            Timber.plant(Timber.DebugTree())
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.mapView.apply {
            onCreate(savedInstanceState)
            getMapAsync(this@MainActivity)
        }

        val apiKey = getString(R.string.ors_api_key)
        if (apiKey.contains("YOUR_ORS_API_KEY")) {
            Snackbar.make(
                findViewById(R.id.container),
                "Set ORS_API_KEY in your environment to enable routing.",
                Snackbar.LENGTH_LONG,
            ).show()
        }
        orsClient = Ors.create(apiKey, this)
        importedRouteLoader = ImportedRouteLoader(contentResolver = contentResolver, language = language)

        handleIntent(intent)

        binding.startRouteButton.setOnClickListener {
            route?.let { route ->
                val userLocation = mapLibreMap.locationComponent.lastKnownLocation ?: return@let
                val options = NavigationLauncherOptions.builder()
                    .directionsRoute(route)
                    .shouldSimulateRoute(simulateRoute)
                    .initialMapCameraPosition(
                        CameraPosition.Builder()
                            .target(LatLng(userLocation.latitude, userLocation.longitude)).build()
                    )
                    .lightThemeResId(R.style.TestNavigationViewLight)
                    .darkThemeResId(R.style.TestNavigationViewDark)
                    .build()
                NavigationLauncher.startNavigation(this@MainActivity, options)
            }
        }

        binding.simulateRouteSwitch.setOnCheckedChangeListener { _, checked ->
            simulateRoute = checked
        }

        binding.clearPoints.setOnClickListener {
            stopNavigationAndRoute()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        Timber.d(
            "handleIntent action=%s data=%s type=%s",
            intent?.action,
            intent?.data,
            intent?.type,
        )

        when (ImportedRouteIntentClassifier.classify(intent)) {
            IncomingIntentType.Geo -> handleGeoIntent(intent)
            IncomingIntentType.JsonRoute -> {
                val uri = intent?.data
                if (uri == null) {
                    showError("Route import failed: missing URI.")
                } else {
                    importJsonRoute(uri)
                }
            }

            IncomingIntentType.Unsupported -> {
                Timber.d("Unsupported or empty launch intent.")
            }
        }
    }

    private fun handleGeoIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        val schemeSpecificPart = uri.schemeSpecificPart
        val coordsPart = schemeSpecificPart.split('?')[0]
        val latLon = coordsPart.split(',')
        if (latLon.size >= 2) {
            try {
                val lat = latLon[0].toDouble()
                val lon = latLon[1].toDouble()
                setDestination(LatLng(lat, lon))
            } catch (e: NumberFormatException) {
                Timber.e(e, "Invalid coordinates in geo URI")
                showError("Invalid geo coordinates in intent URI.")
            }
        } else {
            // Try to parse query if present, e.g., geo:0,0?q=lat,lon(label)
            val query = uri.query
            if (query != null && query.startsWith("q=")) {
                val qValue = query.substring(2).split('(')[0]
                val qLatLon = qValue.split(',')
                if (qLatLon.size >= 2) {
                    try {
                        val lat = qLatLon[0].toDouble()
                        val lon = qLatLon[1].toDouble()
                        setDestination(LatLng(lat, lon))
                    } catch (e: NumberFormatException) {
                        Timber.e(e, "Invalid coordinates in geo URI query")
                        showError("Invalid geo query coordinates in intent URI.")
                    }
                }
            }
        }
    }

    private fun importJsonRoute(uri: Uri) {
        ioScope.launch {
            Timber.d("Importing JSON route from uri=%s", uri)
            when (val result = importedRouteLoader.load(uri)) {
                is ImportedRouteResult.Success -> {
                    withContext(Dispatchers.Main) {
                        activateImportedRoute(result.route)
                        Snackbar.make(
                            findViewById(R.id.container),
                            "Route imported successfully.",
                            Snackbar.LENGTH_LONG,
                        ).show()
                    }
                    Timber.d("Route import successful")
                }

                is ImportedRouteResult.Error -> {
                    Timber.e(result.cause, "Route import failed: %s", result.message)
                    withContext(Dispatchers.Main) {
                        stopNavigationAndRoute()
                        showError("Route import failed: ${result.message}")
                    }
                }
            }
        }
    }

    private fun activateImportedRoute(importedRoute: DirectionsRoute) {
        stopNavigationAndRoute()
        route = importedRoute
        destination = null

        if (::mapLibreMap.isInitialized) {
            navigationMapRoute?.addRoutes(listOf(importedRoute))
        }

        binding.clearPoints.visibility = View.VISIBLE
        binding.startRouteLayout.visibility = View.VISIBLE
        binding.startRouteButton.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        Snackbar.make(
            findViewById(R.id.container),
            message,
            Snackbar.LENGTH_LONG,
        ).show()
    }

    private fun setDestination(point: LatLng) {
        if (::mapLibreMap.isInitialized) {
            stopNavigationAndRoute()
            destination = Point(point.longitude, point.latitude)
            mapLibreMap.addMarker(MarkerOptions().position(point))
            binding.clearPoints.visibility = View.VISIBLE
            calculateRoute()
        } else {
            // Map not ready, store destination to set it later
            destination = Point(point.longitude, point.latitude)
        }
    }

    private fun stopNavigationAndRoute() {
        // Clear existing markers and route
        if (::mapLibreMap.isInitialized) {
            mapLibreMap.markers.forEach {
                mapLibreMap.removeMarker(it)
            }
        }
        navigationMapRoute?.removeRoute()
        route = null
        destination = null
        binding.clearPoints.visibility = View.GONE
        binding.startRouteLayout.visibility = View.GONE
    }

    override fun onMapReady(mapLibreMap: MapLibreMap) {
        this.mapLibreMap = mapLibreMap
        mapLibreMap.setStyle(
            Style.Builder().fromUri(getString(R.string.map_style_light))
        ) { style ->
            enableLocationComponent(style)
            navigationMapRoute = NavigationMapRoute(binding.mapView, mapLibreMap)
            mapLibreMap.addOnMapClickListener(this)

            destination?.let {
                val point = LatLng(it.latitude, it.longitude)
                mapLibreMap.addMarker(MarkerOptions().position(point))
                binding.clearPoints.visibility = View.VISIBLE
                calculateRoute()
            }

            route?.let {
                navigationMapRoute?.addRoutes(listOf(it))
                binding.startRouteLayout.visibility = View.VISIBLE
                binding.clearPoints.visibility = View.VISIBLE
            }

            Snackbar.make(
                findViewById(R.id.container),
                "Tap map to place destination",
                Snackbar.LENGTH_LONG,
            ).show()
        }
    }

    @SuppressWarnings("MissingPermission")
    private fun enableLocationComponent(style: Style) {
        locationComponent = mapLibreMap.locationComponent
        locationComponent?.let {
            it.activateLocationComponent(
                LocationComponentActivationOptions.builder(this, style).build(),
            )
            it.isLocationComponentEnabled = true
            it.cameraMode = CameraMode.TRACKING_GPS_NORTH
            it.renderMode = RenderMode.NORMAL
        }
    }

    override fun onMapClick(point: LatLng): Boolean {
        setDestination(point)
        return true
    }

    private fun calculateRoute() {
        binding.startRouteLayout.visibility = View.GONE
        val userLocation = mapLibreMap.locationComponent.lastKnownLocation
        val destination = destination
        if (userLocation == null) {
            Timber.d("calculateRoute: User location is null, therefore, origin can't be set.")
            return
        }

        if (destination == null) {
            Timber.d("calculateRoute: destination is null, therefore, destination can't be set.")
            return
        }

        val origin = Point(userLocation.longitude, userLocation.latitude)
        if (TurfMeasurement.distance(origin, destination, TurfUnit.METRES) < 50) {
            Timber.d("calculateRoute: distance < 50 m")
            binding.startRouteButton.visibility = View.GONE
            return
        }

        val request = RouteRequest(
            coordinates = listOf(
                listOf(origin.longitude, origin.latitude),
                listOf(destination.longitude, destination.latitude),
            ),
        )

        ioScope.launch {
            try {
                val directionsRoute = OrsRouteAdapter.fetchDirectionsRoute(
                    ors = orsClient,
                    profile = Profile.DRIVING_CAR,
                    request = request,
                )
                val routeWithOptions = directionsRoute.copy(
                    routeOptions = RouteOptions(
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
                        coordinates = listOf(origin, destination),
                        requestUuid = UUID.randomUUID().toString(),
                    ),
                )

                withContext(Dispatchers.Main) {
                    route = routeWithOptions
                    navigationMapRoute?.addRoutes(listOf(routeWithOptions))
                    binding.startRouteLayout.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Timber.e(e, "calculateRoute: ORS route failed")
                withContext(Dispatchers.Main) {
                    Snackbar.make(
                        findViewById(R.id.container),
                        "Route error: ${e.message}",
                        Snackbar.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        binding.mapView.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::mapLibreMap.isInitialized) {
            mapLibreMap.removeOnMapClickListener(this)
        }
        ioScope.cancel()
        binding.mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }
}
