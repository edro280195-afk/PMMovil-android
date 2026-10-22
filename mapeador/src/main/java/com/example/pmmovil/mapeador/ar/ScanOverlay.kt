package com.example.pmmovil.mapeador.ar

import android.opengl.GLES20
import android.util.Log
import com.example.pmmovil.mapeador.core.MeshBuffer
import com.example.pmmovil.mapeador.core.PointCloud
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.concurrent.CopyOnWriteArrayList

/** Puntos de anclaje (con los que se reconoce el lugar), dibujados como puntos redondos amarillos. */
class AnchorLayer(val cloud: PointCloud) {
    @Volatile
    var visible: Boolean = true

    @Volatile
    var alpha: Float = 1f

    /** Matriz de modelo (columna-mayor): del marco de la nube al de la sesión. null = son el mismo. */
    @Volatile
    var model: FloatArray? = null

    internal var vbo = 0
    internal var uploaded = 0

    internal fun resetGpu() {
        vbo = 0
        uploaded = 0
    }
}

/** La malla de superficies escaneadas: relleno translúcido más la cuadrícula de sus cuadriláteros. */
class MeshLayer(val mesh: MeshBuffer) {
    @Volatile
    var visible: Boolean = true

    @Volatile
    var alpha: Float = 1f

    @Volatile
    var model: FloatArray? = null

    internal var vbo = 0
    internal var triangleIbo = 0
    internal var lineIbo = 0
    internal var uploadedVertices = 0
    internal var uploadedQuads = 0
    internal var quadCapacity = 0

    // Refresco de los cuadriláteros quitados: se reescriben por tramos los índices ya subidos.
    internal var refreshCursor = 0
    internal var refreshedVersion = 0
    internal var passVersion = 0

    // Refresco de los vértices que se movieron (la superficie se afinó con cuadros nuevos): se vuelven a subir por tramos.
    internal var moveCursor = 0
    internal var movedVersion = 0
    internal var movePassVersion = 0

    internal fun resetGpu() {
        vbo = 0
        triangleIbo = 0
        lineIbo = 0
        uploadedVertices = 0
        uploadedQuads = 0
        quadCapacity = 0
        refreshCursor = 0
        refreshedVersion = 0
        passVersion = 0
        moveCursor = 0
        movedVersion = 0
        movePassVersion = 0
    }
}

/**
 * Dibuja el escaneo encima de la cámara. La malla se tiñe según hacia dónde mira cada superficie (piso azul, muros
 * turquesa, techo violeta), con luz desde la cámara; lo recién escaneado brilla en blanco y se apaga en un par de
 * segundos (se ve cómo el escaneo «va pintando» el espacio) y lo muy lejano se desvanece. Necesita un contexto de
 * GL ES 3 para usar índices de 32 bits.
 */
class ScanOverlay : GlOverlay {
    private val anchorLayers = CopyOnWriteArrayList<AnchorLayer>()
    private val meshLayers = CopyOnWriteArrayList<MeshLayer>()

    /**
     * Oculta lo que queda detrás de superficies reales, según la profundidad actual de ARCore. Apagada por omisión: con
     * la profundidad suave del Pixel escondía la mitad de la malla (a 3–4 m, el 57 %) y justo la lejana, así que parecía
     * que la malla flotaba delante de la persona y no se pegaba a paredes ni ventanas.
     */
    @Volatile
    var occlusion: Boolean = false

    /** Pinta la profundidad de ARCore como mapa de calor (rojo cerca, azul lejos) para ver si está alineada con la cámara. */
    @Volatile
    var depthHeatmap: Boolean = false

    /** Dibuja los planos que detecta ARCore (piso, muros, techo): apagado por omisión, para comparar con lo que mide la profundidad. */
    @Volatile
    var planesVisible: Boolean = false

