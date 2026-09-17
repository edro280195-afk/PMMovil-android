package com.example.pmmovil.ui.printer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pmmovil.data.model.ZebraPrinterDto
import com.example.pmmovil.data.printing.ZebraLabelType
import com.example.pmmovil.ui.theme.PmmBgCard
import com.example.pmmovil.ui.theme.PmmBgPrimary
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmGreen
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmNavyDark
import com.example.pmmovil.ui.theme.PmmRed
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.PmmYellow

/**
 * Catálogo simple de impresoras Zebra: solo lista, alta y edición. Los ajustes físicos en vivo
 * y la asignación de "predeterminada por tipo de etiqueta" viven en el detalle de cada impresora
 * ([PrinterDetailScreen]), no aquí.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterCatalogScreen(
    onNavigateBack: () -> Unit,
    onNewPrinter: () -> Unit,
    onSelectPrinter: (Int) -> Unit,
    viewModel: PrinterCatalogViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = PmmBgPrimary,
        topBar = {
            Surface(color = PmmNavyDark, shadowElevation = 4.dp) {
                TopAppBar(
                    title = {
                        Column {
                            Text("Impresoras", fontWeight = FontWeight.Black)
                            Text(
                                "Catálogo de impresoras Zebra por red",
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 11.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::loadPrinters) {
                            Icon(Icons.Default.Refresh, "Recargar", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = PmmNavyDark,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onNewPrinter,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep),
                contentPadding = PaddingValues(vertical = 13.dp)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Nueva impresora", fontWeight = FontWeight.Black)
            }

            state.message?.let { message ->
                Surface(
                    color = (if (state.lastSuccess == false) PmmRed else PmmBlueDeep).copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, (if (state.lastSuccess == false) PmmRed else PmmBlueDeep).copy(alpha = 0.24f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(message, color = PmmTextPrimary, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
                }
            }

            when {
                state.isLoading -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = PmmBlueDeep)
                }

                state.printers.isEmpty() -> Surface(
                    color = PmmBgCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PmmNavyBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Sin impresoras registradas", color = PmmTextPrimary, fontWeight = FontWeight.Bold)
                        Text("Captura los datos de la primera Zebra con \"Nueva impresora\".", color = PmmTextSecondary, fontSize = 12.sp)
                    }
                }

                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.printers, key = ZebraPrinterDto::id) { printer ->
                        PrinterRow(
                            printer = printer,
                            defaultLabelTypes = viewModel.defaultLabelTypes(printer),
                            onClick = { onSelectPrinter(printer.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrinterRow(
    printer: ZebraPrinterDto,
    defaultLabelTypes: List<ZebraLabelType>,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = PmmBgSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, PmmNavyBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Print, contentDescription = null, tint = PmmBlueDeep, modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = printer.displayName,
                        color = PmmTextPrimary,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = printer.endpoint,
                        color = PmmTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge(if (printer.isActive) "Activa" else "Inactiva", active = printer.isActive)
                printer.location?.takeIf(String::isNotBlank)?.let { Badge(it, active = true, neutral = true) }
            }
            if (defaultLabelTypes.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = PmmYellow, modifier = Modifier.size(14.dp))
                    Text(
                        "Predeterminada: " + defaultLabelTypes.joinToString(", ") { it.title },
                        color = PmmTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, active: Boolean, neutral: Boolean = false) {
    val color = if (neutral) PmmTextMuted else if (active) PmmGreen else PmmRed
    Surface(
        color = color.copy(alpha = 0.10f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f))
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
