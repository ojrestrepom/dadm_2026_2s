package co.edu.unal.reto_9

import android.graphics.Color
import android.graphics.RectF
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val PLACES_SOURCE = "nearby-places"
private const val PLACE_LAYER = "nearby-place-markers"
private const val CLUSTER_LAYER = "nearby-place-clusters"

internal fun addPlacesLayers(style: Style): GeoJsonSource {
    val source = GeoJsonSource(
        PLACES_SOURCE,
        FeatureCollection.fromFeatures(emptyList<Feature>()),
        GeoJsonOptions().withCluster(true).withClusterMaxZoom(16).withClusterRadius(40)
    )
    style.addSource(source)
    val cluster = Expression.has("point_count")
    style.addLayer(CircleLayer(PLACE_LAYER, PLACES_SOURCE)
        .withFilter(Expression.not(cluster))
        .withProperties(
            PropertyFactory.circleColor(Color.rgb(51, 92, 168)),
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleStrokeColor(Color.WHITE),
            PropertyFactory.circleStrokeWidth(2f)
        ))
    style.addLayer(CircleLayer(CLUSTER_LAYER, PLACES_SOURCE)
        .withFilter(cluster)
        .withProperties(
            PropertyFactory.circleColor(Color.rgb(51, 92, 168)),
            PropertyFactory.circleRadius(17f)
        ))
    style.addLayer(SymbolLayer("nearby-place-counts", PLACES_SOURCE)
        .withFilter(cluster)
        .withProperties(
            PropertyFactory.textField(Expression.toString(Expression.get("point_count"))),
            PropertyFactory.textColor(Color.WHITE),
            PropertyFactory.textAllowOverlap(true)
        ))
    return source
}

internal fun GeoJsonSource.showPlaces(places: List<NearbyPlace>) {
    setGeoJson(FeatureCollection.fromFeatures(places.map { place ->
        Feature.fromGeometry(Point.fromLngLat(place.longitude, place.latitude)).apply {
            addStringProperty("name", place.name)
        }
    }))
}

internal fun MapLibreMap.selectPlace(
    coordinate: LatLng,
    source: GeoJsonSource?,
    onName: (String?) -> Unit
): Boolean {
    val pixel = projection.toScreenLocation(coordinate)
    val hit = RectF(pixel.x - 12, pixel.y - 12, pixel.x + 12, pixel.y + 12)
    val feature = queryRenderedFeatures(hit, PLACE_LAYER, CLUSTER_LAYER).firstOrNull()
    if (feature == null) {
        onName(null)
        return false
    }
    if (feature.hasProperty("point_count")) {
        val center = feature.geometry() as? Point ?: return false
        val zoom = source?.getClusterExpansionZoom(feature) ?: return false
        animateCamera(CameraUpdateFactory.newLatLngZoom(
            LatLng(center.latitude(), center.longitude()), zoom.toDouble()
        ))
        onName(null)
    } else {
        onName(feature.getStringProperty("name"))
    }
    return true
}
