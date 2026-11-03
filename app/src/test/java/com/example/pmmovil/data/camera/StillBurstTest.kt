package com.example.pmmovil.data.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Random
import java.util.concurrent.Executor

class StillBurstTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val direct = Executor { it.run() }

    private fun texture(seed: Long): GrayPlane {
        val random = Random(seed)
        val w = 64
        val h = 48
        val noise = ByteArray(w * h) { random.nextInt(256).toByte() }
        // Suavizado simple para que la escena tenga estructura espacial.
        val out = ByteArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var sum = 0
            var n = 0
            for (dy in -2..2) for (dx in -2..2) {
                sum += noise[(y + dy).coerceIn(0, h - 1) * w + (x + dx).coerceIn(0, w - 1)].toInt() and 0xFF
                n++
            }
            out[y * w + x] = (sum / n).toByte()
        }
        return GrayPlane(w, h, out)
    }

    private val scene = texture(1)
    private val otherScene = texture(2)

    private fun sample(sharpness: Double, plane: GrayPlane = scene) = StillSample(sharpness, plane)

    /** Escribe la marca de cada disparo en el archivo, como lo haria la camara con la foto. */
    private class FakeShooter(private val marks: List<String>, private val results: List<Boolean> = emptyList()) : StillShooter {
        var calls = 0
        override fun shoot(target: File, onResult: (Boolean) -> Unit) {
            val i = calls++
            val ok = results.getOrElse(i) { true }
            if (ok) target.writeText(marks.getOrElse(i) { "?" })
            onResult(ok)
        }
    }

    private class FakeAnalyzer(private val byMark: Map<String, () -> StillSample?>) : StillAnalyzer {
        val analyzed = mutableListOf<String>()
        override fun analyze(file: File): StillSample? {
            val mark = file.readText()
            analyzed += mark
            return byMark.getValue(mark)()
        }
    }

    /** Camara que avisa del obturador en cada disparo, como la real; la pantalla solo debe enterarse del primero. */
    private class ShutterShooter(private val marks: List<String>) : StillShooter {
        var calls = 0
        override fun shoot(target: File, onResult: (Boolean) -> Unit) = shoot(target, {}, onResult)
        override fun shoot(target: File, onShutter: () -> Unit, onResult: (Boolean) -> Unit) {
            val i = calls++
            onShutter()
            target.writeText(marks.getOrElse(i) { "?" })
            onResult(true)
        }
    }

    private class Outcome {
        var saved: File? = null
        var savedCount = 0
        var errors = 0
        var firstShots = 0
        var shutters = 0
        val log = mutableListOf<String>()
    }

    private fun run(shooter: StillShooter, analyzer: StillAnalyzer, now: () -> Long = { 0L }): Outcome {
        val target = File(tmp.root, "foto.jpg")
        val out = Outcome()
        StillBurst(shooter, analyzer, direct, direct, now) { out.log += it }.capture(
            target = target,
            extraFile = { n -> File(tmp.root, "foto.jpg.r$n.tmp") },
            onFirstShot = { out.firstShots++ },
            onSaved = { out.saved = it; out.savedCount++ },
            onError = { out.errors++ },
            onShutter = { out.shutters++ }
        )
        return out
    }

    private fun filesLeft() = tmp.root.listFiles()!!.map { it.name }.sorted()

    private fun content() = File(tmp.root, "foto.jpg").readText()

    // --- Camino normal: la mayoria de las fotos ---

    @Test
    fun `una foto nitida se guarda sin reintentar`() {
        val shooter = FakeShooter(listOf("A"))
        val out = run(shooter, FakeAnalyzer(mapOf("A" to { sample(900.0) })))

        assertEquals(1, shooter.calls)
        assertEquals(1, out.savedCount)
        assertEquals(1, out.firstShots)
        assertEquals("A", content())
        assertEquals(listOf("foto.jpg"), filesLeft())
    }

    @Test
    fun `el obturador avisa una sola vez aunque haya disparo extra`() {
        val shooter = ShutterShooter(listOf("A", "B"))
        val out = run(shooter, FakeAnalyzer(mapOf("A" to { sample(100.0) }, "B" to { sample(900.0) })))

        assertEquals(2, shooter.calls)
        assertEquals(1, out.shutters)
        assertEquals(1, out.savedCount)
    }

    @Test
    fun `una camara que no avisa del obturador no rompe la captura`() {
        val shooter = FakeShooter(listOf("A"))
        val out = run(shooter, FakeAnalyzer(mapOf("A" to { sample(900.0) })))

        assertEquals(0, out.shutters)
        assertEquals(1, out.savedCount)
    }

    @Test
    fun `el log trae la linea de tiempo de la foto`() {
        var clock = 0L
        val shooter = object : StillShooter {
            override fun shoot(target: File, onResult: (Boolean) -> Unit) = shoot(target, {}, onResult)
            override fun shoot(target: File, onShutter: () -> Unit, onResult: (Boolean) -> Unit) {
                clock += 300
                onShutter()
                clock += 500
                target.writeText("A")
                onResult(true)
            }
        }
        val out = run(shooter, FakeAnalyzer(mapOf("A" to { clock += 150; sample(900.0) })), now = { clock })

        val line = out.log.single()
        assertTrue(line, line.contains("obturador=300 ms"))
        assertTrue(line, line.contains("guardada=800 ms"))
        assertTrue(line, line.contains("analizada=950 ms"))
    }

    @Test
    fun `si no se puede medir la foto se guarda sin reintentar`() {
        val shooter = FakeShooter(listOf("A"))
        val out = run(shooter, FakeAnalyzer(mapOf("A" to { null })))

        assertEquals(1, shooter.calls)
        assertEquals(1, out.savedCount)
        assertEquals("A", content())
    }

    @Test
    fun `si el analisis revienta se guarda la primera sin reintentar`() {
        val shooter = FakeShooter(listOf("A"))
        val out = run(shooter, FakeAnalyzer(mapOf("A" to { throw OutOfMemoryError("sin memoria") })))

        assertEquals(1, shooter.calls)
        assertEquals(1, out.savedCount)
        assertEquals(0, out.errors)
        assertEquals("A", content())
    }

    // --- Reintento ---

    @Test
    fun `una foto blanda y una extra nitida de la misma escena se queda con la extra`() {
        val shooter = FakeShooter(listOf("A", "B"))
        val out = run(
            shooter,
            FakeAnalyzer(mapOf("A" to { sample(50.0) }, "B" to { sample(400.0) }))
        )

        assertEquals(2, shooter.calls)
        assertEquals("solo UNA foto llega a la app", 1, out.savedCount)
        assertEquals("la elegida lleva el nombre de la primera", "foto.jpg", out.saved!!.name)
        assertEquals("B", content())
        assertEquals("no queda ningun archivo de mas", listOf("foto.jpg"), filesLeft())
    }

    @Test
    fun `una extra que no mejora lo bastante no abre una segunda`() {
        val shooter = FakeShooter(listOf("A", "B", "C"))
        val out = run(
            shooter,
            FakeAnalyzer(
                mapOf(
                    "A" to { sample(50.0) },
                    "B" to { sample(55.0) },   // 10 % mas: ruido de medicion, no cuenta
                    "C" to { sample(120.0) }   // no se llega a tomar
                )
            )
        )

        assertEquals("un solo disparo extra", 2, shooter.calls)
        assertEquals("A", content())
        assertEquals(1, out.savedCount)
        assertEquals(listOf("foto.jpg"), filesLeft())
    }

    @Test
    fun `el tope es una sola extra porque la segunda nunca ayudo en las pruebas con fotos reales`() {
        // 2-nov-2026, 16 reintentos en un Pixel: las 3 extras elegidas fueron la primera; la segunda se tomo en 4 casos y
        // no gano ninguna vez. Quitarla recorta ~1 s a la foto con reintento (de ~3.7 s a ~2.8 s).
        assertEquals(1, StillBurstPolicy.MAX_EXTRA_SHOTS)
    }

    @Test
    fun `una extra que ya es nitida corta el reintento`() {
        val shooter = FakeShooter(listOf("A", "B", "C"))
        run(
            shooter,
            FakeAnalyzer(mapOf("A" to { sample(50.0) }, "B" to { sample(260.0) }, "C" to { sample(900.0) }))
        )

        assertEquals("con B ya pasa el umbral, no hace falta C", 2, shooter.calls)
        assertEquals("B", content())
    }

    @Test
    fun `una extra mas nitida pero de otra escena se descarta y se deja de disparar`() {
        val shooter = FakeShooter(listOf("A", "B", "C"))
        val out = run(
            shooter,
            FakeAnalyzer(
                mapOf(
                    "A" to { sample(50.0) },
                    "B" to { sample(900.0, otherScene) },   // el usuario bajo la tableta al piso
                    "C" to { sample(900.0) }
                )
            )
        )

        assertEquals("tras ver otra escena no se sigue disparando", 2, shooter.calls)
        assertEquals("la evidencia NUNCA cambia a otra escena", "A", content())
        assertEquals(1, out.savedCount)
        assertEquals(listOf("foto.jpg"), filesLeft())
    }

    @Test
    fun `si ninguna extra mejora se queda la primera`() {
        val shooter = FakeShooter(listOf("A", "B", "C"))
        val out = run(
            shooter,
            FakeAnalyzer(mapOf("A" to { sample(80.0) }, "B" to { sample(70.0) }, "C" to { sample(85.0) }))
        )

        assertEquals("tope de un disparo extra", 2, shooter.calls)
        assertEquals("A", content())
        assertEquals(1, out.savedCount)
        assertEquals(listOf("foto.jpg"), filesLeft())
    }

    @Test
    fun `el umbral de reintento se puede subir para probar con un telefono de buena camara`() {
        // Una foto de 300 es "normal" con el umbral de produccion (200) y "blanda" si se sube a 500.
        val normal = FakeShooter(listOf("A"))
        run(normal, FakeAnalyzer(mapOf("A" to { sample(300.0) })))
        assertEquals("con 200 no reintenta", 1, normal.calls)

        val shooter = FakeShooter(listOf("A", "B"))
        val out = Outcome()
        StillBurst(
            shooter, FakeAnalyzer(mapOf("A" to { sample(300.0) }, "B" to { sample(700.0) })), direct, direct,
            retryBelow = 500.0
        ).capture(
            target = File(tmp.root, "otra.jpg"),
            extraFile = { n -> File(tmp.root, "otra.jpg.r$n.tmp") },
            onFirstShot = { out.firstShots++ },
            onSaved = { out.saved = it; out.savedCount++ },
            onError = { out.errors++ }
        )
        assertEquals("con 500 sí reintenta", 2, shooter.calls)
        assertEquals("B", File(tmp.root, "otra.jpg").readText())
    }

    @Test
    fun `una extra no elegida pasa por discard y la elegida no`() {
        val discarded = mutableListOf<String>()
        val shooter = FakeShooter(listOf("A", "B", "C"))
        val out = Outcome()
        StillBurst(
            shooter,
            FakeAnalyzer(mapOf("A" to { sample(50.0) }, "B" to { sample(400.0) }, "C" to { sample(900.0) })),
            direct, direct,
            discard = { discarded += it.name; it.delete() }
        ).capture(
            target = File(tmp.root, "foto.jpg"),
            extraFile = { n -> File(tmp.root, "foto.jpg.r$n.tmp") },
            onFirstShot = {},
            onSaved = { out.saved = it; out.savedCount++ },
            onError = { out.errors++ }
        )

        assertEquals("B", content())
        assertEquals("la elegida (B) se mueve, no se descarta", emptyList<String>(), discarded)
        assertEquals(listOf("foto.jpg"), filesLeft())
    }

    @Test
    fun `una extra que no se elige se entrega a discard`() {
        val discarded = mutableListOf<String>()
        val shooter = FakeShooter(listOf("A", "B"))
        StillBurst(
            shooter,
            FakeAnalyzer(mapOf("A" to { sample(50.0) }, "B" to { sample(900.0, otherScene) })),
            direct, direct,
            discard = { discarded += it.name; it.delete() }
        ).capture(
            target = File(tmp.root, "foto.jpg"),
            extraFile = { n -> File(tmp.root, "foto.jpg.r$n.tmp") },
            onFirstShot = {},
            onSaved = {},
            onError = {}
        )

        assertEquals("A", content())
        assertEquals(listOf("foto.jpg.r1.tmp"), discarded)
    }

    // --- Fallas ---

    @Test
    fun `si falla el primer disparo se avisa el error y no se guarda nada`() {
        val shooter = FakeShooter(listOf("A"), results = listOf(false))
        val out = run(shooter, FakeAnalyzer(emptyMap()))

        assertEquals(1, out.errors)
        assertEquals(0, out.savedCount)
        assertEquals(0, out.firstShots)
        assertNull(out.saved)
    }

    @Test
    fun `si falla un disparo extra no se pierde la primera foto`() {
        val shooter = FakeShooter(listOf("A", "B"), results = listOf(true, false))
        val out = run(shooter, FakeAnalyzer(mapOf("A" to { sample(50.0) })))

        assertEquals(2, shooter.calls)
        assertEquals(0, out.errors)
        assertEquals(1, out.savedCount)
        assertEquals("A", content())
        assertEquals(listOf("foto.jpg"), filesLeft())
    }

    @Test
    fun `si no se puede medir una extra se queda la primera`() {
        val shooter = FakeShooter(listOf("A", "B"))
        val out = run(shooter, FakeAnalyzer(mapOf("A" to { sample(50.0) }, "B" to { null })))

        assertEquals(1, out.savedCount)
        assertEquals("A", content())
        assertEquals(listOf("foto.jpg"), filesLeft())
    }

    @Test
    fun `si medir una extra revienta se queda la primera`() {
        val shooter = FakeShooter(listOf("A", "B"))
        val out = run(shooter, FakeAnalyzer(mapOf("A" to { sample(50.0) }, "B" to { throw IllegalStateException("x") })))

        assertEquals(1, out.savedCount)
        assertEquals("A", content())
        assertEquals(listOf("foto.jpg"), filesLeft())
    }

    // --- Tiempo ---

    @Test
    fun `pasado el presupuesto de tiempo no se empieza otro disparo`() {
        var clock = 0L
        val shooter = FakeShooter(listOf("A", "B"))
        val out = run(
            shooter,
            FakeAnalyzer(mapOf("A" to { clock += StillBurstPolicy.BUDGET_MS + 1; sample(50.0) }, "B" to { sample(900.0) })),
            now = { clock }
        )

        assertEquals("un primer disparo lento ya gasto el presupuesto", 1, shooter.calls)
        assertEquals(1, out.savedCount)
        assertEquals("A", content())
    }

    // --- Contrato con la pantalla ---

    @Test
    fun `onFirstShot se llama una vez y antes de guardar`() {
        val order = mutableListOf<String>()
        val shooter = FakeShooter(listOf("A", "B"))
        val analyzer = FakeAnalyzer(mapOf("A" to { sample(50.0) }, "B" to { sample(400.0) }))
        StillBurst(shooter, analyzer, direct, direct).capture(
            target = File(tmp.root, "foto.jpg"),
            extraFile = { n -> File(tmp.root, "foto.jpg.r$n.tmp") },
            onFirstShot = { order += "primera" },
            onSaved = { order += "guardada" },
            onError = { order += "error" }
        )

        assertEquals(listOf("primera", "guardada"), order)
    }

    @Test
    fun `el registro cuenta lo que paso`() {
        val out = run(
            FakeShooter(listOf("A", "B")),
            FakeAnalyzer(mapOf("A" to { sample(50.0) }, "B" to { sample(400.0) }))
        )

        val line = out.log.last()
        assertTrue(line, line.contains("primera=50"))
        assertTrue(line, line.contains("elegida=extra"))
        assertNotNull(out.saved)
    }
}
