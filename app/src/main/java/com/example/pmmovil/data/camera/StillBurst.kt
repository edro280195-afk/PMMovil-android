package com.example.pmmovil.data.camera

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executor
import kotlin.math.roundToInt

/** Lo que se midio de una foto recien tomada. */
data class StillSample(val sharpness: Double, val thumbnail: GrayPlane)

/** Toma una foto en [target]. [onResult] se llama una vez, en el hilo principal: true si quedo guardada. */
interface StillShooter {
    fun shoot(target: File, onResult: (Boolean) -> Unit)
}

/** Mide una foto ya guardada. Bloqueante: se llama siempre fuera del hilo principal. Null si no se pudo leer. */
interface StillAnalyzer {
    fun analyze(file: File): StillSample?
}

/**
 * Reglas del reintento invisible. Todo esto ocurre sin que el operador haga nada: toca el obturador una vez y lo unico
 * que puede notar es que, en una de cada veinte fotos, tarda un segundo y medio mas.
 */
object StillBurstPolicy {
    /** Apagador: en false se toma una sola foto, exactamente como antes del reintento. */
    const val ENABLED = true

    /**
     * Por debajo de esta nitidez se intenta de nuevo. Es la misma cifra con la que el servidor decide afilar una foto
     * (FotoMejora:NitidezBaja = 200), calibrada con 700 fotos reales: toca cerca de 8 % de las fotos.
     */
    const val RETRY_BELOW = 200.0

    /** Una foto extra solo vale si se parece a la primera a ese nivel (0-1). Mejor perder una mejora que guardar OTRA escena. */
    const val MIN_SIMILARITY = 0.55

    /** La extra tiene que ser al menos 15 % mas nitida que la mejor hasta ahora, para no cambiar por puro ruido de medicion. */
    const val MIN_GAIN = 1.15

    /**
     * Un solo disparo extra. En la prueba del 2-nov-2026 con 16 reintentos, las 3 extras elegidas fueron siempre la primera;
     * la segunda se tomo en 4 casos y no gano ninguna vez, y costaba ~1 s mas (la foto con reintento pasaba de ~2.8 s a ~3.7 s).
     */
    const val MAX_EXTRA_SHOTS = 1

    /** Tiempo total desde el primer disparo; pasado esto no arranca ningun disparo extra. */
    const val BUDGET_MS = 2_500L

    fun needsRetry(sharpness: Double, retryBelow: Double = RETRY_BELOW): Boolean = sharpness < retryBelow

    fun accepts(bestSharpness: Double, candidateSharpness: Double, similarity: Double): Boolean =
        similarity >= MIN_SIMILARITY && candidateSharpness >= bestSharpness * MIN_GAIN
}

/**
 * Toma una foto y, si sale blanda, toma otra de inmediato y se queda con la mas nitida de la MISMA escena.
 * Solo UNA foto llega a [capture]'s `onSaved`: la elegida, con el nombre de archivo de la primera, de modo que el resto de
 * la app no se entera de nada. Lo que se sube al API pesa igual que antes.
 *
 * Hilos: `capture` y todos los callbacks de [StillShooter] van en [main]; el analisis y los archivos, en [work]. El estado
 * de cada captura solo se toca desde [main].
 *
 * Garantias:
 *  - `onSaved` se llama una sola vez, con el archivo [capture]`.target`, salvo que el PRIMER disparo falle (entonces `onError`).
 *  - Un disparo extra que falla, o un analisis que no se puede hacer, nunca pierde la primera foto.
 *  - Los archivos de los disparos descartados se borran.
 */
