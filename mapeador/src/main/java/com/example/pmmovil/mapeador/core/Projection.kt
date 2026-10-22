package com.example.pmmovil.mapeador.core

/** Punto proyectado a la pantalla (píxeles de la vista); `depth` es la distancia a lo largo del eje de vista. */
class ScreenPoint(val x: Float, val y: Float, val depth: Float)

/** Una ubicación ya proyectada: la base sobre la superficie y la cabeza donde flota la tarjeta. */
class ProjectedLocation(
    val code: String,
    val baseX: Float,
    val baseY: Float,
    val headX: Float,
    val headY: Float,
    /** Distancia horizontal en metros desde la cámara. */
    val distanceM: Float,
    /** Si la cabeza (o la base) cae dentro de la pantalla y delante de la cámara. */
    val visible: Boolean
)

/** Matemática de matrices de OpenGL (4x4, columna-mayor) en Kotlin puro. */
object Projection {
    /** a · b, ambas columna-mayor. */
    fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(16)
        for (col in 0..3) {
            for (row in 0..3) {
                var s = 0f
                for (k in 0..3) s += a[k * 4 + row] * b[col * 4 + k]
                out[col * 4 + row] = s
            }
        }
        return out
    }

    /** Proyecta con una matriz vista-proyección. Devuelve null si el punto queda detrás de la cámara. */
    fun toScreen(viewProjection: FloatArray, p: Vec3, width: Int, height: Int): ScreenPoint? {
        val x = p.x.toFloat()
        val y = p.y.toFloat()
        val z = p.z.toFloat()
        val cx = viewProjection[0] * x + viewProjection[4] * y + viewProjection[8] * z + viewProjection[12]
        val cy = viewProjection[1] * x + viewProjection[5] * y + viewProjection[9] * z + viewProjection[13]
        val cw = viewProjection[3] * x + viewProjection[7] * y + viewProjection[11] * z + viewProjection[15]
        if (cw <= 0.01f) return null
        val sx = (cx / cw * 0.5f + 0.5f) * width
        val sy = (1f - (cy / cw * 0.5f + 0.5f)) * height
        return ScreenPoint(sx, sy, cw)
    }

    /** Lleva un punto del mundo al espacio de la cámara de pantalla (X derecha, Y arriba, -Z adelante). */
    fun toCameraSpace(view: FloatArray, p: Vec3): Vec3 {
        val x = p.x.toFloat()
        val y = p.y.toFloat()
        val z = p.z.toFloat()
        return Vec3(
            (view[0] * x + view[4] * y + view[8] * z + view[12]).toDouble(),
            (view[1] * x + view[5] * y + view[9] * z + view[13]).toDouble(),
            (view[2] * x + view[6] * y + view[10] * z + view[14]).toDouble()
        )
    }

    /** Distancia horizontal (se ignora la altura; el mundo de ARCore tiene +Y arriba). */
    fun horizontalDistance(a: Vec3, b: Vec3): Double = kotlin.math.hypot(a.x - b.x, a.z - b.z)

    /**
     * Proyecta una lista de ubicaciones. `sessionFromMap` convierte del marco del mapa al de la sesión
     * actual (null = ya están en el marco de la sesión, como al mapear).
     */
    fun projectLocations(
        locations: List<MapLocation>,
        sessionFromMap: Se3?,
        viewProjection: FloatArray,
        cameraPosition: Vec3,
        width: Int,
        height: Int
    ): List<ProjectedLocation> {
        val up = sessionFromMap?.rotate(Vec3.UP) ?: Vec3.UP
        val out = ArrayList<ProjectedLocation>(locations.size)
        for (loc in locations) {
            val base = sessionFromMap?.apply(loc.position) ?: loc.position
            val head = base + up * loc.cardHeightM
            val pb = toScreen(viewProjection, base, width, height)
            val ph = toScreen(viewProjection, head, width, height)
            val dist = horizontalDistance(cameraPosition, base).toFloat()
            // Solo se dibuja si base y cabeza quedan delante de la cámara y alguna cae en pantalla.
            val onScreen = ph != null && pb != null &&
                ((ph.x in 0f..width.toFloat() && ph.y in 0f..height.toFloat()) ||
                    (pb.x in 0f..width.toFloat() && pb.y in 0f..height.toFloat()))
            out.add(
                ProjectedLocation(
                    code = loc.code,
                    baseX = pb?.x ?: 0f,
                    baseY = pb?.y ?: 0f,
                    headX = ph?.x ?: 0f,
                    headY = ph?.y ?: 0f,
                    distanceM = dist,
                    visible = onScreen
                )
            )
        }
        return out
    }
}
