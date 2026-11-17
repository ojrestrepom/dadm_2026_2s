package co.edu.unal.reto_9

import android.location.Location
import com.wdtinc.mapbox_vector_tile.VectorTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.tan

internal data class NearbyPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Float
)

internal class SearchAreaTooLarge : IOException()

private const val TILE_ZOOM = 14
private const val WORLD_TILES = 1 shl TILE_ZOOM
private const val MAX_TILES = 128
private const val MAX_MERCATOR_LATITUDE = 85.05112878

private data class SearchTile(val x: Int, val y: Int)

internal suspend fun findNearbyPlaces(origin: Location, radiusKm: Double): List<NearbyPlace> =
    withContext(Dispatchers.IO) {
        val radiusMeters = radiusKm * 1000
        val tiles = searchTiles(origin, radiusMeters)
        if (tiles.isEmpty()) return@withContext emptyList()
        val template = readOpenFreeMap("https://tiles.openfreemap.org/planet") {
            JSONObject(it.bufferedReader().readText()).getJSONArray("tiles").getString(0)
        }
        tiles.chunked(4).flatMap { batch ->
            batch.map { tile ->
                async { readPlaces(template, tile, origin, radiusMeters) }
            }.awaitAll().flatten()
        }.distinctBy { Triple(it.name, it.latitude, it.longitude) }
            .sortedBy { it.distanceMeters }
    }

private fun searchTiles(origin: Location, radiusMeters: Double): List<SearchTile> {
    // Use a conservative spherical bound before applying Android's geodesic distance.
    val angle = radiusMeters / 6_330_000
    val latitude = Math.toRadians(origin.latitude)
    if (angle >= PI / 2) throw SearchAreaTooLarge()
    val south = Math.toDegrees(latitude - angle)
    val north = Math.toDegrees(latitude + angle)
    if (south > MAX_MERCATOR_LATITUDE || north < -MAX_MERCATOR_LATITUDE) return emptyList()
    val longitudeSpan = Math.toDegrees(asin((sin(angle) / cos(latitude)).coerceIn(0.0, 1.0)))
    val west = tileX(origin.longitude - longitudeSpan)
    val east = tileX(origin.longitude + longitudeSpan)
    val top = tileY(north)
    val bottom = tileY(south)
    if ((east - west + 1L) * (bottom - top + 1) > MAX_TILES) throw SearchAreaTooLarge()
    return (west..east).flatMap { x ->
        (top..bottom).map { y -> SearchTile(Math.floorMod(x, WORLD_TILES), y) }
    }
}

private fun tileX(longitude: Double): Int = floor((longitude + 180) / 360 * WORLD_TILES).toInt()

private fun tileY(latitude: Double): Int {
    val radians = Math.toRadians(latitude.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE))
    return floor((1 - ln(tan(radians) + 1 / cos(radians)) / PI) / 2 * WORLD_TILES)
        .toInt().coerceIn(0, WORLD_TILES - 1)
}

private fun readPlaces(
    template: String,
    tile: SearchTile,
    origin: Location,
    radiusMeters: Double
): List<NearbyPlace> {
    val url = template.replace("{z}", TILE_ZOOM.toString())
        .replace("{x}", tile.x.toString()).replace("{y}", tile.y.toString())
    val layer = readOpenFreeMap(url, VectorTile.Tile::parseFrom)
        .layersList.firstOrNull { it.name == "poi" } ?: return emptyList()
    val nameKey = layer.keysList.indexOf("name")
    if (nameKey == -1) return emptyList()
    val distance = FloatArray(1)
    return buildList {
        for (feature in layer.featuresList) {
            if (feature.type != VectorTile.Tile.GeomType.POINT || feature.geometryCount < 3) continue
            val tags = feature.tagsList
            val nameTag = (0 until tags.size - 1 step 2).firstOrNull { tags[it] == nameKey }
                ?: continue
            val name = layer.getValues(tags[nameTag + 1]).stringValue.trim()
            if (name.isEmpty()) continue

            // A point geometry starts with MoveTo and zigzag-encoded tile coordinates.
            val x = decodeCoordinate(feature.getGeometry(1))
            val y = decodeCoordinate(feature.getGeometry(2))
            // Ignore the copies that the tile buffer includes from adjacent tiles.
            if (x !in 0 until layer.extent || y !in 0 until layer.extent) continue
            val longitude = (tile.x + x.toDouble() / layer.extent) / WORLD_TILES * 360 - 180
            val latitude = Math.toDegrees(atan(sinh(PI *
                (1 - 2 * (tile.y + y.toDouble() / layer.extent) / WORLD_TILES))))
            Location.distanceBetween(origin.latitude, origin.longitude, latitude, longitude, distance)
            if (distance[0] <= radiusMeters) add(NearbyPlace(name, latitude, longitude, distance[0]))
        }
    }
}

private fun decodeCoordinate(value: Int): Int = (value ushr 1) xor -(value and 1)

private fun <T> readOpenFreeMap(url: String, read: (java.io.InputStream) -> T): T {
    val endpoint = URL(url)
    if (endpoint.protocol != "https" || endpoint.host != "tiles.openfreemap.org") {
        throw IOException("Unexpected OpenFreeMap endpoint")
    }
    val connection = endpoint.openConnection() as HttpURLConnection
    return try {
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.inputStream.use(read)
    } finally {
        connection.disconnect()
    }
}
