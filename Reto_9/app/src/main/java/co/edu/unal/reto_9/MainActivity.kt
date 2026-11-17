package co.edu.unal.reto_9

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import co.edu.unal.reto_9.ui.theme.Reto_9Theme
import org.json.JSONException
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.IOException
import kotlin.math.log2

class MainActivity : ComponentActivity() {
    private lateinit var mapView: MapView
    private var map by mutableStateOf<MapLibreMap?>(null)
    private var placesSource by mutableStateOf<GeoJsonSource?>(null)
    private var selectedPlace by mutableStateOf<String?>(null)
    private var mapFailed by mutableStateOf(false)
    private val searchPreferences by lazy {
        getSharedPreferences("search_preferences", MODE_PRIVATE)
    }
    private var radiusKm by mutableStateOf(DEFAULT_RADIUS_KM)
    private var location by mutableStateOf<Location?>(null)
    private var status by mutableStateOf(R.string.location_intro)
    private var pendingRequest: CancellationSignal? = null
    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            readLocation()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        radiusKm = searchPreferences.getString(RADIUS_KEY, null)
            ?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: DEFAULT_RADIUS_KM
        MapLibre.getInstance(this)
        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)
        mapView.addOnDidFailLoadingMapListener { mapFailed = true }
        mapView.getMapAsync { readyMap ->
            loadMapStyle(readyMap)
            readyMap.addOnMapClickListener { coordinate ->
                readyMap.selectPlace(coordinate, placesSource) { selectedPlace = it }
            }
            map = readyMap
        }
        enableEdgeToEdge()
        setContent {
            var radiusInput by rememberSaveable { mutableStateOf(formatRadius(radiusKm)) }
            var radiusError by rememberSaveable { mutableStateOf(false) }
            val focusManager = LocalFocusManager.current
            val origin = location
            val radius = radiusKm
            var retry by remember { mutableStateOf(0) }
            var places by remember(origin, radius, retry) { mutableStateOf(emptyList<NearbyPlace>()) }
            var searchStatus by remember(origin, radius, retry) { mutableStateOf(R.string.places_loading) }
            LaunchedEffect(origin, radius, retry) {
                if (origin != null) {
                    searchStatus = try {
                        places = findNearbyPlaces(origin, radius)
                        if (places.isEmpty()) R.string.places_empty else R.string.places_found
                    } catch (_: SearchAreaTooLarge) {
                        R.string.places_area_large
                    } catch (_: IOException) {
                        R.string.places_error
                    } catch (_: JSONException) {
                        R.string.places_error
                    }
                }
            }
            val activeMap = map
            LaunchedEffect(activeMap, origin, radius) {
                if (activeMap != null && origin != null) {
                    val zoom = (12.5 - log2(radius / 5)).coerceIn(1.0, 17.0)
                    activeMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                        LatLng(origin.latitude, origin.longitude), zoom
                    ))
                }
            }
            LaunchedEffect(activeMap, origin, places) {
                if (activeMap != null) {
                    activeMap.clear()
                    if (origin != null) {
                        activeMap.addMarker(MarkerOptions()
                            .position(LatLng(origin.latitude, origin.longitude))
                            .title(getString(R.string.location_title)))
                        if (places.size <= INDIVIDUAL_MARKER_LIMIT) {
                            activeMap.addMarkers(places.map { place ->
                                MarkerOptions().position(LatLng(place.latitude, place.longitude))
                                    .title(place.name)
                            })
                        }
                    }
                }
            }
            val source = placesSource
            LaunchedEffect(source, activeMap, origin, places) {
                selectedPlace = null
                source?.showPlaces(places.takeIf { it.size > INDIVIDUAL_MARKER_LIMIT }.orEmpty())
                if (source != null && activeMap != null && origin != null && places.isNotEmpty()) {
                    val bounds = LatLngBounds.Builder()
                        .include(LatLng(origin.latitude, origin.longitude))
                        .includes(places.map { LatLng(it.latitude, it.longitude) })
                        .build()
                    if (bounds.latitudeSpan > 0 || bounds.longitudeSpan > 0) {
                        activeMap.moveCamera(CameraUpdateFactory.newLatLngBounds(
                            bounds, (32 * resources.displayMetrics.density).toInt()
                        ))
                    }
                }
            }
            Reto_9Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier.padding(innerPadding).imePadding()
                            .verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            stringResource(R.string.location_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        val current = location
                        Text(
                            if (current == null) stringResource(status)
                            else stringResource(
                                R.string.location_coordinates,
                                current.latitude,
                                current.longitude
                            )
                        )
                        Button(
                            onClick = {
                                if (hasPermission(ACCESS_COARSE_LOCATION) ||
                                    hasPermission(ACCESS_FINE_LOCATION)
                                ) {
                                    readLocation()
                                } else {
                                    requestPermissions.launch(
                                        arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)
                                    )
                                }
                            },
                            enabled = status != R.string.location_loading
                        ) {
                            Text(stringResource(R.string.location_button))
                        }
                        Text(
                            stringResource(R.string.radius_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(stringResource(R.string.radius_active, formatRadius(radiusKm)))
                        OutlinedTextField(
                            value = radiusInput,
                            onValueChange = {
                                radiusInput = it
                                radiusError = false
                            },
                            label = { Text(stringResource(R.string.radius_input)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = radiusError,
                            supportingText = {
                                if (radiusError) Text(stringResource(R.string.radius_error))
                            }
                        )
                        Button(onClick = {
                            val value = radiusInput.trim().replace(',', '.').toDoubleOrNull()
                            if (value == null || !value.isFinite() || value <= 0) {
                                radiusError = true
                            } else {
                                searchPreferences.edit().putString(RADIUS_KEY, value.toString()).apply()
                                radiusKm = value
                                radiusInput = formatRadius(value)
                                radiusError = false
                                focusManager.clearFocus()
                            }
                        }) {
                            Text(stringResource(R.string.radius_save))
                        }
                        if (origin != null) {
                            Text(
                                if (searchStatus == R.string.places_found) {
                                    stringResource(searchStatus, places.size)
                                } else {
                                    stringResource(searchStatus)
                                }
                            )
                            if (searchStatus == R.string.places_error) {
                                Button(onClick = { retry++ }) {
                                    Text(stringResource(R.string.places_retry))
                                }
                            }
                        }
                        if (mapFailed) {
                            Text(stringResource(R.string.map_error))
                            Button(onClick = {
                                mapFailed = false
                                map?.let(::loadMapStyle)
                            }) {
                                Text(stringResource(R.string.map_retry))
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(400.dp)) {
                            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
                            selectedPlace?.let { name ->
                                Surface(
                                    modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                                    shape = MaterialTheme.shapes.medium,
                                    tonalElevation = 4.dp
                                ) {
                                    Text(name, Modifier.padding(12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    private fun loadMapStyle(target: MapLibreMap) {
        placesSource = null
        target.setStyle(MAP_STYLE) { style ->
            placesSource = addPlacesLayers(style)
            mapFailed = false
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PERMISSION_GRANTED

    private fun readLocation() {
        location = null
        val precise = hasPermission(ACCESS_FINE_LOCATION)
        if (!precise && !hasPermission(ACCESS_COARSE_LOCATION)) {
            status = R.string.location_denied
            return
        }

        val manager = getSystemService(LocationManager::class.java)
        if (!LocationManagerCompat.isLocationEnabled(manager)) {
            status = R.string.location_disabled
            return
        }

        val providers = if (precise) {
            listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        } else {
            listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER)
        }
        val enabledProviders = providers.filter {
            it in manager.allProviders && manager.isProviderEnabled(it)
        }
        status = R.string.location_loading
        requestLocation(manager, enabledProviders.iterator())
    }

    private fun requestLocation(manager: LocationManager, providers: Iterator<String>) {
        if (!providers.hasNext()) {
            status = R.string.location_unavailable
            return
        }

        val signal = CancellationSignal()
        pendingRequest = signal
        try {
            LocationManagerCompat.getCurrentLocation(
                manager, providers.next(), signal, ContextCompat.getMainExecutor(this)
            ) { result ->
                if (pendingRequest === signal) {
                    pendingRequest = null
                    if (result == null) {
                        requestLocation(manager, providers)
                    } else {
                        location = result
                        status = R.string.location_intro
                    }
                }
            }
        } catch (_: SecurityException) {
            pendingRequest = null
            status = R.string.location_denied
        }
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
        pendingRequest?.let {
            pendingRequest = null
            it.cancel()
            status = R.string.location_intro
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        mapView.onDestroy()
        super.onDestroy()
    }

    private fun formatRadius(value: Double): String = value.toString().removeSuffix(".0")

    private companion object {
        const val INDIVIDUAL_MARKER_LIMIT = 250
        const val DEFAULT_RADIUS_KM = 5.0
        const val RADIUS_KEY = "radius_km"
        const val MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"
    }
}





