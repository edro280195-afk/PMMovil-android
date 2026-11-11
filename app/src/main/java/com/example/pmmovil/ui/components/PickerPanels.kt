package com.example.pmmovil.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmNavyVariant
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary

// ───────────────────────────────────────────────────────────────────────────
// Pantallas de selección que respetan el teclado
//
// Van DENTRO de la ventana de la pantalla (no en un Dialog) y suben desde arriba: con el teclado abierto la lista se
// encoge en vez de quedar tapada. En un Dialog la ventana es otra y el teclado no le avisa a la lista, que era justo
// lo que pasaba con la hoja pegada abajo. Se colocan dentro del contenido de un Scaffold (ya debajo de la barra
// superior), con los insets del Scaffold consumidos, para que imePadding solo sume lo que el Scaffold no reservó.
// ───────────────────────────────────────────────────────────────────────────

/** Fondo oscuro que cierra al tocarlo y un panel pegado ARRIBA que se encoge cuando aparece el teclado. */
@Composable
fun PickerOverlayHost(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 760.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler(onBack = onDismiss)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // Los toques dentro del panel no lo cierran
                ),
            color = PmmBgSurface,
            shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
            shadowElevation = 16.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
fun PickerPanelHeader(title: String, trailing: String?, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onDismiss) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = PmmTextSecondary)
        }
        Text(
            text = title,
            color = PmmTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(trailing, color = PmmTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Los más usados, en una sola fila de botones para elegirlos sin escribir ni desplazarse. */
@Composable
fun PickerFrequentChips(
    labels: List<Pair<String, Int>>,
    onClick: (index: Int) -> Unit
) {
    if (labels.isEmpty()) return
    Text(
        "Frecuentes",
        color = PmmTextSecondary,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(labels.size) { index ->
            val (label, usage) = labels[index]
            Surface(
                color = Color(0xFFEFF6FF),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clickable { onClick(index) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        label.take(26),
                        color = Color(0xFF1E3A8A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text("$usage", color = PmmTextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}

/** Un resultado: iniciales, nombre con lo escrito resaltado y su código debajo. */
@Composable
fun PickerRow(
    title: String,
    subtitle: String,
    query: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val initials = title.split(" ")
        .filter { it.isNotBlank() }
        .map { it.first() }
        .take(2)
        .joinToString("")
        .uppercase()
        .ifBlank { "•" }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClick = onClick),
        color = if (selected) Color(0xFFEEF3FF) else Color.White,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (selected) PmmBlueDeep else PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(PmmNavyVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(initials, color = PmmBlueDeep, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = highlightedName(title, query),
                    color = PmmTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = PmmTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = "Elegido", tint = PmmBlueDeep, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Al desplazar la lista el teclado se esconde, para ver todo lo que hay sin tener que cerrarlo a mano. */
@Composable
fun HideKeyboardWhileScrolling(listState: LazyListState) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) {
                keyboard?.hide()
                focus.clearFocus()
            }
        }
    }
}

/** El nombre con lo que se escribió resaltado, para ver de un vistazo por qué salió ese resultado. */
fun highlightedName(name: String, query: String): AnnotatedString {
    val q = query.trim()
    if (q.isEmpty()) return AnnotatedString(name)
    val start = name.indexOf(q, ignoreCase = true)
    if (start < 0) return AnnotatedString(name)
    return buildAnnotatedString {
        append(name.substring(0, start))
        withStyle(SpanStyle(background = Color(0xFFFDE68A))) {
            append(name.substring(start, start + q.length))
        }
        append(name.substring(start + q.length))
    }
}
