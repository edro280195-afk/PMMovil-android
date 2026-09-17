package com.example.pmmovil.ui.printer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.ui.home.components.bounceClick
import com.example.pmmovil.ui.theme.PmmBgPrimary
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmBlueMid
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmNavyDark
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.PmmYellow

/**
 * Punto de entrada único para todo lo de impresión: elegir qué etiqueta editar, o administrar
 * el catálogo de impresoras Zebra. Reemplaza el acceso directo que antes iba a "Impresora Zebra"
 * (una sola pantalla que mezclaba ambas cosas).
 *
 * En pantallas anchas (tablet) las dos opciones se muestran como tiles grandes con el mismo
 * degradado/estilo "hero" que ya usa [com.example.pmmovil.ui.home.components.CasetaFeaturedCard]
 * en Home, en vez de tarjetas blancas chicas flotando en un canvas gris vacío. En angostas
 * (teléfono) quedan como fila apilada a ancho completo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintingHubScreen(
    onNavigateBack: () -> Unit,
    onOpenLabelPicker: () -> Unit,
    onOpenPrinterCatalog: () -> Unit
) {
    Scaffold(
        containerColor = PmmBgPrimary,
        topBar = {
            Surface(color = PmmNavyDark, shadowElevation = 4.dp) {
                TopAppBar(
                    title = {
                        Column {
                            Text("Etiquetas e impresoras", fontWeight = FontWeight.Black)
                            Text(
                                "Elige qué quieres hacer",
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
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = PmmNavyDark,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val isWide = maxWidth >= 600.dp
            if (isWide) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 1040.dp),
                        verticalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        Column {
                            Text(
                                "¿Qué necesitas hacer?",
                                color = PmmTextPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 26.sp
                            )
                            Text(
                                "Elige una opción para continuar",
                                color = PmmTextMuted,
                                fontSize = 14.sp
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            HubHeroTile(
                                eyebrow = "DISEÑO",
                                title = "Etiquetas",
                                subtitle = "Diseña cómo se ve la etiqueta de Entrada Bodega o Caseta: textos, QR, logo, tamaños.",
                                icon = Icons.Default.Style,
                                onClick = onOpenLabelPicker,
                                modifier = Modifier.weight(1f)
                            )
                            HubHeroTile(
                                eyebrow = "HARDWARE",
                                title = "Impresoras",
                                subtitle = "Catálogo de impresoras Zebra: nombre, IP, puerto y cuál usar por default.",
                                icon = Icons.Default.Print,
                                onClick = onOpenPrinterCatalog,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    HubOptionCard(
                        title = "Etiquetas",
                        subtitle = "Diseña cómo se ve la etiqueta de Entrada Bodega o Caseta: textos, QR, logo, tamaños.",
                        icon = Icons.Default.Style,
                        accentColor = PmmBlueDeep,
                        onClick = onOpenLabelPicker
                    )
                    HubOptionCard(
                        title = "Impresoras",
                        subtitle = "Catálogo de impresoras Zebra: nombre, IP, puerto y cuál usar por default.",
                        icon = Icons.Default.Print,
                        accentColor = PmmNavyDark,
                        onClick = onOpenPrinterCatalog
                    )
                }
            }
        }
    }
}

/**
 * Tile grande con el mismo degradado PMM + brillo decorativo que usa la tarjeta destacada de
 * Caseta en Home, para pantallas anchas. Icono arriba, texto y botón "Abrir" abajo (SpaceBetween
 * en vez de centrar todo, así el tile se ve lleno aunque sea alto).
 */
@Composable
private fun HubHeroTile(
    eyebrow: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pmmGradient = Brush.linearGradient(colors = listOf(PmmNavyDark, PmmBlueDeep, PmmBlueMid))

    Surface(
        modifier = modifier
            .height(420.dp)
            .bounceClick(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pmmGradient)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(PmmYellow.copy(alpha = 0.14f), Color.Transparent),
                        radius = size.width * 0.6f
                    ),
                    center = Offset(size.width * 0.85f, size.height * 0.12f)
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(PmmYellow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = PmmBlueDeep, modifier = Modifier.size(36.dp))
                }
                Column {
                    Text(
                        eyebrow,
                        color = PmmYellow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    )
                    Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Text(
                        subtitle,
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Surface(
                        color = Color.White.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                        modifier = Modifier.padding(top = 18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text("Abrir", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = PmmYellow,
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Fila tappable a ancho completo, usada en pantallas angostas (teléfono). */
@Composable
private fun HubOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = PmmBgSurface),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PmmNavyBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(26.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = PmmTextPrimary, fontWeight = FontWeight.Black, fontSize = 17.sp)
                Text(subtitle, color = PmmTextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = PmmTextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
