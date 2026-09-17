package com.example.pmmovil.ui.settings.nfc

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pmmovil.data.nfc.NfcBadgeManager
import com.example.pmmovil.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NfcAssociationScreen(
    onNavigateBack: () -> Unit,
    viewModel: NfcAssociationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val state by viewModel.state.collectAsState()

    // La escucha vive con la pantalla completa (no solo en "Waiting"): así el
    // usuario puede acercar el gafete apenas entra, sin esperar el primer
    // tap en "Comenzar". El ViewModel ya ignora los toques fuera de Waiting.
    DisposableEffect(activity) {
        if (activity != null) {
            NfcBadgeManager.startListening(activity) { tag -> viewModel.onTagDiscovered(tag) }
        }
        onDispose {
            if (activity != null) NfcBadgeManager.stopListening(activity)
        }
    }

    BackHandler(enabled = state !is NfcAssocState.Intro) {
        when (state) {
            is NfcAssocState.Confirmed -> { viewModel.finish(); onNavigateBack() }
            else -> viewModel.cancel()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PmmBgPrimary)
    ) {
        NfcTopBar(
            onBack = {
                if (state is NfcAssocState.Confirmed) viewModel.finish()
                onNavigateBack()
            }
        )

        Box(modifier = Modifier.weight(1f)) {
            if (activity == null) return@Box

            when {
                !NfcBadgeManager.isAvailable(activity) -> UnsupportedContent()
                !NfcBadgeManager.isEnabled(activity) -> NfcDisabledContent(
                    onOpenSettings = {
                        context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                    }
                )
                else -> AnimatedContent(
                    targetState = state,
                    label = "nfcAssocState",
                    transitionSpec = {
                        (fadeIn(tween(Motion.DurationMedium, easing = Motion.Standard)) +
                            scaleIn(tween(Motion.DurationMedium, easing = Motion.EmphasizedDecelerate), initialScale = 0.97f))
                            .togetherWith(fadeOut(tween(Motion.DurationQuick, easing = Motion.Standard)))
                    }
                ) { s ->
                    when (s) {
                        is NfcAssocState.Intro -> IntroContent(
                            alreadyAssociated = viewModel.hasExistingAssociation(),
                            onStart = viewModel::startAssociation
                        )
                        is NfcAssocState.FetchingPayload -> FetchingContent()
                        is NfcAssocState.Waiting -> WaitingReadingContent(isReading = false, onCancel = viewModel::cancel)
                        is NfcAssocState.Reading -> WaitingReadingContent(isReading = true, onCancel = viewModel::cancel)
                        is NfcAssocState.Confirmed -> ConfirmedContent(
                            state = s,
                            onDone = { viewModel.finish(); onNavigateBack() }
                        )
                        is NfcAssocState.Conflict -> ConflictContent(
                            onOverwrite = viewModel::confirmOverwrite,
                            onCancel = viewModel::cancel
                        )
                        is NfcAssocState.ErrorState -> ErrorContent(
                            message = s.message,
                            onRetry = viewModel::retryAfterError
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NfcTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PmmBlueDeep)
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White, modifier = Modifier.size(19.dp))
        }
        Column {
            Text("Gafete NFC", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("Identificación", color = Color.White.copy(alpha = 0.65f), fontSize = 11.5.sp)
        }
    }
}

@Composable
private fun ScreenPad(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

@Composable
private fun IntroContent(alreadyAssociated: Boolean, onStart: () -> Unit) {
    ScreenPad {
        NfcBadge(size = 64.dp, background = PmmNavyVariant, tint = PmmBlueDeep)
        Spacer(Modifier.height(20.dp))
        Text(
            if (alreadyAssociated) "Actualiza tu gafete" else "Asocia tu gafete físico",
            color = PmmTextPrimary, fontWeight = FontWeight.Bold, fontSize = 19.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (alreadyAssociated)
                "Si tu contraseña cambió o reemitieron tu gafete QR, vuelve a acercar la misma etiqueta y se actualiza sola."
            else
                "Una vez asociado, acercarlo a la parte trasera del teléfono te deja entrar sin escanear ni teclear nada.",
            color = PmmTextSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp)
        )
        InfoNote(
            if (alreadyAssociated)
                "Si acercas la misma etiqueta que ya tenías, se actualiza directo, sin advertencias. Si acercas una distinta con datos, sí te preguntará antes de sobrescribir."
            else
                "Usa el mismo código de tu gafete QR — solo que ahora por contacto, no por cámara."
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PmmYellow, contentColor = PmmBlueDeep)
        ) {
            Text(
                if (alreadyAssociated) "Actualizar gafete" else "Comenzar asociación",
                fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun FetchingContent() {
    ScreenPad {
        Spacer(Modifier.weight(1f))
        CircularProgressIndicator(color = PmmBlueDeep, strokeWidth = 2.5.dp, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(16.dp))
        Text("Preparando tu gafete…", color = PmmTextSecondary, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun WaitingReadingContent(isReading: Boolean, onCancel: () -> Unit) {
    ScreenPad {
        Text("Acerca el gafete", color = PmmTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("a la parte trasera del teléfono", color = PmmTextSecondary, fontSize = 12.5.sp)

        Spacer(Modifier.height(20.dp))
        PhoneSilhouette(pulsing = !isReading)
        Spacer(Modifier.height(20.dp))

        val chipBg = if (isReading) Color(0xFFFEF9E7) else PmmNavyVariant
        val chipFg = if (isReading) PmmYellowDark else PmmBlueDeep
        val infiniteTransition = rememberInfiniteTransition(label = "chip_dot")
        val dotAlpha by infiniteTransition.animateFloat(
            initialValue = 1f, targetValue = 0.25f,
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
            label = "dotAlpha"
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(chipBg)
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(chipFg.copy(alpha = dotAlpha))
            )
            Text(
                if (isReading) "LEYENDO ETIQUETA…" else "ESPERANDO LECTURA…",
                color = chipFg, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 0.4.sp
            )
        }

        Spacer(Modifier.weight(1f))
        TextButton(onClick = onCancel, enabled = !isReading) {
            Text("Cancelar", color = PmmTextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun PhoneSilhouette(pulsing: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "hotspot")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = Motion.Standard), RepeatMode.Restart),
        label = "ringProgress"
    )

    Box(
        modifier = Modifier
            .size(width = 118.dp, height = 172.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF10192B)),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .padding(top = 14.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(0xFF2A3555))
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val hotspotCenter = Offset(size.width / 2f, 46.dp.toPx())
            if (pulsing) {
                listOf(0f, 0.33f, 0.66f).forEach { offset ->
                    val p = ((progress + offset) % 1f)
                    val radius = 7.dp.toPx() + p * 40.dp.toPx()
                    drawCircle(
                        color = PmmBlueDeep.copy(alpha = (1f - p) * 0.6f),
                        radius = radius,
                        center = hotspotCenter,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
            drawCircle(color = PmmYellow, radius = 6.dp.toPx(), center = hotspotCenter)
        }
    }
}

@Composable
private fun ConfirmedContent(state: NfcAssocState.Confirmed, onDone: () -> Unit) {
    ScreenPad {
        val scale = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            scale.animateTo(1f, animationSpec = spring(dampingRatio = Motion.SpringDampingLowBounce, stiffness = Motion.SpringStiffnessMedium))
        }
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color(0xFFF0FDF4)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = PmmGreenDark,
                modifier = Modifier
                    .size(38.dp)
                    .graphicsScale(scale.value)
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            if (state.alreadyWas) "Este gafete ya estaba asociado" else "¡Gafete asociado!",
            color = PmmTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text("A partir de ahora, acércalo al iniciar sesión.", color = PmmTextSecondary, fontSize = 12.5.sp, textAlign = TextAlign.Center)

        Spacer(Modifier.height(18.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = PmmBgSurface,
            border = BorderStroke(1.dp, PmmNavyBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NfcBadge(size = 40.dp, background = PmmBlueDeep, tint = Color.White, filled = true)
                Column {
                    Text("Gafete vinculado", color = PmmTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    Text(
                        "asociado " + formatWhen(state.alreadyWas),
                        color = PmmTextMuted, fontSize = 10.5.sp
                    )
                    Text(
                        "UID ${state.uid} · ${state.tech}",
                        color = PmmTextMuted, fontSize = 9.5.sp
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PmmYellow, contentColor = PmmBlueDeep)
        ) {
            Text("Listo", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ConflictContent(onOverwrite: () -> Unit, onCancel: () -> Unit) {
    ScreenPad {
        Box(
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFFEF3E2)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = PmmAmberDark, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("Esta etiqueta ya tiene datos", color = PmmTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text("Puede pertenecer a otro gafete o haberse asociado antes.", color = PmmTextSecondary, fontSize = 12.5.sp, textAlign = TextAlign.Center)

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFFEF9F0),
            border = BorderStroke(1.dp, Color(0xFFFBE3B8)),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text(
                "Si continúas, el contenido anterior se sobrescribirá y ese gafete dejará de funcionar para iniciar sesión.",
                color = PmmAmberDark, fontSize = 11.sp, lineHeight = 16.sp,
                modifier = Modifier.padding(12.dp)
            )
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = onOverwrite,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PmmAmberDark, contentColor = Color.White)
        ) {
            Text("Sobrescribir de todas formas", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancelar", color = PmmTextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    ScreenPad {
        Box(
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFFEE2E2)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = PmmRedDark, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("No se pudo asociar", color = PmmTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, color = PmmTextSecondary, fontSize = 12.5.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PmmYellow, contentColor = PmmBlueDeep)
        ) {
            Text("Reintentar", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun UnsupportedContent() {
    ScreenPad {
        Spacer(Modifier.weight(1f))
        Icon(Icons.Default.Info, contentDescription = null, tint = PmmTextMuted, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(14.dp))
        Text("Este dispositivo no tiene NFC", color = PmmTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            "Puedes seguir entrando con tu gafete QR o con usuario y contraseña.",
            color = PmmTextSecondary, fontSize = 12.5.sp, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 260.dp)
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun NfcDisabledContent(onOpenSettings: () -> Unit) {
    ScreenPad {
        Spacer(Modifier.weight(1f))
        NfcBadge(size = 56.dp, background = Color(0xFFFEF3E2), tint = PmmAmberDark)
        Spacer(Modifier.height(14.dp))
        Text("El NFC está apagado", color = PmmTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            "Actívalo en los ajustes del sistema para poder asociar tu gafete.",
            color = PmmTextSecondary, fontSize = 12.5.sp, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 260.dp)
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onOpenSettings,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep, contentColor = Color.White)
        ) {
            Text("Abrir ajustes de NFC", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun InfoNote(text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        modifier = Modifier
            .padding(top = 18.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PmmBgCard)
            .border(1.dp, PmmNavyBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = PmmBlueDeep, modifier = Modifier.size(15.dp))
        Text(text, color = PmmTextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

/** Ícono de ondas NFC dibujado a mano: evita depender de material-icons-extended. */
@Composable
private fun NfcBadge(size: androidx.compose.ui.unit.Dp, background: Color, tint: Color, filled: Boolean = false) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3.2f))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.5f)) {
            val strokeWidth = 2.1.dp.toPx()
            val center = Offset(size.toPx() * 0.32f, size.toPx() * 0.5f)
            drawCircle(color = tint, radius = 2.4.dp.toPx(), center = center)
            listOf(0.62f, 0.92f).forEach { factor ->
                drawArc(
                    color = tint,
                    startAngle = -55f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(center.x - this.size.minDimension * factor / 2f, center.y - this.size.minDimension * factor / 2f),
                    size = Size(this.size.minDimension * factor, this.size.minDimension * factor),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
    }
}

private fun formatWhen(alreadyWas: Boolean): String {
    val formatter = SimpleDateFormat("d MMM, HH:mm", Locale("es", "MX"))
    return if (alreadyWas) "anteriormente" else "hoy · ${formatter.format(Date())}"
}

private fun Modifier.graphicsScale(value: Float): Modifier = this.then(
    Modifier.graphicsLayer(scaleX = value, scaleY = value)
)
