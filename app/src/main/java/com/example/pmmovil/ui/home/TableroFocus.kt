package com.example.pmmovil.ui.home

import com.example.pmmovil.ui.components.Station

/**
 * La estación que el Tablero resalta: la del puesto del usuario. Sigue la misma regla que el módulo principal del
 * inicio de siempre (Revisión primero, luego Caseta, y si no, la primera que tenga), de modo que quien ya conocía su
 * pantalla principal la reconoce en el mismo lugar de importancia.
 *
 * @param allowed estaciones cuyo módulo puede abrir el usuario, en el orden de La Línea.
 */
fun tableroFocus(allowed: List<Station>): Station? = when {
    Station.Revision in allowed -> Station.Revision
    Station.Caseta in allowed -> Station.Caseta
    else -> allowed.firstOrNull()
}
