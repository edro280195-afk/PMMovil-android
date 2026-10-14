package com.example.pmmovil.ui.revision

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.data.ocr.LabelFieldExtractor

/**
 * "Encontre esto en la etiqueta": lo que la camara leyo, para que el revisador
 * confirme antes de que se guarde nada.
 *
 * La regla del modulo: **la camara propone, el revisador dispone**. Nada se
 * escribe sin que alguien lo vea. Por eso cada renglon trae de donde salio el
 * dato (el ancla, o el codigo GS1) y los que no se pueden escribir se muestran
 * igual, marcados como referencia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelCaptureSheet(
    estado: LabelCaptureViewModel.Estado,
    onAlternar: (LabelFieldExtractor.Campo) -> Unit,
    onUsar: () -> Unit,
    onRepetir: () -> Unit,
    onCerrar: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
        ) {
            when {
                estado.analizando -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Spacer(Modifier.size(14.dp))
                        Text("Leyendo la etiqueta...", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Se usa la foto completa, no el video: el lote impreso chico no se " +
                            "alcanza a la resolucion del preview.",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                !estado.huboLectura -> {
                    Text("No se pudo sacar nada", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        estado.error ?: "La etiqueta no trae ninguna de las anclas conocidas.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onRepetir) { Text("Tomar otra") }
                        OutlinedButton(onClick = onCerrar) { Text("Capturar a mano") }
                    }
                }

                else -> {
                    Text("Encontre esto en la etiqueta", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Revisa y confirma. Lo que desmarques no se guarda.",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))

                    estado.escribibles.forEach { propuesto ->
                        FilaCampo(propuesto = propuesto, onAlternar = { onAlternar(propuesto.leido.campo) })
                    }

                    val referencia = estado.campos.filterNot { it.escribible }
                    if (referencia.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "SOLO DE REFERENCIA",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Estos no se escriben solos: o no son editables en revision, o " +
                                "necesitan una decision tuya.",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        referencia.forEach { FilaCampo(propuesto = it, onAlternar = null) }
                    }

                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onUsar,
                            enabled = estado.aceptados.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (estado.aceptados.isEmpty()) {
                                    "Nada marcado"
                                } else {
                                    "Usar ${estado.aceptados.size} dato" +
                                        if (estado.aceptados.size == 1) "" else "s"
                                }
                            )
                        }
                        OutlinedButton(onClick = onRepetir) { Text("Repetir") }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaCampo(
    propuesto: LabelCaptureViewModel.CampoPropuesto,
    onAlternar: (() -> Unit)?
) {
    val activo = propuesto.escribible && propuesto.aceptado
    val fondo = when {
        activo -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        propuesto.escribible -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(fondo)
            .then(if (onAlternar != null) Modifier.clickable(onClick = onAlternar) else Modifier)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (propuesto.escribible) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        if (activo) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (activo) {
                    Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.size(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                nombreVisible(propuesto.leido.campo),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                propuesto.leido.valor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            propuesto.detalle?.let { detalle ->
                Text(
                    detalle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            origenVisible(propuesto.leido),
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

}

private fun nombreVisible(campo: LabelFieldExtractor.Campo): String = when (campo) {
    LabelFieldExtractor.Campo.LOTE -> "NUMERO DE LOTE"
    LabelFieldExtractor.Campo.NUMERO_SERIE -> "NUMERO DE SERIE"
    LabelFieldExtractor.Campo.NUMERO_PARTE -> "NUMERO DE PARTE"
    LabelFieldExtractor.Campo.CANTIDAD -> "CANTIDAD EN LA ETIQUETA"
    LabelFieldExtractor.Campo.PIEZAS_POR_EMPAQUE -> "PIEZAS POR EMPAQUE"
    LabelFieldExtractor.Campo.PAIS_ORIGEN -> "PAIS DE ORIGEN"
    LabelFieldExtractor.Campo.CADUCIDAD -> "CADUCIDAD"
    LabelFieldExtractor.Campo.UN_HAZMAT -> "NUMERO UN"
    LabelFieldExtractor.Campo.CLASE_HAZMAT -> "CLASE DE PELIGRO"
    LabelFieldExtractor.Campo.GRUPO_EMBALAJE -> "GRUPO DE EMBALAJE"
}

private fun origenVisible(leido: LabelFieldExtractor.CampoLeido): String =
    when (leido.origen) {
        LabelFieldExtractor.Origen.GS1 -> "codigo ${leido.ancla}"
        LabelFieldExtractor.Origen.ROMBO_HAZMAT -> "rombo"
        LabelFieldExtractor.Origen.ANCLA_LINEA_SIGUIENTE -> "${leido.ancla} (abajo)"
        LabelFieldExtractor.Origen.ANCLA_MISMA_LINEA -> leido.ancla
    }
