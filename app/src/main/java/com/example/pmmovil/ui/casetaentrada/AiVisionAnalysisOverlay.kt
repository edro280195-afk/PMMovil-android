package com.example.pmmovil.ui.casetaentrada

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.io.File
import kotlin.math.roundToInt

private val VisionBg = Color(0xFF020617) // Azul oscuro profundo
private val VisionScrim = Color(0xFF020617)
private val VisionPanel = Color(0xFFFFFFFF) // Fondo blanco
private val VisionPanelSoft = Color(0xFFF1F5FA) // Fondo azul grisáceo claro
private val VisionInk = Color(0xFF0F172A) // Texto oscuro (Slate 900)
private val VisionMuted = Color(0xFF64748B) // Texto secundario (Slate 500)
private val VisionLine = Color(0x1F003DA5) // Borde azul sutil
private val VisionScan = Color(0xFF3B82F6) // Azul claro PMM
private val VisionMint = Color(0xFF003DA5) // Azul profundo PMM
private val VisionAmber = Color(0xFFD97706) // Ámbar de advertencia
private val VisionRed = Color(0xFFDC2626) // Rojo de error
private val AiMono = FontFamily.Monospace

@Composable
fun AiVisionAnalysisOverlay(
    imagePath: String?,
    ocrState: CasetaEntradaOcrState,
    finishedMessage: String?,
    onDismiss: () -> Unit
) {
    val phase = ocrState.phase
    val finished = finishedMessage != null ||
        phase == CasetaEntradaOcrPhase.Ready ||
        phase == CasetaEntradaOcrPhase.Failed
    val isError = ocrState.outcome == CasetaEntradaOcrOutcome.Error ||
        ocrState.outcome == CasetaEntradaOcrOutcome.ManualRequired
    val resolved = ocrState.primaryValue.ifBlank {
        ocrState.candidates.firstOrNull()?.value.orEmpty()
    }
    val success = finished && resolved.isNotBlank() && !isError
    val targetLabel = when (ocrState.mode) {
        CasetaEntradaCaptureMode.Box -> "caja"
        CasetaEntradaCaptureMode.Plate -> "placa"
        null -> "dato"
    }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (finished) 0.58f else 0.42f,
        animationSpec = tween(420),
        label = "visionScrim"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VisionBg)
        ) {
            if (imagePath != null) {
                AsyncImage(
                    model = File(imagePath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF252A22),
                                    VisionBg,
                                    Color(0xFF080A07)
                                )
                            )
                        )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(VisionScrim.copy(alpha = scrimAlpha))
            )

            if (!finished) {
                SheenSweep(modifier = Modifier.fillMaxSize())
            }

            VisionTopBar(
                title = if (finished) "${targetLabel.replaceFirstChar { it.uppercase() }} detectada" else "Revisando $targetLabel",
                eyebrow = if (finished) "Resultado" else "Foto capturada",
                onDismiss = onDismiss
            )

            VisionStatusChip(
                phase = phase,
                finished = finished,
                success = success,
                isError = isError,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 78.dp, start = 18.dp, end = 18.dp)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 40.dp)
                    .padding(bottom = 72.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LiveTextHighlight(
                    resolved = resolved,
                    success = success,
                    isError = isError && finished
                )
                AnimatedVisibility(visible = success) {
                    Surface(
                        color = VisionPanel.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, VisionMint.copy(alpha = 0.28f)),
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Text(
                            text = "${targetLabel.replaceFirstChar { it.uppercase() }} detectada, $resolved",
                            color = VisionInk,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            VisionAnalysisPanel(
                ocrState = ocrState,
                phase = phase,
                resolved = resolved,
                finished = finished,
                isError = isError,
                success = success,
                finishedMessage = finishedMessage,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun VisionTopBar(
    title: String,
    eyebrow: String,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(VisionScrim.copy(alpha = 0.86f), Color.Transparent)
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(VisionPanel.copy(alpha = 0.78f))
        ) {
            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = VisionInk)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = eyebrow.uppercase(),
                color = VisionScan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
                maxLines = 1
            )
            Text(
                text = title,
                color = VisionInk,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun VisionStatusChip(
    phase: CasetaEntradaOcrPhase,
    finished: Boolean,
    success: Boolean,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    val dotTransition = rememberInfiniteTransition(label = "visionDot")
    val dotAlpha by dotTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(760, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "visionDotAlpha"
    )
    val accent = when {
        success -> VisionMint
        isError && finished -> VisionAmber
        else -> VisionScan
    }
    val title = when {
        success -> "Lectura confiable"
        isError && finished -> "Necesita revision"
        else -> "Inspeccion en proceso"
    }

    Surface(
        color = VisionPanel.copy(alpha = 0.86f),
        shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, VisionLine),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(accent.copy(alpha = if (finished) 1f else dotAlpha))
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = VisionInk, fontSize = 13.sp, fontWeight = FontWeight.Black)
                Text(
                    text = phase.description,
                    color = VisionMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = if (finished) "OK" else "...",
                color = accent,
                fontFamily = AiMono,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun LiveTextHighlight(
    resolved: String,
    success: Boolean,
    isError: Boolean
) {
    val borderColor = when {
        success -> VisionMint
        isError -> VisionAmber
        else -> VisionScan
    }
    val fillColor = when {
        success -> VisionMint.copy(alpha = 0.13f)
        isError -> VisionAmber.copy(alpha = 0.13f)
        else -> VisionScan.copy(alpha = 0.10f)
    }
    val textColor = when {
        isError -> VisionAmber
        else -> VisionInk
    }

    Box(
        modifier = Modifier
            .size(width = 258.dp, height = 112.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(fillColor)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(targetState = success || isError, label = "visionNumberReveal") { done ->
                if (done) {
                    val scale by animateFloatAsState(
                        targetValue = 1f,
                        animationSpec = tween(280),
                        label = "visionNumberScale"
                    )
                    Text(
                        text = resolved.ifBlank { "Sin lectura" },
                        color = textColor,
                        fontFamily = AiMono,
                        fontSize = if (resolved.isBlank()) 18.sp else 32.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(horizontal = 14.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                    )
                } else {
                    ShimmerBar(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxWidth()
                            .height(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
            }
        }
        ScanLines(color = borderColor)
        CornerAccents(color = borderColor)
    }
}

@Composable
private fun ScanLines(color: Color) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.84f)
                .height(2.dp)
                .background(color)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp)
                .fillMaxWidth(0.76f)
                .height(1.dp)
                .background(color.copy(alpha = 0.42f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
                .fillMaxWidth(0.76f)
                .height(1.dp)
                .background(color.copy(alpha = 0.28f))
        )
    }
}

@Composable
private fun CornerAccents(color: Color) {
    Box(modifier = Modifier.fillMaxSize()) {
        listOf(
            Alignment.TopStart to (true to true),
            Alignment.TopEnd to (true to false),
            Alignment.BottomStart to (false to true),
            Alignment.BottomEnd to (false to false)
        ).forEach { (align, dir) ->
            val (top, start) = dir
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = align) {
                Box(modifier = Modifier.size(28.dp)) {
                    Box(
                        modifier = Modifier
                            .align(if (top) Alignment.TopCenter else Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(color)
                    )
                    Box(
                        modifier = Modifier
                            .align(if (start) Alignment.CenterStart else Alignment.CenterEnd)
                            .width(3.dp)
                            .fillMaxSize()
                            .background(color)
                    )
                }
            }
        }
    }
}

@Composable
private fun ShimmerBar(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "visionShimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "visionShimmerX"
    )
    Box(
        modifier = modifier.drawWithCache {
            val width = size.width
            val band = width * 0.55f
            val startX = -band + (width + band) * progress
            val brush = Brush.linearGradient(
                colors = listOf(
                    VisionInk.copy(alpha = 0.08f),
                    VisionScan.copy(alpha = 0.38f),
                    VisionInk.copy(alpha = 0.08f)
                ),
                start = Offset(startX, 0f),
                end = Offset(startX + band, 0f)
            )
            onDrawBehind {
                drawRect(VisionInk.copy(alpha = 0.07f))
                drawRect(brush)
            }
        }
    )
}

@Composable
private fun SheenSweep(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "visionSheen")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "visionSheenX"
    )
    Box(
        modifier = modifier.drawWithCache {
            val width = size.width
            val height = size.height
            val band = width * 0.30f
            val startX = -band + (width + band) * progress
            val brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    VisionScan.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                start = Offset(startX, 0f),
                end = Offset(startX + band, height)
            )
            onDrawBehind { drawRect(brush) }
        }
    )
}

private fun phaseProgress(phase: CasetaEntradaOcrPhase): Float = when (phase) {
    CasetaEntradaOcrPhase.Idle -> 0.05f
    CasetaEntradaOcrPhase.Captured -> 0.14f
    CasetaEntradaOcrPhase.PreparingImage -> 0.30f
    CasetaEntradaOcrPhase.ReadingLocal -> 0.48f
    CasetaEntradaOcrPhase.ValidatingCandidates -> 0.58f
    CasetaEntradaOcrPhase.ValidatingServer -> 0.74f
    CasetaEntradaOcrPhase.SearchingHistory -> 0.90f
    CasetaEntradaOcrPhase.Ready,
    CasetaEntradaOcrPhase.Failed -> 1f
}
@Composable
private fun VisionAnalysisPanel(
    ocrState: CasetaEntradaOcrState,
    phase: CasetaEntradaOcrPhase,
    resolved: String,
    finished: Boolean,
    isError: Boolean,
    success: Boolean,
    finishedMessage: String?,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = phaseProgress(phase),
        animationSpec = tween(420),
        label = "visionProgress"
    )
    val accent = when {
        success -> VisionMint
        isError && finished -> VisionAmber
        else -> VisionScan
    }
    val confidenceText = if (success) {
        ocrState.confidence?.let { "${(it * 100).roundToInt().coerceIn(0, 100)}%" }
    } else {
        null
    }

    Surface(
        color = VisionPanel.copy(alpha = 0.98f),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        border = BorderStroke(1.dp, VisionLine),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = accent.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.30f))
                ) {
                    Icon(
                        imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.padding(8.dp).size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (finished) {
                            if (success) "Revisa antes de aplicar" else "No salió claro"
                        } else {
                            "La IA está optimizando"
                        },
                        color = VisionInk,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = finishedMessage ?: ocrState.message ?: phase.description,
                        color = VisionMuted,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (confidenceText != null) {
                    Surface(
                        color = VisionMint.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, VisionMint.copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = confidenceText,
                            color = VisionMint,
                            fontFamily = AiMono,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            if (finished && resolved.isNotBlank()) {
                ResultHero(
                    value = resolved,
                    source = ocrState.source.ifBlank { "OCR + IA" },
                    candidates = ocrState.candidates.take(3)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = accent,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = when (phase) {
                                CasetaEntradaOcrPhase.PreparingImage -> "Optimizando contraste..."
                                CasetaEntradaOcrPhase.ReadingLocal -> "Extrayendo caracteres..."
                                CasetaEntradaOcrPhase.ValidatingCandidates -> "Filtrando datos..."
                                CasetaEntradaOcrPhase.ValidatingServer -> "Validando en servidor..."
                                CasetaEntradaOcrPhase.SearchingHistory -> "Buscando en historial..."
                                else -> "Procesando imagen..."
                            },
                            color = VisionInk,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Por favor, mantén estable la imagen",
                            color = VisionMuted,
                            fontSize = 11.sp
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = accent,
                    trackColor = VisionInk.copy(alpha = 0.10f)
                )
            }

            AnimatedVisibility(visible = finished && isError) {
                Surface(
                    color = VisionAmber.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, VisionAmber.copy(alpha = 0.36f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Acércate al número o usa captura manual.",
                        color = VisionInk,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultHero(
    value: String,
    source: String,
    candidates: List<com.example.pmmovil.data.model.CasetaEntradaOcrCandidate>
) {
    Surface(
        color = VisionPanelSoft,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, VisionMint.copy(alpha = 0.22f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "LECTURA PRINCIPAL",
                color = VisionMint,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            Text(
                text = value,
                color = VisionInk,
                fontFamily = AiMono,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ResultMiniField("Origen", source, Modifier.weight(1f))
                ResultMiniField("Candidatos", candidates.size.coerceAtLeast(1).toString(), Modifier.weight(1f))
            }
            if (candidates.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    candidates.forEachIndexed { index, candidate ->
                        Surface(
                            color = if (index == 0) VisionScan else VisionInk.copy(alpha = 0.07f),
                            shape = RoundedCornerShape(999.dp),
                            border = if (index == 0) null else BorderStroke(1.dp, VisionLine)
                        ) {
                            Text(
                                text = candidate.value,
                                color = if (index == 0) VisionBg else VisionInk,
                                fontFamily = AiMono,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultMiniField(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = VisionInk.copy(alpha = 0.06f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, VisionLine),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label.uppercase(), color = VisionMuted, fontSize = 9.sp, fontWeight = FontWeight.Black)
            Text(
                text = value,
                color = VisionInk,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
