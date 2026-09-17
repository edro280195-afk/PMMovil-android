package com.example.pmmovil.data.model

/**
 * Spec compartido Clase -> Modelo de carga.
 *
 * Android siempre usa `primitive`; `modelUrl` queda para la web.
 */
data class ClaseModelSpec(
    val claseId: String = "",
    val primitive: String = "box",
    val color: String = "#9ca3af",
    val unitFootprint: Float = 0.6f,
    val unitHeight: Float = 0.5f,
    val stackable: Boolean = true,
    val icon: String = "cube",
    val modelUrl: String? = null
)

data class ClaseModelCatalog(
    val version: Int = 1,
    val updatedBy: String? = null,
    val updatedAt: String? = null,
    val items: List<ClaseModelSpec> = emptyList()
)

data class ClaseModelosResponse(
    val success: Boolean = false,
    val catalogo: ClaseModelCatalog? = null,
    val message: String? = null
)

val DEFAULT_CLASE_MODELS = listOf(
    ClaseModelSpec("CAJA(S)", "box", "#b08968", 0.60f, 0.50f, true, "box"),
    ClaseModelSpec("HUACAL(ES)", "crate", "#a97142", 0.70f, 0.60f, true, "crate"),
    ClaseModelSpec("CONTENEDOR(ES)", "container", "#3b6ea5", 2.40f, 2.40f, false, "container"),
    ClaseModelSpec("TAMBOR(ES)", "drum", "#2b6cb0", 0.58f, 0.88f, true, "drum"),
    ClaseModelSpec("TINETA(S)", "tub", "#2c9c8f", 0.60f, 0.55f, true, "tub"),
    ClaseModelSpec("SACO(S)", "sack", "#d6a960", 0.70f, 0.55f, true, "sack"),
    ClaseModelSpec("BULTO(S)", "soft", "#9aa2ad", 0.60f, 0.50f, true, "package"),
    ClaseModelSpec("ATADO(S)", "bundle", "#7a8450", 1.20f, 0.40f, true, "bundle"),
    ClaseModelSpec("ROLLO(S)", "rollH", "#6b5bd0", 0.80f, 0.60f, true, "roll"),
    ClaseModelSpec("CARRETE(S)", "reel", "#5b6b7a", 0.70f, 0.70f, true, "reel"),
    ClaseModelSpec("TUBO(S)", "pipe", "#7b8a99", 1.40f, 0.40f, true, "pipe"),
    ClaseModelSpec("PIEZA(S)", "piece", "#c2703d", 0.60f, 0.50f, false, "gear"),
    ClaseModelSpec("LOTE(S)", "mixed", "#8a5cc0", 0.80f, 0.70f, true, "layers"),
    ClaseModelSpec("SOBRE(S)", "flat", "#d9a441", 0.50f, 0.12f, true, "envelope")
)

val FALLBACK_CLASE_MODEL = ClaseModelSpec()