class StillBurst(
    private val shooter: StillShooter,
    private val analyzer: StillAnalyzer,
    private val main: Executor,
    private val work: Executor,
    private val now: () -> Long = System::currentTimeMillis,
    /** Umbral de nitidez para reintentar. En produccion es [StillBurstPolicy.RETRY_BELOW]; en pruebas con telefono se sube. */
    private val retryBelow: Double = StillBurstPolicy.RETRY_BELOW,
    /** Que hacer con un disparo extra que no se eligio. Por omision se borra; el debug los conserva para calibrar. */
    private val discard: (File) -> Unit = { it.delete() },
    private val log: (String) -> Unit = {}
) {
    private class Candidate(val file: File, val sharpness: Double, val similarity: Double)

    private class Run(
        val target: File,
        val extraFile: (Int) -> File,
        val onSaved: (File) -> Unit,
        val startedAt: Long,
        val first: StillSample
    ) {
        var best = Candidate(target, first.sharpness, 1.0)
        val extras = mutableListOf<Candidate>()
        var stop = false
    }

    fun capture(
        target: File,
        extraFile: (Int) -> File,
        onFirstShot: () -> Unit,
        onSaved: (File) -> Unit,
        onError: () -> Unit
    ) {
        val startedAt = now()
        shooter.shoot(target) { ok ->
            if (!ok) {
                onError()
                return@shoot
            }
            onFirstShot()
            work.execute {
                val first = safeAnalyze(target)
                main.execute {
                    if (first == null || !StillBurstPolicy.needsRetry(first.sharpness, retryBelow)) {
                        log("sin reintento: nitidez=${first?.sharpness?.roundToInt()}")
                        onSaved(target)
                    } else {
                        nextShot(Run(target, extraFile, onSaved, startedAt, first))
                    }
                }
            }
        }
    }

    private fun nextShot(run: Run) {
        val elapsed = now() - run.startedAt
        if (run.stop ||
            run.extras.size >= StillBurstPolicy.MAX_EXTRA_SHOTS ||
            elapsed > StillBurstPolicy.BUDGET_MS ||
            !StillBurstPolicy.needsRetry(run.best.sharpness, retryBelow)
        ) {
            finish(run)
            return
        }

        val file = run.extraFile(run.extras.size + 1)
        shooter.shoot(file) { ok ->
            if (!ok) {
                work.execute { file.delete() }
                finish(run)
                return@shoot
            }
            work.execute {
                val sample = safeAnalyze(file)
                val similarity = sample?.let { StillQuality.similarity(run.first.thumbnail, it.thumbnail) } ?: 0.0
                main.execute {
                    val candidate = Candidate(file, sample?.sharpness ?: 0.0, similarity)
                    run.extras.add(candidate)
                    when {
                        sample == null -> run.stop = true
                        // Otra escena: el usuario ya aparto la tableta, no tiene caso seguir disparando.
                        similarity < StillBurstPolicy.MIN_SIMILARITY -> run.stop = true
                        StillBurstPolicy.accepts(run.best.sharpness, candidate.sharpness, similarity) -> run.best = candidate
                    }
                    nextShot(run)
                }
            }
        }
    }

    private fun finish(run: Run) {
        work.execute {
            val winner = run.best.file
            if (winner != run.target && !replace(run.target, winner)) {
                log("no se pudo reemplazar la primera foto; se queda la primera")
            }
            for (extra in run.extras) {
                if (extra.file.exists()) discard(extra.file)
            }
            main.execute {
                val extras = run.extras.joinToString(",") { "${it.sharpness.roundToInt()}@${(it.similarity * 100).roundToInt()}%" }
                log(
                    "reintento: primera=${run.first.sharpness.roundToInt()} extras=[$extras] " +
                        "elegida=${if (winner == run.target) "primera" else "extra"} en ${now() - run.startedAt} ms"
                )
                run.onSaved(run.target)
            }
        }
    }

    private fun safeAnalyze(file: File): StillSample? =
        try {
            analyzer.analyze(file)
        } catch (e: Throwable) {
            log("no se pudo analizar ${file.name}: ${e.message}")
            null
        }

    /** Pone [winner] con el nombre de [target]. Mover en la misma carpeta es atomico: la primera foto nunca queda a medias. */
    private fun replace(target: File, winner: File): Boolean =
        try {
            Files.move(winner.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            true
        } catch (e: Exception) {
            try {
                Files.move(winner.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                true
            } catch (e2: Exception) {
                false
            }
        }
}
