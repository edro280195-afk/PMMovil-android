package com.example.pmmovil.ui.entrada

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.data.model.EntradaCatalogItem
import com.example.pmmovil.ui.components.HideKeyboardWhileScrolling
import com.example.pmmovil.ui.components.PickerFrequentChips
import com.example.pmmovil.ui.components.PickerOverlayHost
import com.example.pmmovil.ui.components.PickerPanelHeader
import com.example.pmmovil.ui.components.PickerRow
import com.example.pmmovil.ui.theme.PmmBgCard
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextSecondary
import kotlinx.coroutines.delay

/**
 * Lo que pide un campo del formulario para elegir algo. El panel se dibuja una sola vez en la raíz de la pantalla
 * (dentro de su ventana, no en un Dialog) y el campo solo avisa qué quiere elegir.
 */
internal sealed interface EntradaPickerRequest {
    /** Catálogo grande con búsqueda en el servidor: Cliente, Transporte, Caja de arribo. */
    data class Lookup(
        val type: EntradaLookupType,
        val title: String,
        val placeholder: String,
        val selectedCode: String
    ) : EntradaPickerRequest

    /** Catálogo chico y estático: Bodega, Manejo, Clase, Ubicación, Clase HAZMAT. */
    data class Catalog(
        val title: String,
        val items: List<EntradaCatalogItem>,
        val selectedValue: String,
        val onSelect: (EntradaCatalogItem) -> Unit
    ) : EntradaPickerRequest
}

internal class EntradaPickerHost(val open: (EntradaPickerRequest) -> Unit)

internal val LocalEntradaPickerHost = compositionLocalOf { EntradaPickerHost { } }

@Composable
internal fun EntradaPickerOverlay(
    request: EntradaPickerRequest,
    state: EntradaUiState,
    viewModel: EntradaViewModel,
    onDismiss: () -> Unit
) {
    when (request) {
        is EntradaPickerRequest.Lookup -> EntradaLookupPanel(
            request = request,
            state = state,
            onSearch = viewModel::searchLookup,
            onDismiss = onDismiss,
            onSelect = {
                viewModel.selectLookup(it)
                onDismiss()
            }
        )
        is EntradaPickerRequest.Catalog -> EntradaCatalogPanel(
            request = request,
            onDismiss = onDismiss,
            onSelect = {
                request.onSelect(it)
                onDismiss()
            }
        )
    }
}

/** Búsqueda en catálogo grande: campo arriba, frecuentes en una fila y los resultados debajo. */
@Composable
private fun EntradaLookupPanel(
    request: EntradaPickerRequest.Lookup,
    state: EntradaUiState,
    onSearch: (EntradaLookupType, String) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (com.example.pmmovil.data.model.EntradaLookupItem) -> Unit
) {
    val type = request.type
    var query by remember(type) { mutableStateOf("") }
    val results = if (state.lookupType == type) state.lookupItems else emptyList()
    val isSearching = state.isLookupLoading && state.lookupType == type
    val frequent = when (type) {
        EntradaLookupType.CUSTOMER -> state.frequentCustomers
        EntradaLookupType.TRANSPORT -> state.frequentTransports
        EntradaLookupType.BOX -> emptyList()
    }

    LaunchedEffect(type, query) {
        delay(280)
        onSearch(type, query)
    }

    // Una lista nueva por cada búsqueda: así cada búsqueda arranca arriba sin mover nada por código.
    val listState = remember(query) { LazyListState() }
    HideKeyboardWhileScrolling(listState)
    val keyboard = LocalSoftwareKeyboardController.current

    PickerOverlayHost(onDismiss = onDismiss) {
        PickerPanelHeader(
            title = request.title,
            trailing = if (query.isNotBlank() && !isSearching) "${results.size} resultados" else null,
            onDismiss = onDismiss
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(request.placeholder) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PmmTextMuted) },
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = PmmBlueDeep)
                } else if (query.isNotBlank()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Limpiar búsqueda", tint = PmmTextSecondary)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                onSearch(type, query)
                keyboard?.hide()
            }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp)
        )
        if (query.isBlank()) {
            PickerFrequentChips(
                labels = frequent.map { it.lookupItem.nombre.ifBlank { "Sin nombre" } to it.uso },
                onClick = { index -> onSelect(frequent[index].lookupItem) }
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Text(
                    if (query.isBlank()) "Todos" else "Resultados",
                    color = PmmTextSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            if (results.isEmpty() && !isSearching) {
                item { EntradaEmptyResults() }
            } else {
                items(results) { item ->
                    PickerRow(
                        title = item.nombre.ifBlank { "Sin nombre" },
                        subtitle = listOf(item.id, item.extra).filter(String::isNotBlank).joinToString(" · "),
                        query = query,
                        selected = request.selectedCode.isNotBlank() && item.id == request.selectedCode,
                        onClick = { onSelect(item) }
                    )
                }
            }
        }
    }
}

/** Catálogo corto: toda la lista a la vista; el campo de búsqueda solo aparece si hay más de 8 opciones. */
@Composable
private fun EntradaCatalogPanel(
    request: EntradaPickerRequest.Catalog,
    onDismiss: () -> Unit,
    onSelect: (EntradaCatalogItem) -> Unit
) {
    var query by remember(request.title) { mutableStateOf("") }
    val showSearch = request.items.size > 8
    val filtered = remember(request.items, query) {
        if (query.isBlank()) {
            request.items
        } else {
            request.items.filter { it.value.contains(query, true) || it.text.contains(query, true) }
        }
    }
    val listState = remember(query) { LazyListState() }
    HideKeyboardWhileScrolling(listState)

    PickerOverlayHost(onDismiss = onDismiss) {
        PickerPanelHeader(
            title = request.title,
            trailing = if (showSearch && query.isNotBlank()) "${filtered.size} de ${request.items.size}" else null,
            onDismiss = onDismiss
        )
        if (showSearch) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar en ${request.title}") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PmmTextMuted) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpiar búsqueda", tint = PmmTextSecondary)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (filtered.isEmpty()) {
                item { EntradaEmptyResults() }
            } else {
                items(filtered) { item ->
                    PickerRow(
                        title = item.text.ifBlank { item.value },
                        subtitle = item.value,
                        query = query,
                        selected = item.value == request.selectedValue,
                        onClick = { onSelect(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EntradaEmptyResults() {
    Surface(
        color = PmmBgCard,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, PmmNavyBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = PmmTextMuted)
            Text("Sin resultados. Prueba con otro código o nombre.", color = PmmTextSecondary)
        }
    }
}
