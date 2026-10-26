package com.example.pmmovil.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.ui.theme.PmmBgPrimary
import com.example.pmmovil.ui.theme.PmmNavyDark
import com.example.pmmovil.ui.theme.PmmYellow
import com.example.pmmovil.ui.theme.SignageType

/**
 * La etiqueta viva: la ficha de una caja, un bulto, una carpeta o una relación dibujada como la etiqueta impresa.
 * Arriba la banda con la marca y el tipo; en medio el cuerpo (código, datos); una perforación con sus muescas; y
 * abajo el talón, donde va el sello de estado y qué sigue.
 *
 * @param kindLabel tipo de pieza, ya traducido («CAJA», «BULTO»…).
 * @param notchColor color del fondo sobre el que descansa la tarjeta, para que las muescas de la perforación se vean
 *   como un corte. Por omisión es el fondo de pantalla de la app.
 */
@Composable
fun LabelCard(
    kindLabel: String,
    modifier: Modifier = Modifier,
    notchColor: Color = PmmBgPrimary,
    body: @Composable ColumnScope.() -> Unit,
    stub: @Composable ColumnScope.() -> Unit
) {
    // Elevación de «hoja» del sistema (10-16 dp): la ficha flota sobre el contenido normal.
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, Color(0x0F002680))
    ) {
        Column {
            LabelTopBand(kindLabel)
            body()
            Perforation(notchColor)
            stub()
        }
    }
}

@Composable
private fun LabelTopBand(kindLabel: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PmmNavyDark)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Color.White)) { append("PMM") }
                withStyle(SpanStyle(color = PmmYellow)) { append("ovil") }
                withStyle(SpanStyle(color = Color.White.copy(alpha = 0.8f))) { append(" · IMEX") }
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Box(
            Modifier
                .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(5.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text(text = kindLabel, color = Color.White, style = SignageType.Caption)
        }
    }
}

/** Línea punteada con una muesca a cada lado, como el corte de una etiqueta que se despega. */
@Composable
private fun Perforation(notchColor: Color) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(18.dp)
    ) {
        val y = size.height / 2
        val margin = 14.dp.toPx()
        drawLine(
            color = Color(0xFFB6C2D4),
            start = Offset(margin, y),
            end = Offset(size.width - margin, y),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
        )
        val radius = 9.dp.toPx()
        drawCircle(notchColor, radius = radius, center = Offset(0f, y))
        drawCircle(notchColor, radius = radius, center = Offset(size.width, y))
    }
}
