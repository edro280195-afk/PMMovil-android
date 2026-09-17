package com.example.pmmovil.data.model

/**
 * Contrato JSON compartido con la web (Konva) y el backend.
 * Ver docs/contrato-warehouse-layout.md en el repo PMMovil-migracion.
 * schemaVersion actual = 1. Los campos opcionales no rompen datos previos.
 */
data class WarehouseLayoutResponse(
    val success: Boolean = false,
    val warehouseId: String = "",
    val layout: WarehouseLayout? = null,
    val source: String = "",
    val version: Int = 0,
    val updatedBy: String? = null,
    val updatedAt: String? = null,
    val message: String? = null
)

data class WarehouseLayout(
    val schemaVersion: Int? = null,
    val version: Int = 1,
    val warehouseId: String = "MAQUILA",
    val name: String = "",
    val canvas: WarehouseCanvas = WarehouseCanvas(),
    val structures: List<WarehouseStructure> = emptyList(),
    val zones: List<WarehouseZone> = emptyList(),
    val savedAt: String? = null
)

data class WarehouseCanvas(
    val width: Float = 1200f,
    val height: Float = 640f,
    val snapSize: Float? = 10f
)

/** Muros, oficina, guias. Solo dibujo, no seleccionable. */
data class WarehouseStructure(
    val id: String = "",
    val label: String = "",
    val type: String = "",
    val shape: String = "rect",       // rect | line
    val x: Float? = null,
    val y: Float? = null,
    val width: Float? = null,
    val height: Float? = null,
    val points: List<Float>? = null,  // para shape=line: [x1,y1,x2,y2,...]
    val fill: String? = null,
    val stroke: String? = null,
    val strokeWidth: Float? = null
)

/** Ubicacion operativa. El id ES el codigo de ubicacion (lo que se guarda en Entrada). */
data class WarehouseZone(
    val id: String = "",
    val label: String = "",
    val description: String? = null,
    val type: String = "rack",
    val shape: String? = "rect",      // rect | circle | diamond
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
    val rotation: Float? = 0f,
    val fill: String? = null,
    val locked: Boolean? = false,
    val section: String? = null,
    val aisle: String? = null,
    val level: String? = null,
    val capacity: Int? = null,
    val status: String? = null,       // libre | ocupada | parcial | bloqueada
    // --- Apariencia 3D (opcional; la escribe el editor web) ---
    val shape3d: String? = null,      // auto | box | rack | pallets | cylinder | silo | cone | dome | floor
    val height3d: Float? = null,      // altura en metros de maqueta (0.4 - 4)
    val levels3d: Int? = null,        // niveles de estante cuando shape3d = rack (2 - 6)
    // --- Infraestructura fisica (contenedor). La carga se deriva de la clase ocupante. ---
    val structure: String? = null,    // rack | floor | patio | dock | office | pad
    val structureLevels: Int? = null  // alias operativo de levels3d para racks
)

data class WarehouseLayoutSaveRequest(val layout: WarehouseLayout)

data class WarehouseLayoutSaveResponse(
    val success: Boolean = false,
    val warehouseId: String = "",
    val version: Int = 0,
    val message: String? = null
)

data class WarehouseOccupancyResponse(
    val success: Boolean = false,
    val warehouseId: String = "",
    val generatedAt: String = "",
    val locations: Map<String, LocationOccupancy> = emptyMap(),
    val message: String? = null
)

data class LocationOccupancy(
    val status: String = "ocupada",
    val confidence: String = "alta",
    val bultos: Int = 0,
    val etiquetasPendientes: Int = 0,
    val etiquetasCargadas: Int = 0,
    val etiquetasSalida: Int = 0,
    val bultosSinEtiqueta: Int = 0,
    val directasSinEtiqueta: Int = 0,
    val entradasCount: Int = 0,
    val entradasOmitidas: Int = 0,
    val clasePrincipal: String = "",
    val clases: List<ClaseOccupancy> = emptyList(),
    val entradas: List<OccupancyEntry> = emptyList()
)

/** Bultos por clase de mercancia dentro de una ubicacion. */
data class ClaseOccupancy(
    val clase: String = "",
    val bultos: Int = 0
)

data class OccupancyEntry(
    val creferencia: String = "",
    val ref: String = "",
    val tipop: String = "",
    val tipoOperacion: String = "",
    val linea: Int = 0,
    val cliente: String = "",
    val clienteId: String = "",
    val bultos: Int = 0,
    val etiquetasPendientes: Int = 0,
    val etiquetasCargadas: Int = 0,
    val etiquetasSalida: Int = 0,
    val bultosSinEtiqueta: Int = 0,
    val directasSinEtiqueta: Int = 0,
    val estado: String = "",
    val confidence: String = "alta",
    val fechaArribo: String = "",
    val descripcion: String = "",
    val clase: String = ""
)

data class WarehouseMapData(
    val layout: WarehouseLayout,
    val occupancy: Map<String, LocationOccupancy> = emptyMap(),
    val claseModels: List<ClaseModelSpec> = DEFAULT_CLASE_MODELS,
    val occupancyError: String? = null
)
