package com.example.pmmovil.mapeador.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.mapeador.core.MapMeta
import com.example.pmmovil.mapeador.core.MapStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(store: MapStore, onCapture: () -> Unit, onLocate: (String) -> Unit) {
    var maps by remember { mutableStateOf<List<MapMeta>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    var info by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<MapMeta?>(null) }
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.forLanguageTag("es-MX")) }

    LaunchedEffect(refresh) { maps = withContext(Dispatchers.IO) { store.listMaps() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Text("PMM Mapeador", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Prototipo de oficina · v0.1", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onCapture, modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text("Mapear un lugar nuevo", fontSize = 18.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Camina despacio mirando muros, muebles y techo; marca las ubicaciones y guarda. " +
                "Después abre «Ubicar» con ese mapa para ver las tarjetas flotando.",
            color = Muted, fontSize = 13.sp
        )
        Spacer(Modifier.height(20.dp))
        Text("Mapas guardados", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Spacer(Modifier.height(8.dp))

        if (maps.isEmpty()) {
            Text("Todavía no hay mapas. Empieza con «Mapear un lugar nuevo».", color = Muted)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(maps, key = { it.id }) { m ->
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(14.dp)) {
                        Text(m.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                        Text(dateFormat.format(Date(m.createdAtMs)), color = Muted, fontSize = 12.sp)
                        Text(
                            String.format(
                                Locale.US, "%.0f m² de piso · %d anclas · %d fotogramas · %d ubicaciones",
                                m.areaM2, m.landmarks, m.keyframes, m.locations
                            ),
                            color = Color.White, fontSize = 13.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onLocate(m.id) }) { Text("Ubicar") }
                            OutlinedButton(onClick = {
                                scope.launch {
                                    info = withContext(Dispatchers.IO) {
                                        runCatching {
                                            val out = store.exportColmap(m.id)
                                            "Exportado a COLMAP:\n${out.absolutePath}\nFotos y poses en:\n${store.mapDir(m.id).absolutePath}"
                                        }.getOrElse { "No se pudo exportar: ${it.message}" }
                                    }
                                }
                            }) { Text("Exportar") }
                            TextButton(onClick = { toDelete = m }) { Text("Borrar", color = Bad) }
                        }
                    }
                }
            }
        }
        info?.let {
            Spacer(Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(12.dp)) {
                    Text(it, color = Color.White, fontSize = 12.sp)
                    TextButton(onClick = { info = null }) { Text("Cerrar") }
                }
            }
        }
    }

    toDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Borrar «${m.name}»?") },
            text = { Text("Se borran el mapa, sus fotogramas y sus ubicaciones. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { store.delete(m.id) }
                        toDelete = null
                        refresh++
                    }
                }) { Text("Borrar", color = Bad) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } }
        )
    }
}
