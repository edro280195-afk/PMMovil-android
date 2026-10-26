package com.example.pmmovil.data.model

/** Identificadores compartidos con MobileModuleIds del API. */
object MobileModuleIds {
    /**
     * Operación general de caseta: tablero, entradas, pre-registro y edición.
     *
     * Reutiliza a proposito el modulo historico 12 "Caseta" en vez de crear
     * uno nuevo en el rango movil: ya existe en el catalogo, esta activo y
     * significa exactamente lo mismo. Asi la caseta queda separada de las
     * operaciones de bodega, que antes compartian [WAREHOUSE_OPERATIONS] y
     * hacian imposible dar acceso a una sin dar acceso a la otra.
     */
    const val GATE = 12
    const val GATE_RELATION_EXIT = 165
    const val GATE_PHYSICAL_EXIT = 166

    const val WAREHOUSE_EXIT = 151
    const val WAREHOUSE_RELATION = 167
    const val PROVIDERS = 152
    const val WAREHOUSE_OPERATIONS = 153
    const val PRE_INSPECTION = 154
    const val CARTA_PORTE = 155
    const val IMEX = 156
    const val REVIEW = 159
    const val SYSTEMS_MONITOR = 160
    const val SYSTEMS_ANALYTICS = 161
    const val REPORTS = 162
}
