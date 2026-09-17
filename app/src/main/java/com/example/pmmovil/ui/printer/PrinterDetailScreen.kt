package com.example.pmmovil.ui.printer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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
 * Alta/edición de una sola impresora. Reemplaza a la vieja "Impresora Zebra" (que mezclaba
 * catálogo + formulario + ajustes físicos + botones del diseñador en una sola pantalla):
 * aquí solo vive lo de ESTA impresora. Las preferencias físicas avanzadas quedan colapsadas
 * y requieren permiso especial; para editar cómo se ve una etiqueta, usa "Etiquetas" desde el
 * hub, no esta pantalla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterDetailScreen(
    onNavigateBack: () -> Unit,
    printerId: Int? = null,
    viewModel: PrinterDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(printerId) {
        if (printerId != null && printerId > 0) viewModel.loadPrinter(printerId)
    }

    Scaffold(
        containerColor = PmmBgPrimary,
        topBar = {
            Surface(color = PmmNavyDark, shadowElevation = 4.dp) {
                TopAppBar(
                    title = {
                        Column {
                            Text(if (state.isNew) "Nueva impresora" else "Editar impresora", fontWeight = FontWeight.Black)
                            Text(
                                state.printerName.ifBlank { "Nombre, IP y puerto de la Zebra" },
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
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
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PmmBlueDeep)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BasicInfoCard(state, viewModel)
            DefaultForCard(state, viewModel)

            Button(
                onClick = viewModel::saveConfig,
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PmmYellow, contentColor = PmmNavyDark),
                contentPadding = PaddingValues(vertical = 13.dp)
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Guardar impresora", fontWeight = FontWeight.Black)
            }

            ConnectionTestsCard(state, viewModel)
            AdvancedSettingsCard(state, viewModel)
            StatusRow(state)
        }
    }
}

@Composable
private fun BasicInfoCard(state: PrinterDetailState, viewModel: PrinterDetailViewModel) {
    DetailCard(title = "Datos de la impresora", icon = Icons.Default.Router) {
        OutlinedTextField(
            value = state.printerName,
            onValueChange = viewModel::updatePrinterName,
            label = { Text("Nombre visible") },
            placeholder = { Text("Zebra Caseta 1") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = state.host,
                onValueChange = viewModel::updateHost,
                label = { Text("IP de impresora") },
                placeholder = { Text("192.168.20.37") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = state.port,
                onValueChange = viewModel::updatePort,
                label = { Text("Puerto") },
                placeholder = { Text("9100") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.width(118.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = state.location,
                onValueChange = viewModel::updateLocation,
                label = { Text("Ubicacion") },
                placeholder = { Text("Caseta / Bodega") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = state.terminalName,
                onValueChange = viewModel::updateTerminalName,
                label = { Text("Terminal") },
                placeholder = { Text("CASETA-01") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = state.connectTimeoutMillis,
                onValueChange = viewModel::updateConnectTimeout,
                label = { Text("Timeout conexion ms") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = state.writeTimeoutMillis,
                onValueChange = viewModel::updateWriteTimeout,
                label = { Text("Timeout escritura ms") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        ToggleLine(
            title = "Activa",
            subtitle = "Disponible para imprimir",
            checked = state.isActive,
            onCheckedChange = viewModel::updateIsActive
        )
    }
}

@Composable
private fun DefaultForCard(state: PrinterDetailState, viewModel: PrinterDetailViewModel) {
    DetailCard(title = "Predeterminada para", icon = Icons.Default.SettingsEthernet) {
        Text(
            "Se guarda solo en este dispositivo. Cada tipo de etiqueta puede tener una impresora distinta.",
            color = PmmTextSecondary,
            fontSize = 11.sp
        )
        ToggleLine(
            title = "Entrada Bodega",
            subtitle = "Usar esta impresora al imprimir desde Entrada Bodega",
            checked = state.defaultForBodega,
            onCheckedChange = viewModel::updateDefaultForBodega
        )
        ToggleLine(
            title = "Caseta",
            subtitle = "Usar esta impresora al imprimir desde Entrada Caseta",
            checked = state.defaultForCaseta,
            onCheckedChange = viewModel::updateDefaultForCaseta
        )
    }
}

@Composable
private fun ConnectionTestsCard(state: PrinterDetailState, viewModel: PrinterDetailViewModel) {
    DetailCard(title = "Conexión y pruebas", icon = Icons.Default.SettingsEthernet) {
        OutlinedButton(
            onClick = viewModel::testConnection,
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 13.dp)
        ) {
            Icon(Icons.Default.SettingsEthernet, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text("Probar conexion", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("Probar impresión con:", color = PmmTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { viewModel.testPrint(ZebraLabelType.ENTRADA_BODEGA) },
                enabled = !state.isBusy,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(Icons.Default.Print, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Bodega", maxLines = 1, fontSize = 12.sp)
            }
            Button(
                onClick = { viewModel.testPrint(ZebraLabelType.CASETA) },
                enabled = !state.isBusy,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(Icons.Default.Print, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Caseta", maxLines = 1, fontSize = 12.sp)
            }
        }
        if (state.isBusy) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = PmmBlueDeep)
                Text("Comunicando con Zebra...", color = PmmTextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AdvancedSettingsCard(state: PrinterDetailState, viewModel: PrinterDetailViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PmmBgSurface),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PmmNavyBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Settings, null, tint = PmmBlueDeep, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Preferencias avanzadas de impresión", color = PmmTextPrimary, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    Text("Oscuridad, velocidad, offsets — hardware físico", color = PmmTextMuted, fontSize = 11.sp)
                }
                IconButton(onClick = viewModel::toggleAdvancedExpanded) {
                    Icon(
                        if (state.advancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (state.advancedExpanded) "Ocultar" else "Mostrar",
                        tint = PmmBlueDeep
                    )
                }
            }

            if (state.advancedExpanded) {
                HorizontalDivider(color = PmmNavyBorder)
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when {
                        state.isLoadingPermission -> Text(
                            "Verificando permiso para modificar preferencias...",
                            color = PmmTextMuted,
                            fontSize = 11.sp
                        )
                        !state.canModifyPrinterSettings -> Text(
                            "Solo lectura: se requiere Modificar en Sistemas (módulo 160) para aplicar cambios.",
                            color = PmmRed,
                            fontSize = 11.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = state.printDarkness,
                            onValueChange = viewModel::updatePrintDarkness,
                            label = { Text("Oscuridad") },
                            placeholder = { Text("0.0 - 30.0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            enabled = !state.isBusy && state.canModifyPrinterSettings,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = state.printSpeedIps,
                            onValueChange = viewModel::updatePrintSpeed,
                            label = { Text("Velocidad ips") },
                            placeholder = { Text("2 - 6") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            enabled = !state.isBusy && state.canModifyPrinterSettings,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = state.printMethod,
                            onValueChange = viewModel::updatePrintMethod,
                            label = { Text("Metodo") },
                            placeholder = { Text("thermal_transfer") },
                            singleLine = true,
                            enabled = !state.isBusy && state.canModifyPrinterSettings,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = state.mediaType,
                            onValueChange = viewModel::updateMediaType,
                            label = { Text("Material") },
                            placeholder = { Text("gap_notch") },
                            singleLine = true,
                            enabled = !state.isBusy && state.canModifyPrinterSettings,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        "Metodo: thermal_transfer o direct_thermal. Material: continuous, gap_notch o mark.",
                        color = PmmTextMuted,
                        fontSize = 11.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = state.labelTop,
                            onValueChange = viewModel::updateLabelTop,
                            label = { Text("Ajuste vertical") },
                            placeholder = { Text("-120 a 120 puntos") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            enabled = !state.isBusy && state.canModifyPrinterSettings,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = state.leftPosition,
                            onValueChange = viewModel::updateLeftPosition,
                            label = { Text("Ajuste horizontal") },
                            placeholder = { Text("-9999 a 9999") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            enabled = !state.isBusy && state.canModifyPrinterSettings,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = viewModel::refreshPrintSettings,
                            enabled = !state.isBusy && !state.isNew,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 13.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Leer de impresora", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Button(
                            onClick = viewModel::applyPrintSettings,
                            enabled = !state.isBusy && state.canModifyPrinterSettings && state.liveSettings != null && !state.isNew,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep),
                            contentPadding = PaddingValues(vertical = 13.dp)
                        ) {
                            Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Aplicar y guardar", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (state.isNew) {
                        Text(
                            "Guarda primero la impresora para poder leer/aplicar sus preferencias físicas.",
                            color = PmmTextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(state: PrinterDetailState) {
    val message = state.message ?: return
    val color = when (state.lastSuccess) {
        true -> PmmGreen
        false -> PmmRed
        null -> PmmBlueDeep
    }
    Surface(
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.24f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (state.lastSuccess == false) Icons.Default.Error else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Text(message, color = PmmTextPrimary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ToggleLine(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = PmmBgCard,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, PmmNavyBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = PmmTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(subtitle, color = PmmTextMuted, fontSize = 11.sp)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun DetailCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PmmBgSurface),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PmmNavyBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = PmmBlueDeep, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, color = PmmTextPrimary, fontWeight = FontWeight.Black, fontSize = 14.sp)
            }
            HorizontalDivider(color = PmmNavyBorder)
            content()
        }
    }
}
