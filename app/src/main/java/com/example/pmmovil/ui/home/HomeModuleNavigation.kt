package com.example.pmmovil.ui.home

object HomeModuleRouteKeys {
    const val WAREHOUSE_ENTRIES = "Entradas"
    const val WAREHOUSE_LOADS = "Cargar mercancía"
    const val WAREHOUSE_RELATION = "Relación"
    const val GATE = "Caseta"
    const val GATE_HUB = "Caseta Hub"
    const val GATE_RELATION_EXIT = "Salida de relación Caseta"
    const val GATE_PHYSICAL_EXIT = "Salida física de caja"
    const val WAREHOUSE_MAP = "Mapa Bodega"
    const val ENTRY_AR = "AR de entradas"
    const val GATE_ENTRY = "Entrada Caseta"
    const val GATE_EXIT = "Salida Caseta"
    const val SYSTEMS_ADMIN = "SistemasAdmin"
}

object HomeDestinations {
    const val WAREHOUSE_ENTRIES = "bodega_lista"
    const val WAREHOUSE_LOADS = "salida"
    const val WAREHOUSE_RELATION = "relacion"
    const val GATE = "caseta"
    const val GATE_HUB = "caseta_hub"
    // Conserva las rutas internas existentes para no romper navegación
    // pendiente; las etiquetas visibles ya distinguen cada operación.
    // La salida con relación y la manual viven ahora en una sola pantalla.
    const val GATE_RELATION_EXIT = "salida_caseta"
    const val GATE_PHYSICAL_EXIT = "salida_caseta"
    const val WAREHOUSE_MAP = "mapa_bodega"
    const val ENTRY_AR = "ar_entradas"
    const val GATE_ENTRY = "caseta_entrada"
    const val GATE_EXIT = "salida_caseta"
    // "Impresora Zebra" se retiró por el hub Etiquetas/Impresoras (ver PrintingHubScreen).
    const val SYSTEMS_ADMIN = "printing_hub"
}

/** Devuelve el destino real del NavHost para una tarjeta del inicio. */
fun resolveHomeModuleDestination(routeKey: String): String? = when (routeKey) {
    HomeModuleRouteKeys.WAREHOUSE_ENTRIES -> HomeDestinations.WAREHOUSE_ENTRIES
    HomeModuleRouteKeys.WAREHOUSE_LOADS -> HomeDestinations.WAREHOUSE_LOADS
    HomeModuleRouteKeys.WAREHOUSE_RELATION -> HomeDestinations.WAREHOUSE_RELATION
    HomeModuleRouteKeys.GATE -> HomeDestinations.GATE
    HomeModuleRouteKeys.GATE_HUB -> HomeDestinations.GATE_HUB
    HomeModuleRouteKeys.GATE_RELATION_EXIT -> HomeDestinations.GATE_RELATION_EXIT
    HomeModuleRouteKeys.GATE_PHYSICAL_EXIT -> HomeDestinations.GATE_PHYSICAL_EXIT
    HomeModuleRouteKeys.WAREHOUSE_MAP -> HomeDestinations.WAREHOUSE_MAP
    HomeModuleRouteKeys.ENTRY_AR -> HomeDestinations.ENTRY_AR
    HomeModuleRouteKeys.GATE_ENTRY -> HomeDestinations.GATE_ENTRY
    HomeModuleRouteKeys.GATE_EXIT -> HomeDestinations.GATE_EXIT
    HomeModuleRouteKeys.SYSTEMS_ADMIN -> HomeDestinations.SYSTEMS_ADMIN
    else -> null
}
