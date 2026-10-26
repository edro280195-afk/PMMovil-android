package com.example.pmmovil.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.R
import com.example.pmmovil.ui.theme.PmmAmberDark
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmRedDark
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.SignageType

/**
 * Las cinco estaciones por las que pasa una caja o una entrada, en el orden en que ocurren. Es el hilo de todo el
 * producto: el Tablero, la ficha y las marcas del AR usan la misma línea.
 */
enum class Station(@param:StringRes val nameRes: Int, @param:StringRes val shortRes: Int) {
    Caseta(R.string.station_caseta, R.string.station_caseta_short),
    Entrada(R.string.station_entrada, R.string.station_entrada_short),
    Revision(R.string.station_revision, R.string.station_revision_short),
    Relacion(R.string.station_relacion, R.string.station_relacion_short),
    Salida(R.string.station_salida, R.string.station_salida_short)
}

/** Cómo va una pieza en una estación. */
enum class StationState(@param:StringRes val descriptionRes: Int) {
    /** Ya pasó por aquí. Círculo azul con palomita. */
    Done(R.string.station_state_done),

    /** Está aquí ahora. Anillo azul con halo. */
    Now(R.string.station_state_now),

    /** Tiene un problema en esta estación. Cuadrado rojo con «!». */
    Warn(R.string.station_state_warn),

    /** No hay forma de saberlo, por ejemplo, la caja no está vinculada a una entrada. Anillo punteado ámbar. */
    Unknown(R.string.station_state_unknown),

    /** Todavía no llega. Anillo vacío. */
    Todo(R.string.station_state_todo),

    /** No le corresponde, por ejemplo, la revisión de una caja que llegó vacía. Gris con guion. */
    NotApplicable(R.string.station_state_na)
}

/**
 * Nodo de una estación sobre la línea. [number] es la posición (1 a 5); se dibuja dentro de los estados que no
 * llevan símbolo.
 */
@Composable
fun StationNode(
    number: Int,
    state: StationState,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp
) {
    val description = stringResource(state.descriptionRes)
    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(size)) { drawNode(state) }
        val label = when (state) {
            StationState.Now -> number.toString() to PmmBlueDeep
            StationState.Todo -> number.toString() to PmmTextMuted
            StationState.Warn -> "!" to Color.White
            StationState.Unknown -> "?" to PmmAmberDark
            StationState.NotApplicable -> "–" to PmmTextSecondary
            StationState.Done -> null
        }
        if (label != null) {
            Text(
                text = label.first,
                color = label.second,
                style = SignageType.StationNumber.copy(fontSize = (size.value * 0.46f).sp, lineHeight = (size.value * 0.46f).sp)
            )
        }
    }
}

private fun DrawScope.drawNode(state: StationState) {
    val w = size.width
    val center = Offset(w / 2, w / 2)
    when (state) {
        StationState.Done -> {
            drawCircle(PmmBlueDeep, radius = w / 2, center = center)
            val check = Path().apply {
                moveTo(w * 0.28f, w * 0.52f)
                lineTo(w * 0.43f, w * 0.67f)
                lineTo(w * 0.72f, w * 0.35f)
            }
            drawPath(check, Color.White, style = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        StationState.Now -> {
            drawCircle(PmmBlueDeep.copy(alpha = 0.16f), radius = w / 2, center = center)
            drawCircle(Color.White, radius = w * 0.38f, center = center)
            drawCircle(PmmBlueDeep, radius = w * 0.38f - w * 0.055f, center = center, style = Stroke(width = w * 0.11f))
        }

        StationState.Warn -> drawRoundRect(
            PmmRedDark,
            topLeft = Offset(w * 0.04f, w * 0.04f),
            size = Size(w * 0.92f, w * 0.92f),
            cornerRadius = CornerRadius(w * 0.27f)
        )

        StationState.Unknown -> {
            drawCircle(Color.White, radius = w / 2 - w * 0.04f, center = center)
            drawCircle(
                PmmAmberDark,
                radius = w / 2 - w * 0.04f,
                center = center,
                style = Stroke(
                    width = w * 0.08f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.14f, w * 0.1f))
                )
            )
        }

        StationState.Todo -> {
            drawCircle(Color.White, radius = w / 2 - w * 0.04f, center = center)
            drawCircle(PmmNavyBorder, radius = w / 2 - w * 0.04f, center = center, style = Stroke(width = w * 0.08f))
        }

        StationState.NotApplicable -> drawCircle(PmmNavyBorder, radius = w / 2, center = center)
    }
}

/**
 * La Línea completa en miniatura: cinco nodos unidos y, debajo de cada uno, la abreviatura de su estación. Es lo que
 * lleva cada marca del AR y la tarjeta de «Siguiente».
 */
@Composable
fun MiniStationLine(
    states: List<StationState>,
    modifier: Modifier = Modifier,
    nodeSize: Dp = 13.dp
) {
    require(states.size == Station.entries.size) { "La Línea tiene ${Station.entries.size} estaciones" }
    // stringResource no se puede llamar dentro de joinToString (su lambda no es composable): se resuelve antes.
    val stationNames = Station.entries.map { stringResource(it.nameRes) }
    val stateNames = states.map { stringResource(it.descriptionRes) }
    val summary = stationNames.zip(stateNames).joinToString(", ") { (name, state) -> "$name: $state" }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = summary },
        verticalAlignment = Alignment.Top
    ) {
        Station.entries.forEachIndexed { index, station ->
            if (index > 0) {
                // Tramo que une dos nodos: ocupa lo que sobra entre columnas, a la altura del centro del nodo.
                Box(
                    Modifier
                        .weight(1f)
                        .padding(top = nodeSize / 2 - 1.dp)
                        .height(2.dp)
                        .background(PmmNavyBorder)
                )
            }
            Column(
                modifier = Modifier
                    .width(34.dp)
                    .clearAndSetSemantics { },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StationNode(number = index + 1, state = states[index], size = nodeSize)
                Text(
                    text = stringResource(station.shortRes),
                    color = PmmTextSecondary,
                    style = SignageType.Caption.copy(fontSize = 8.5.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
