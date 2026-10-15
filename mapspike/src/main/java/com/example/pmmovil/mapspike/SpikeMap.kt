package com.example.pmmovil.mapspike

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.os.SystemClock
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.hypot

/**
 * Cuenta y bloquea cualquier petición de red del mapa. Si el estilo, los glifos y los
 * datos se cargan de los assets, el contador debe quedar en 0 (prueba de modo sin red).
 */
object Net {
    val count = AtomicInteger()
    val urls: MutableList<String> = Collections.synchronizedList(ArrayList())

    fun install() {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                count.incrementAndGet()
                if (urls.size < 20) urls.add(chain.request().url.toString())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(599)
                    .message("bloqueado por el spike")
                    .body("".toResponseBody(null))
                    .build()
            }
            .build()
        HttpRequestUtil.setOkHttpClient(client)
    }
}

/** Controla el mapa del spike: estilo compartido, datos, cámara, selección y ruta. */
class SpikeMap(
    private val activity: Activity,
    val mapView: MapView,
    val replicas: Int
) {
    lateinit var map: MapLibreMap
        private set
    lateinit var style: Style
        private set
    lateinit var scene: SceneData
        private set

    var sceneBuildMs = 0L
    var styleLoadMs = 0L
    var dataSetMs = 0L
    var firstIdleMs = 0L

    private val t0 = SystemClock.elapsedRealtime()

    /** Longitud y acumulados de la ruta (metros locales) para animar el punto. */
    lateinit var routeCum: DoubleArray
        private set
    var routeLen = 0.0
        private set

    fun start(onReady: () -> Unit) {
        val tBuild = SystemClock.elapsedRealtime()
        scene = FloorScene.build(replicas)
        sceneBuildMs = SystemClock.elapsedRealtime() - tBuild
        computeRouteLength()

        mapView.addOnDidBecomeIdleListener(MapView.OnDidBecomeIdleListener {
            if (firstIdleMs == 0L) firstIdleMs = SystemClock.elapsedRealtime() - t0
        })

        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.setLogoEnabled(false)
            m.uiSettings.setAttributionEnabled(false)
            m.uiSettings.setCompassEnabled(true)
            m.setMinZoomPreference(15.0)
            m.setMaxZoomPreference(23.0)

            val tStyle = SystemClock.elapsedRealtime()
            // El mismo style.json sirve en web y en Android; solo cambia dónde viven los glifos.
            val json = activity.assets.open("style.json").bufferedReader().use { it.readText() }
                .replace("{GLYPHS_URL}", "asset://glyphs/{fontstack}/{range}.pbf")
            m.setStyle(Style.Builder().fromJson(json)) { s ->
                style = s
                styleLoadMs = SystemClock.elapsedRealtime() - tStyle
                s.addImage("chev", chevronBitmap(), true)

                val tData = SystemClock.elapsedRealtime()
                setSource("zones", scene.zones)
                setSource("slots", scene.slots)
                setSource("walls", scene.walls)
                setSource("units", scene.units)
                setSource("aisles", scene.aisles)
                setSource("labels", scene.labels)
                setSource("route", lineJson(scene.route))
                val start = scene.route.first()
                setSource("you", pointJson(start[0], start[1]))
                dataSetMs = SystemClock.elapsedRealtime() - tData

                fitAll(0)
                onReady()
            }
        }
    }

    private fun setSource(id: String, json: String) {
        val source: GeoJsonSource? = style.getSourceAs(id)
        source?.setGeoJson(json)
    }

    // --- Cámara ---

    fun fitAll(durationMs: Int) {
        val b = scene.bounds
        val bounds = LatLngBounds.Builder()
            .include(LatLng(Geo.lat(b[1]), Geo.lon(b[0])))
            .include(LatLng(Geo.lat(b[3]), Geo.lon(b[2])))
            .build()
        val update = CameraUpdateFactory.newLatLngBounds(bounds, 48)
        if (durationMs == 0) map.moveCamera(update) else map.animateCamera(update, durationMs)
    }

    private fun position(x: Double, y: Double, zoom: Double, tilt: Double, bearing: Double) =
        CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder()
                .target(LatLng(Geo.lat(y), Geo.lon(x)))
                .zoom(zoom).tilt(tilt).bearing(bearing)
                .build()
        )

    fun camera(x: Double, y: Double, zoom: Double, tilt: Double, bearing: Double) =
        map.moveCamera(position(x, y, zoom, tilt, bearing))

    fun fly(x: Double, y: Double, zoom: Double, tilt: Double, bearing: Double, ms: Int) =
        map.animateCamera(position(x, y, zoom, tilt, bearing), ms)

    fun setTilt(deg: Double) = map.moveCamera(CameraUpdateFactory.tiltTo(deg))

    // --- Selección: atenúa el resto de las cargas y resalta la zona ---

    fun select(zoneCode: String?) {
        val q = "\"${zoneCode ?: ""}\""
        style.getLayerAs<LineLayer>("sel-halo")
            ?.setFilter(Expression.raw("[\"==\",[\"get\",\"code\"],$q]"))
        val expr = if (zoneCode == null) {
            "[\"get\",\"color\"]"
        } else {
            "[\"case\",[\"==\",[\"get\",\"zone\"],$q],[\"get\",\"color\"],[\"get\",\"dim\"]]"
        }
        style.getLayerAs<FillExtrusionLayer>("units")
            ?.setProperties(PropertyFactory.fillExtrusionColor(Expression.raw(expr)))
    }

    fun halo(k: Double) {
        style.getLayer("sel-halo")?.setProperties(
            PropertyFactory.lineWidth((3.0 + 4.0 * k).toFloat()),
            PropertyFactory.lineOpacity((0.5 + 0.45 * k).toFloat())
        )
    }

    // --- Ruta y punto en movimiento ---

    fun setDot(x: Double, y: Double) = setSource("routeDot", pointJson(x, y))

    fun pointAlongRoute(distance: Double): DoubleArray {
        val pts = scene.route
        val d = distance % routeLen
        for (i in 1 until pts.size) {
            if (d <= routeCum[i]) {
                val t = (d - routeCum[i - 1]) / (routeCum[i] - routeCum[i - 1]).coerceAtLeast(1e-9)
                val a = pts[i - 1]
                val b = pts[i]
                return doubleArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
            }
        }
        return pts.last()
    }

    private fun computeRouteLength() {
        val pts = scene.route
        routeCum = DoubleArray(pts.size)
        for (i in 1 until pts.size) {
            routeCum[i] = routeCum[i - 1] + hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1])
        }
        routeLen = routeCum.last()
    }

    // --- Consultas ---

    fun queryUnitsAt(x: Float, y: Float): Int = map.queryRenderedFeatures(PointF(x, y), "units").size

    fun queryUnitCodeAt(x: Float, y: Float): String? =
        map.queryRenderedFeatures(PointF(x, y), "units").firstOrNull()?.getStringProperty("zone")

    fun statsView(enabled: Boolean) = map.enableRenderingStatsView(enabled)

    // --- Utilidades ---

    private fun lineJson(pts: List<DoubleArray>): String {
        val sb = StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[")
        for ((i, p) in pts.withIndex()) {
            if (i > 0) sb.append(',')
            sb.append('[').append(Geo.lon(p[0])).append(',').append(Geo.lat(p[1])).append(']')
        }
        return sb.append("]}}]}").toString()
    }

    private fun pointJson(x: Double, y: Double): String =
        "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Point\",\"coordinates\":[${Geo.lon(x)},${Geo.lat(y)}]}}]}"

    /** Flecha de la ruta como imagen SDF (se tiñe desde el estilo con icon-color). */
    private fun chevronBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 9f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.BLACK
        }
        val path = Path().apply { moveTo(14f, 10f); lineTo(32f, 24f); lineTo(14f, 38f) }
        Canvas(bmp).drawPath(path, paint)
        return bmp
    }
}