    private var planeStaging: FloatBuffer = ByteBuffer.allocateDirect(2048 * PlaneGeometry.FLOATS_PER_VERTEX * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()
    private var meshProgram = 0
    private var anchorProgram = 0
    private var heatmapProgram = 0
    private var planeProgram = 0
    private var maxPointSize = 32f
    private var maxLineWidth = 1f

    private val vertexStaging: FloatBuffer = ByteBuffer.allocateDirect(VERTEX_CHUNK * MeshBuffer.STRIDE * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val vertexArray = FloatArray(VERTEX_CHUNK * MeshBuffer.STRIDE)
    private val triangleStaging: IntBuffer = ByteBuffer.allocateDirect(QUAD_CHUNK * 6 * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
    private val lineStaging: IntBuffer = ByteBuffer.allocateDirect(QUAD_CHUNK * 8 * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
    private val quadArray = IntArray(QUAD_CHUNK * 4)
    private val triangleArray = IntArray(QUAD_CHUNK * 6)
    private val lineArray = IntArray(QUAD_CHUNK * 8)
    private val anchorStaging: FloatBuffer = ByteBuffer.allocateDirect(ANCHOR_CHUNK * PointCloud.STRIDE * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val anchorArray = FloatArray(ANCHOR_CHUNK * PointCloud.STRIDE)
    private val deadStaging: FloatBuffer = ByteBuffer.allocateDirect(MeshBuffer.STRIDE * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val screenQuad: FloatBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        .apply { put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0) }

    fun addAnchors(layer: AnchorLayer) {
        anchorLayers.add(layer)
    }

    fun addMesh(layer: MeshLayer) {
        meshLayers.add(layer)
    }

    override fun onSurfaceCreated() {
        meshProgram = link(MESH_VERTEX, MESH_FRAGMENT)
        anchorProgram = link(ANCHOR_VERTEX, ANCHOR_FRAGMENT)
        heatmapProgram = link(HEATMAP_VERTEX, HEATMAP_FRAGMENT)
        // Los planos son un extra: si su sombreador fallara, no se tumba el escaneo.
        planeProgram = runCatching { link(PLANE_VERTEX, PLANE_FRAGMENT) }.onFailure { Log.w("MAPEADOR", "Planos de ARCore sin dibujo: ${it.message}") }.getOrDefault(0)
        val points = FloatArray(2)
        GLES20.glGetFloatv(GLES20.GL_ALIASED_POINT_SIZE_RANGE, points, 0)
        maxPointSize = points[1].coerceAtLeast(8f)
        val lines = FloatArray(2)
        GLES20.glGetFloatv(GLES20.GL_ALIASED_LINE_WIDTH_RANGE, lines, 0)
        maxLineWidth = lines[1].coerceAtLeast(1f)
        meshLayers.forEach { it.resetGpu() }
        anchorLayers.forEach { it.resetGpu() }
    }

    override fun draw(frame: OverlayFrame) {
        if (meshProgram == 0 || anchorProgram == 0) return
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        if (depthHeatmap) drawHeatmap(frame)

        // La malla se dibuja con prueba de profundidad (lo cercano tapa a lo lejano); las anclas van encima de todo.
        GLES20.glDepthMask(true)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        for (layer in meshLayers) {
            if (layer.visible) drawMesh(layer, frame)
        }
        if (planesVisible && frame.planes.isNotEmpty()) drawPlanes(frame)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        for (layer in anchorLayers) {
            if (layer.visible) drawAnchors(layer, frame)
        }

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES20.glDepthMask(true)
        GLES20.glColorMask(true, true, true, true)
        GLES20.glDisable(GLES20.GL_BLEND)
        checkGl("escaneo")
    }

    // ---------- profundidad ----------

    /** Deja lista la textura de profundidad (unidad 1) y su transformación de coordenadas para un programa que la use. */
    private fun bindDepth(program: Int, depth: DepthView?) {
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "u_UseDepth"), if (depth != null) 1f else 0f)
        // El muestreador siempre apunta a la unidad 1 (nunca a la 0, donde vive la textura externa de la cámara).
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "u_DepthTex"), 1)
        if (depth == null) return
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, depth.textureId)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "u_UvOrigin"), depth.originS, depth.originT)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "u_UvDx"), depth.dxS, depth.dxT)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "u_UvDy"), depth.dyS, depth.dyT)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    }

    private fun drawHeatmap(f: OverlayFrame) {
        val depth = f.depth ?: return
        if (heatmapProgram == 0) return
        GLES20.glUseProgram(heatmapProgram)
        bindDepth(heatmapProgram, depth)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        val aNdc = GLES20.glGetAttribLocation(heatmapProgram, "a_Ndc")
        screenQuad.position(0)
        GLES20.glEnableVertexAttribArray(aNdc)
        GLES20.glVertexAttribPointer(aNdc, 2, GLES20.GL_FLOAT, false, 0, screenQuad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aNdc)
        checkGl("mapa de profundidad")
    }

    // ---------- malla ----------

    private fun drawMesh(l: MeshLayer, f: OverlayFrame) {
        syncMesh(l)
        val quads = l.uploadedQuads
        if (quads == 0) return
        val program = meshProgram
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "u_ViewProj"), 1, false, f.viewProjection, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "u_Model"), 1, false, l.model ?: IDENTITY, 0)
        GLES20.glUniform3f(GLES20.glGetUniformLocation(program, "u_CamPos"), f.cameraPosition.x.toFloat(), f.cameraPosition.y.toFloat(), f.cameraPosition.z.toFloat())
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "u_Now"), f.nowSeconds)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "u_Alpha"), l.alpha)
        val pass = GLES20.glGetUniformLocation(program, "u_Pass")
        bindDepth(program, if (occlusion) f.depth else null)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, l.vbo)
        val aPos = GLES20.glGetAttribLocation(program, "a_Pos")
        val aNormal = GLES20.glGetAttribLocation(program, "a_Normal")
        val aBorn = GLES20.glGetAttribLocation(program, "a_Born")
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, VERTEX_BYTES, 0)
        GLES20.glEnableVertexAttribArray(aNormal)
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, VERTEX_BYTES, 12)
        GLES20.glEnableVertexAttribArray(aBorn)
        GLES20.glVertexAttribPointer(aBorn, 1, GLES20.GL_FLOAT, false, VERTEX_BYTES, 24)

        // 1) Solo profundidad, un poco más lejos, para que después el relleno y las líneas pasen en la superficie
        //    más cercana. El sesgo va en el sombreador (en unidades de z/w): ~1 mm a 1 m, ~1 cm a 3 m.
        val bias = GLES20.glGetUniformLocation(program, "u_Bias")
        GLES20.glUniform1f(pass, 0f)
        GLES20.glUniform1f(bias, DEPTH_BIAS)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, l.triangleIbo)
        GLES20.glColorMask(false, false, false, false)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, quads * 6, GLES20.GL_UNSIGNED_INT, 0)
        GLES20.glColorMask(true, true, true, true)

        // 2) Relleno translúcido y 3) cuadrícula (un poco hacia la cámara), sin escribir profundidad.
        GLES20.glDepthMask(false)
        GLES20.glUniform1f(bias, 0f)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, quads * 6, GLES20.GL_UNSIGNED_INT, 0)

        GLES20.glUniform1f(pass, 1f)
        GLES20.glUniform1f(bias, -DEPTH_BIAS)
        GLES20.glLineWidth(minOf(1.5f, maxLineWidth))
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, l.lineIbo)
        GLES20.glDrawElements(GLES20.GL_LINES, quads * 8, GLES20.GL_UNSIGNED_INT, 0)
        GLES20.glDepthMask(true)
        checkGl("malla")

        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aNormal)
        GLES20.glDisableVertexAttribArray(aBorn)
    }

    /** Sube a la GPU los vértices y cuadriláteros nuevos (por tramos), sin pasar de los vértices ya subidos. */
    private fun syncMesh(l: MeshLayer) {
        if (l.vbo == 0) {
            val ids = IntArray(1)
            GLES20.glGenBuffers(1, ids, 0)
            l.vbo = ids[0]
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, l.vbo)
            // Un vértice de más al final, con normal cero: los cuadriláteros quitados apuntan a él y el sombreador lo manda
            // fuera de la pantalla, así no queda ningún punto suelto dibujado.
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, (l.mesh.maxVertices + 1) * VERTEX_BYTES, null, GLES20.GL_DYNAMIC_DRAW)
            deadStaging.position(0)
            GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, l.mesh.maxVertices * VERTEX_BYTES, VERTEX_BYTES, deadStaging)
            l.uploadedVertices = 0
        } else {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, l.vbo)
        }
        val totalVertices = l.mesh.vertexCount
        if (totalVertices > l.uploadedVertices) {
            val chunk = minOf(totalVertices - l.uploadedVertices, VERTEX_CHUNK)
            l.mesh.copyVertices(l.uploadedVertices, l.uploadedVertices + chunk, vertexArray)
            vertexStaging.clear()
            vertexStaging.put(vertexArray, 0, chunk * MeshBuffer.STRIDE)
            vertexStaging.position(0)
            GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, l.uploadedVertices * VERTEX_BYTES, chunk * VERTEX_BYTES, vertexStaging)
            l.uploadedVertices += chunk
        }

        val totalQuads = l.mesh.quadCount
        if (l.triangleIbo == 0 || totalQuads > l.quadCapacity) growIndexBuffers(l, totalQuads)
        if (totalQuads > l.uploadedQuads) {
            val want = minOf(totalQuads - l.uploadedQuads, QUAD_CHUNK)
            l.mesh.copyQuads(l.uploadedQuads, l.uploadedQuads + want, quadArray)
            // Solo se suben los cuadriláteros cuyos vértices ya están en la GPU.
            var n = 0
            val deadIndex = l.mesh.maxVertices
            while (n < want) {
                var a = quadArray[n * 4]
                var b = quadArray[n * 4 + 1]
                var c = quadArray[n * 4 + 2]
                var d = quadArray[n * 4 + 3]
                if ((a or b or c or d) == 0) {
                    // Cuadrilátero quitado (salió como ceros): apunta al vértice muerto.
                    a = deadIndex
                    b = deadIndex
                    c = deadIndex
                    d = deadIndex
                } else if (maxOf(maxOf(a, b), maxOf(c, d)) >= l.uploadedVertices) break
                triangleArray[n * 6] = a
                triangleArray[n * 6 + 1] = b
                triangleArray[n * 6 + 2] = c
                triangleArray[n * 6 + 3] = a
                triangleArray[n * 6 + 4] = c
                triangleArray[n * 6 + 5] = d
                lineArray[n * 8] = a
                lineArray[n * 8 + 1] = b
                lineArray[n * 8 + 2] = b
                lineArray[n * 8 + 3] = c
                lineArray[n * 8 + 4] = c
                lineArray[n * 8 + 5] = d
                lineArray[n * 8 + 6] = d
                lineArray[n * 8 + 7] = a
                n++
            }
            if (n > 0) {
                triangleStaging.clear()
                triangleStaging.put(triangleArray, 0, n * 6)
                triangleStaging.position(0)
                GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, l.triangleIbo)
                GLES20.glBufferSubData(GLES20.GL_ELEMENT_ARRAY_BUFFER, l.uploadedQuads * 6 * 4, n * 6 * 4, triangleStaging)
                lineStaging.clear()
                lineStaging.put(lineArray, 0, n * 8)
                lineStaging.position(0)
                GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, l.lineIbo)
                GLES20.glBufferSubData(GLES20.GL_ELEMENT_ARRAY_BUFFER, l.uploadedQuads * 8 * 4, n * 8 * 4, lineStaging)
                l.uploadedQuads += n
            }
        }
        refreshRemoved(l)
        refreshMoved(l)
    }

    /**
     * Los vértices que ya estaban en la GPU se vuelven a subir por tramos cuando el volumen afinó su posición. Sin esto,
     * la malla en pantalla se queda con la geometría del primer reconocimiento aunque el volumen ya la haya corregido.
     */
    private fun refreshMoved(l: MeshLayer) {
        val version = l.mesh.moveVersion
        if (l.moveCursor == 0) {
            if (l.movedVersion == version) return
            l.movePassVersion = version
        }
        val to = minOf(l.moveCursor + MOVE_CHUNK, l.uploadedVertices)
        if (to > l.moveCursor) {
            val from = l.moveCursor
            val n = to - from
            l.mesh.copyVertices(from, to, vertexArray)
            vertexStaging.clear()
            vertexStaging.put(vertexArray, 0, n * MeshBuffer.STRIDE)
            vertexStaging.position(0)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, l.vbo)
            GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, from * VERTEX_BYTES, n * VERTEX_BYTES, vertexStaging)
        }
        l.moveCursor = to
        if (l.moveCursor >= l.uploadedVertices) {
            l.moveCursor = 0
            l.movedVersion = l.movePassVersion
        }
    }

    /**
     * Cuando se quitan cuadriláteros (porque otros cuadros los contradijeron) hay que borrarlos también de la GPU: se
     * reescriben, por tramos y sin dejar de dibujar, los índices ya subidos (los quitados apuntan al vértice muerto).
     */
    private fun refreshRemoved(l: MeshLayer) {
        val version = l.mesh.removalVersion
        if (l.refreshCursor == 0) {
            if (l.refreshedVersion == version) return
            l.passVersion = version
        }
        val to = minOf(l.refreshCursor + REFRESH_CHUNK, l.uploadedQuads)
        if (to > l.refreshCursor) {
            val from = l.refreshCursor
            val n = to - from
            val deadIndex = l.mesh.maxVertices
            l.mesh.copyQuads(from, to, quadArray)
            for (i in 0 until n) {
                var a = quadArray[i * 4]
                var b = quadArray[i * 4 + 1]
                var c = quadArray[i * 4 + 2]
                var d = quadArray[i * 4 + 3]
                if ((a or b or c or d) == 0) {
                    a = deadIndex
                    b = deadIndex
                    c = deadIndex
                    d = deadIndex
                }
                triangleArray[i * 6] = a
                triangleArray[i * 6 + 1] = b
                triangleArray[i * 6 + 2] = c
                triangleArray[i * 6 + 3] = a
                triangleArray[i * 6 + 4] = c
                triangleArray[i * 6 + 5] = d
                lineArray[i * 8] = a
                lineArray[i * 8 + 1] = b
                lineArray[i * 8 + 2] = b
                lineArray[i * 8 + 3] = c
                lineArray[i * 8 + 4] = c
                lineArray[i * 8 + 5] = d
                lineArray[i * 8 + 6] = d
                lineArray[i * 8 + 7] = a
            }
            triangleStaging.clear()
            triangleStaging.put(triangleArray, 0, n * 6)
            triangleStaging.position(0)
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, l.triangleIbo)
            GLES20.glBufferSubData(GLES20.GL_ELEMENT_ARRAY_BUFFER, from * 6 * 4, n * 6 * 4, triangleStaging)
            lineStaging.clear()
            lineStaging.put(lineArray, 0, n * 8)
            lineStaging.position(0)
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, l.lineIbo)
            GLES20.glBufferSubData(GLES20.GL_ELEMENT_ARRAY_BUFFER, from * 8 * 4, n * 8 * 4, lineStaging)
        }
        l.refreshCursor = to
        if (l.refreshCursor >= l.uploadedQuads) {
            l.refreshCursor = 0
            l.refreshedVersion = l.passVersion
        }
    }

    /** Los buffers de índices crecen al doble; al crecer se vuelven a subir los cuadriláteros desde el principio. */
    private fun growIndexBuffers(l: MeshLayer, needed: Int) {
        val old = intArrayOf(l.triangleIbo, l.lineIbo).filter { it != 0 }.toIntArray()
        if (old.isNotEmpty()) GLES20.glDeleteBuffers(old.size, old, 0)
        var capacity = maxOf(l.quadCapacity * 2, INITIAL_QUADS)
        while (capacity < needed) capacity *= 2
        val ids = IntArray(2)
        GLES20.glGenBuffers(2, ids, 0)
        l.triangleIbo = ids[0]
        l.lineIbo = ids[1]
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, l.triangleIbo)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, capacity * 6 * 4, null, GLES20.GL_DYNAMIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, l.lineIbo)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, capacity * 8 * 4, null, GLES20.GL_DYNAMIC_DRAW)
        l.quadCapacity = capacity
        l.uploadedQuads = 0
    }

    // ---------- anclas ----------

    private fun drawAnchors(l: AnchorLayer, f: OverlayFrame) {
        if (l.vbo == 0) {
            val ids = IntArray(1)
            GLES20.glGenBuffers(1, ids, 0)
            l.vbo = ids[0]
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, l.vbo)
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, l.cloud.maxPoints * VERTEX_BYTES, null, GLES20.GL_DYNAMIC_DRAW)
            l.uploaded = 0
        } else {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, l.vbo)
        }
        val total = l.cloud.count
        if (total > l.uploaded) {
            val chunk = minOf(total - l.uploaded, ANCHOR_CHUNK)
            l.cloud.copyRange(l.uploaded, l.uploaded + chunk, anchorArray)
            anchorStaging.clear()
            anchorStaging.put(anchorArray, 0, chunk * PointCloud.STRIDE)
            anchorStaging.position(0)
            GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, l.uploaded * VERTEX_BYTES, chunk * VERTEX_BYTES, anchorStaging)
            l.uploaded += chunk
        }
        if (l.uploaded == 0) return

        val program = anchorProgram
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "u_ViewProj"), 1, false, f.viewProjection, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "u_Model"), 1, false, l.model ?: IDENTITY, 0)
        GLES20.glUniform3f(GLES20.glGetUniformLocation(program, "u_CamPos"), f.cameraPosition.x.toFloat(), f.cameraPosition.y.toFloat(), f.cameraPosition.z.toFloat())
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "u_SizeScale"), 0.03f * f.focalPx)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "u_MaxSize"), minOf(16f, maxPointSize))
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "u_Alpha"), l.alpha)
        val aPos = GLES20.glGetAttribLocation(program, "a_Pos")
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, VERTEX_BYTES, 0)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, l.uploaded)
        GLES20.glDisableVertexAttribArray(aPos)
    }

    // ---------- planos de ARCore ----------

    /** Dibuja los planos de ARCore con prueba de profundidad (los tapa la malla) y sin escribirla; un poco hacia atrás para no parpadear con la malla. */
    private fun drawPlanes(f: OverlayFrame) {
        val program = planeProgram
        if (program == 0) return
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "u_ViewProj"), 1, false, f.viewProjection, 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "u_Cell"), PLANE_CELL_M)
        val color = GLES20.glGetUniformLocation(program, "u_Color")
        val aPos = GLES20.glGetAttribLocation(program, "a_Pos")
        val aLocal = GLES20.glGetAttribLocation(program, "a_Local")
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glDepthMask(false)
        GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
        GLES20.glPolygonOffset(2f, 2f)
        for (plane in f.planes) {
            val n = plane.vertices.size
            if (planeStaging.capacity() < n) {
                planeStaging = ByteBuffer.allocateDirect(n * 4 * 2).order(ByteOrder.nativeOrder()).asFloatBuffer()
            }
            planeStaging.clear()
            planeStaging.put(plane.vertices)
            when (plane.kind) {
                HitKind.FLOOR -> GLES20.glUniform4f(color, 0.22f, 0.56f, 1.00f, 0.13f)
                HitKind.CEILING -> GLES20.glUniform4f(color, 0.70f, 0.48f, 1.00f, 0.13f)
                else -> GLES20.glUniform4f(color, 0.10f, 0.90f, 0.88f, 0.13f)
            }
            val stride = PlaneGeometry.FLOATS_PER_VERTEX * 4
            planeStaging.position(0)
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, stride, planeStaging)
            planeStaging.position(3)
            GLES20.glEnableVertexAttribArray(aLocal)
            GLES20.glVertexAttribPointer(aLocal, 2, GLES20.GL_FLOAT, false, stride, planeStaging)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, plane.vertexCount)
        }
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aLocal)
        GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL)
        GLES20.glDepthMask(true)
        checkGl("planos")
    }

    // ---------- sombreadores ----------

    private fun link(vertexSource: String, fragmentSource: String): Int {
        val vertex = compile(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertex)
        GLES20.glAttachShader(program, fragment)
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { "No se pudo preparar el dibujo del escaneo: ${GLES20.glGetProgramInfoLog(program)}" }
        return program
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { "No se pudo compilar un sombreador: ${GLES20.glGetShaderInfoLog(shader)}" }
        return shader
    }

    private var glErrorsLogged = 0

    /** Anota (como mucho unas cuantas veces) un error de GL: dibujar mal en silencio es lo más difícil de encontrar. */
    private fun checkGl(where: String) {
        val code = GLES20.glGetError()
        if (code != GLES20.GL_NO_ERROR && glErrorsLogged < 8) {
            glErrorsLogged++
            Log.w("MAPEADOR", "Error de GL 0x${Integer.toHexString(code)} al dibujar: $where")
        }
    }

    private companion object {
        const val DEPTH_BIAS = 1.2e-4f
        const val VERTEX_BYTES = MeshBuffer.STRIDE * 4
        const val VERTEX_CHUNK = 30_000
        const val QUAD_CHUNK = 30_000
        const val ANCHOR_CHUNK = 20_000
        const val INITIAL_QUADS = 50_000
        const val REFRESH_CHUNK = 20_000

        /** Vértices que se vuelven a subir por cuadro (28 B cada uno): un recorrido de oficina se barre en 2–4 cuadros. */
        const val MOVE_CHUNK = 10_000
        const val PLANE_CELL_M = 0.25f
        val IDENTITY = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

        val MESH_VERTEX = """
            uniform mat4 u_ViewProj;
            uniform mat4 u_Model;
            uniform vec3 u_CamPos;
            uniform float u_Now;
            uniform float u_Alpha;
            uniform float u_Pass;
            uniform float u_Bias;
            attribute vec3 a_Pos;
            attribute vec3 a_Normal;
            attribute float a_Born;
            varying vec4 v_Color;
            varying vec4 v_Clip;
            void main() {
                if (dot(a_Normal, a_Normal) < 0.25) {
                    // Vértice «muerto» (de un cuadrilátero quitado): fuera del volumen de recorte, no se dibuja.
                    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
                    v_Clip = vec4(2.0, 2.0, 2.0, 1.0);
                    v_Color = vec4(0.0);
                    return;
                }
                vec4 wp = u_Model * vec4(a_Pos, 1.0);
                vec3 toCam = u_CamPos - wp.xyz;
                float dist = max(length(toCam), 0.05);
                vec4 clip = u_ViewProj * wp;
                v_Clip = clip;
                gl_Position = clip;
                gl_Position.z += u_Bias * gl_Position.w;
                vec3 n = normalize((u_Model * vec4(a_Normal, 0.0)).xyz);
                float ndv = abs(dot(n, toCam / dist));
                float light = 0.55 + 0.45 * ndv;
                vec3 floorC = vec3(0.22, 0.56, 1.00);
                vec3 wallC  = vec3(0.10, 0.90, 0.88);
                vec3 ceilC  = vec3(0.70, 0.48, 1.00);
                vec3 base = n.y > 0.0
                    ? mix(wallC, floorC, smoothstep(0.25, 0.8, n.y))
                    : mix(wallC, ceilC, smoothstep(0.25, 0.8, -n.y));
                float fresh = clamp(exp(-(u_Now - a_Born) * 1.4), 0.0, 1.0);
                vec3 c = mix(base * light, vec3(1.0), fresh * 0.9);
                float fade = 1.0 - smoothstep(4.5, 7.0, dist);
                float a = (u_Pass < 0.5 ? 0.26 : 0.55) * u_Alpha * fade;
                v_Color = vec4(c, clamp(a + fresh * 0.30 * u_Alpha * fade, 0.0, 1.0));
            }
        """.trimIndent()

        /**
         * Oclusión: se compara la profundidad del fragmento con la profundidad actual de ARCore en ese punto de la
         * pantalla; si la malla queda más lejos que la superficie real (con un margen por el error de la profundidad),
         * se desvanece. La textura guarda milímetros en dos bytes (bajo en R, alto en G).
         */
        val MESH_FRAGMENT = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            precision highp sampler2D;
            #else
            precision mediump float;
            precision mediump sampler2D;
            #endif
            uniform sampler2D u_DepthTex;
            uniform float u_UseDepth;
            uniform vec2 u_UvOrigin;
            uniform vec2 u_UvDx;
            uniform vec2 u_UvDy;
            varying vec4 v_Color;
            varying vec4 v_Clip;
            void main() {
                float a = v_Color.a;
                if (u_UseDepth > 0.5) {
                    vec2 ndc = v_Clip.xy / v_Clip.w;
                    vec2 st = u_UvOrigin + (ndc.x + 1.0) * u_UvDx + (ndc.y + 1.0) * u_UvDy;
                    vec4 d = texture2D(u_DepthTex, st);
                    float realM = (d.r * 255.0 + d.g * 65280.0) * 0.001;
                    if (realM > 0.05) {
                        float margin = max(0.30, 0.25 * realM);
                        a *= 1.0 - smoothstep(margin, margin * 2.0, v_Clip.w - realM);
                    }
                }
                gl_FragColor = vec4(v_Color.rgb, a);
            }
        """.trimIndent()

        val HEATMAP_VERTEX = """
            attribute vec2 a_Ndc;
            varying vec2 v_Ndc;
            void main() {
                v_Ndc = a_Ndc;
                gl_Position = vec4(a_Ndc, 0.0, 1.0);
            }
        """.trimIndent()

        val HEATMAP_FRAGMENT = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            precision highp sampler2D;
            #else
            precision mediump float;
            precision mediump sampler2D;
            #endif
            uniform sampler2D u_DepthTex;
            uniform vec2 u_UvOrigin;
            uniform vec2 u_UvDx;
            uniform vec2 u_UvDy;
            varying vec2 v_Ndc;
            void main() {
                vec2 st = u_UvOrigin + (v_Ndc.x + 1.0) * u_UvDx + (v_Ndc.y + 1.0) * u_UvDy;
                vec4 d = texture2D(u_DepthTex, st);
                float m = (d.r * 255.0 + d.g * 65280.0) * 0.001;
                if (m < 0.05) {
                    gl_FragColor = vec4(0.0);
                    return;
                }
                float t = clamp((m - 0.3) / 4.7, 0.0, 1.0);
                vec3 c = t < 0.33 ? mix(vec3(1.0, 0.1, 0.1), vec3(1.0, 0.9, 0.1), t / 0.33)
                       : t < 0.66 ? mix(vec3(1.0, 0.9, 0.1), vec3(0.1, 0.9, 0.3), (t - 0.33) / 0.33)
                       : mix(vec3(0.1, 0.9, 0.3), vec3(0.1, 0.3, 1.0), (t - 0.66) / 0.34);
                gl_FragColor = vec4(c, 0.45);
            }
        """.trimIndent()

        /** Planos de ARCore: relleno muy tenue con una cuadrícula cada 25 cm (en las coordenadas locales del plano). */
        val PLANE_VERTEX = """
            uniform mat4 u_ViewProj;
            attribute vec3 a_Pos;
            attribute vec2 a_Local;
            varying vec2 v_Local;
            void main() {
                v_Local = a_Local;
                gl_Position = u_ViewProj * vec4(a_Pos, 1.0);
            }
        """.trimIndent()

        val PLANE_FRAGMENT = """
            precision mediump float;
            uniform vec4 u_Color;
            uniform float u_Cell;
            varying vec2 v_Local;
            void main() {
                vec2 f = fract(v_Local / u_Cell);
                float line = (f.x < 0.04 || f.y < 0.04) ? 1.0 : 0.0;
                gl_FragColor = vec4(u_Color.rgb, u_Color.a + 0.38 * line);
            }
        """.trimIndent()

        val ANCHOR_VERTEX = """
            uniform mat4 u_ViewProj;
            uniform mat4 u_Model;
            uniform vec3 u_CamPos;
            uniform float u_SizeScale;
            uniform float u_MaxSize;
            uniform float u_Alpha;
            attribute vec3 a_Pos;
            varying vec4 v_Color;
            void main() {
                vec4 wp = u_Model * vec4(a_Pos, 1.0);
                float dist = max(length(u_CamPos - wp.xyz), 0.05);
                gl_Position = u_ViewProj * wp;
                gl_PointSize = clamp(u_SizeScale / dist, 6.0, u_MaxSize);
                v_Color = vec4(1.0, 0.82, 0.18, u_Alpha);
            }
        """.trimIndent()

        val ANCHOR_FRAGMENT = """
            precision mediump float;
            varying vec4 v_Color;
            void main() {
                float r = length(gl_PointCoord - vec2(0.5)) * 2.0;
                if (r > 1.0) discard;
                gl_FragColor = vec4(v_Color.rgb, v_Color.a * smoothstep(1.0, 0.55, r));
            }
        """.trimIndent()
    }
}
