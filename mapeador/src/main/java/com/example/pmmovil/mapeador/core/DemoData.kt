package com.example.pmmovil.mapeador.core

import java.time.LocalDate
import kotlin.random.Random

/**
 * Entrada de DEMOSTRACIÓN. Tiene la misma forma que `OccupancyEntry` del API de ocupación
 * (cliente, referencia, bultos, estado, fecha de arribo, descripción, clase) para poder
 * cambiarla por la ocupación real sin rehacer las tarjetas.
 */
class DemoEntry(
    val reference: String,
    val client: String,
    val description: String,
    val packages: Int,
    /** "ocupada" o "parcial", igual que el API. */
    val state: String,
    val arrival: LocalDate,
    val cargoClass: String
)

object DemoData {
    private val clients = listOf(
        "FREUDENBERG-NOK", "ALLNEX", "SKF", "EXXONMOBIL", "WIAMEC", "VORTEX", "BERGSTROM", "INDUSTRIAL DE VALVULAS"
    )
    private val descriptions = listOf(
        "O-RINGS FKM 75", "RESINA ACRILICA", "BALEROS 6204-2RS", "ACEITE HIDRAULICO ISO 68",
        "VALVULA BOLA 2 IN", "SELLOS RADIALES", "MANGUERA HOSE 20", "COMPUESTO QUIMICO"
    )
    private val classes = listOf("TARIMA", "TAMBOR", "CAJA", "HUACAL", "TINA IBC")

    /** Entradas inventadas pero estables: el mismo código siempre da las mismas (para repetir pruebas). */
    fun entriesFor(code: String, today: LocalDate = LocalDate.now()): List<DemoEntry> {
        val rnd = Random(code.trim().uppercase().hashCode().toLong() * 7919L + 13L)
        val count = when (rnd.nextInt(10)) {
            0 -> 0
            in 1..4 -> 1
            in 5..7 -> 2
            8 -> 3
            else -> 4
        }
        return List(count) {
            val digits = buildString { repeat(10) { append(rnd.nextInt(10)) } }
            DemoEntry(
                reference = "01C26$digits",
                client = clients[rnd.nextInt(clients.size)],
                description = descriptions[rnd.nextInt(descriptions.size)],
                packages = 1 + rnd.nextInt(40),
                state = if (rnd.nextInt(4) == 0) "parcial" else "ocupada",
                arrival = today.minusDays(1L + rnd.nextInt(21)),
                cargoClass = classes[rnd.nextInt(classes.size)]
            )
        }
    }

    fun totalPackages(entries: List<DemoEntry>): Int = entries.sumOf { it.packages }

    fun summary(entries: List<DemoEntry>): String = when (entries.size) {
        0 -> "Libre · sin entradas"
        1 -> "1 entrada · ${totalPackages(entries)} bultos"
        else -> "${entries.size} entradas · ${totalPackages(entries)} bultos"
    }
}
