package com.example.pmmovil.ui.settings

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pmmovil.data.biometria.FaceEngine
import com.example.pmmovil.data.repository.AuthRepository
import com.example.pmmovil.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onOpenNfcAssociation: () -> Unit,
    onOpenRostro: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var nfcInfo by remember { mutableStateOf<AuthRepository.NfcAssociationInfo?>(viewModel.getNfcAssociationInfo()) }
    val rostro by viewModel.rostro.collectAsStateWithLifecycle()

    // El estado de "asociado" y el del rostro los cambian otras pantallas; al
    // volver aquí (ON_RESUME) hay que releerlos, no quedan reactivos solos.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                nfcInfo = viewModel.getNfcAssociationInfo()
                viewModel.refrescarRostro()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val context = LocalContext.current
    val rostroDisponible = FaceEngine.isAvailable(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PmmBgPrimary)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PmmBgSurface)
                .statusBarsPadding()
                .padding(start = 16.dp, end = 18.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(PmmNavyVariant)
                    .clickable { onNavigateBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = stringResource(com.example.pmmovil.R.string.settings_back),
                    tint = PmmBlueDeep,
                    modifier = Modifier.size(19.dp)
                )
            }
            Text(
                stringResource(com.example.pmmovil.R.string.settings_title),
                color = PmmTextPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 26.sp,
                letterSpacing = (-0.8).sp
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(top = 12.dp, bottom = 28.dp)
        ) {
            // Entrada escalonada: la pantalla se abre, no aparece de golpe.
            var indice = 0

            Escalonado(indice++) {
                TarjetaIdentidad(
                    nombre = viewModel.getUserName() ?: stringResource(com.example.pmmovil.R.string.settings_user_default),
                    // El lugar es un código ("09") que no dice nada a quien lo lee.
                    detalle = listOfNotNull(
                        viewModel.getUserUsername(),
                        viewModel.getUserArea()
                    ).joinToString(" · ")
                )
            }

            Escalonado(indice++) {
                LanguagePreference()
            }

            Escalonado(indice++) {
                EtiquetaGrupo(stringResource(com.example.pmmovil.R.string.settings_access_group))
            }

            // El rostro va primero porque es lo único de esta pantalla que
            // puede estar sin configurar, y es a lo que se viene.
            if (rostroDisponible) {
                Escalonado(indice++) {
                    val info = rostro
                    FilaAjuste(
                        icono = Icons.Outlined.Face,
                        fondoIcono = null,
                        tinteIcono = Color.White,
                        titulo = stringResource(com.example.pmmovil.R.string.settings_face_title),
                        subtitulo = when {
                            info == null -> stringResource(com.example.pmmovil.R.string.settings_face_login_subtitle)
                            info.enrolado -> pluralStringResource(
                                com.example.pmmovil.R.plurals.settings_face_captures,
                                info.plantillas,
                                info.plantillas
                            )
                            else -> stringResource(com.example.pmmovil.R.string.settings_face_unconfigured_subtitle)
                        },
                        etiqueta = when {
                            info == null -> null
                            info.enrolado -> stringResource(com.example.pmmovil.R.string.settings_status_active)
                            else -> stringResource(com.example.pmmovil.R.string.settings_status_configure)
                        },
                        etiquetaOk = info?.enrolado == true,
                        destacada = true,
                        onClick = onOpenRostro
                    )
                }
            }

            Escalonado(indice++) {
                FilaAjuste(
                    icono = Icons.Default.Nfc,
                    fondoIcono = Color(0xFFFEF9E7),
                    tinteIcono = PmmAmberDark,
                    titulo = stringResource(com.example.pmmovil.R.string.settings_nfc_title),
                    subtitulo = nfcInfo?.let { info ->
                        formatWhen(info.associatedAtMillis)?.let { date ->
                            stringResource(com.example.pmmovil.R.string.settings_nfc_associated_at, date)
                        } ?: stringResource(com.example.pmmovil.R.string.settings_nfc_associated)
                    } ?: stringResource(com.example.pmmovil.R.string.settings_nfc_unassociated),
                    etiqueta = if (nfcInfo != null) {
                        stringResource(com.example.pmmovil.R.string.settings_status_active)
                    } else {
                        stringResource(com.example.pmmovil.R.string.settings_nfc_status_pending)
                    },
                    etiquetaOk = nfcInfo != null,
                    onClick = onOpenNfcAssociation
                )
            }

            Escalonado(indice++) {
                FilaAjuste(
                    icono = Icons.Default.QrCode2,
                    fondoIcono = PmmNavyVariant,
                    tinteIcono = PmmBlueDeep,
                    titulo = stringResource(com.example.pmmovil.R.string.settings_qr_title),
                    subtitulo = stringResource(com.example.pmmovil.R.string.settings_qr_subtitle),
                    etiqueta = stringResource(com.example.pmmovil.R.string.settings_status_active),
                    etiquetaOk = true
                )
            }
        }
    }
}

/** Cada fila entra con un pequeño retraso sobre la anterior. */
@Composable
private fun Escalonado(indice: Int, contenido: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val avance by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = Motion.DurationEmphasized,
            delayMillis = indice * Motion.DurationStagger,
            easing = LinearOutSlowInEasing
        ),
        label = "entrada-$indice"
    )

    Box(
        modifier = Modifier
            .alpha(avance)
            .offset(y = ((1f - avance) * 14f).dp)
    ) {
        contenido()
    }
}

@Composable
private fun TarjetaIdentidad(nombre: String, detalle: String) {
    val iniciales = nombre.trim().split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = PmmBlueDeep,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                // Un halo amarillo apenas visible en la esquina: es el acento
                // de PMM, y basta con insinuarlo.
                .drawBehind {
                    drawCircle(
                        color = PmmYellow.copy(alpha = 0.14f),
                        radius = size.height * 1.1f,
                        center = Offset(size.width * 0.95f, -size.height * 0.35f)
                    )
                }
                .padding(horizontal = 17.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    iniciales,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    letterSpacing = (-0.5).sp
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    nombre,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.5.sp,
                    letterSpacing = (-0.2).sp
                )
                if (detalle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        detalle,
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 11.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun EtiquetaGrupo(texto: String) {
    Text(
        texto.uppercase(Locale.getDefault()),
        color = PmmTextMuted,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp,
        modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 9.dp)
    )
}

@Composable
private fun LanguagePreference() {
    val currentLanguage = LocalConfiguration.current.locales[0].language
    Column(modifier = Modifier.fillMaxWidth()) {
        EtiquetaGrupo(stringResource(com.example.pmmovil.R.string.settings_language_section))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 9.dp),
            shape = RoundedCornerShape(18.dp),
            color = PmmBgSurface,
            border = BorderStroke(1.dp, PmmNavyBorder),
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp)) {
                Text(
                    text = stringResource(com.example.pmmovil.R.string.settings_language_title),
                    color = PmmTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(com.example.pmmovil.R.string.settings_language_subtitle),
                    color = PmmTextSecondary,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(11.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LanguageButton(
                        modifier = Modifier.weight(1f),
                        selected = currentLanguage != "en",
                        label = stringResource(com.example.pmmovil.R.string.language_spanish),
                        onClick = {
                            AppCompatDelegate.setApplicationLocales(
                                LocaleListCompat.forLanguageTags("es-MX")
                            )
                        }
                    )
                    LanguageButton(
                        modifier = Modifier.weight(1f),
                        selected = currentLanguage == "en",
                        label = stringResource(com.example.pmmovil.R.string.language_english),
                        onClick = {
                            AppCompatDelegate.setApplicationLocales(
                                LocaleListCompat.forLanguageTags("en")
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageButton(
    modifier: Modifier,
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier.heightIn(min = 46.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text(label, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 46.dp),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text(label, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FilaAjuste(
    icono: ImageVector,
    fondoIcono: Color?,
    tinteIcono: Color,
    titulo: String,
    subtitulo: String,
    etiqueta: String? = null,
    etiquetaOk: Boolean = false,
    destacada: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val interaccion = remember { MutableInteractionSource() }
    val presionada by interaccion.collectIsPressedAsState()
    val escala by animateFloatAsState(
        targetValue = if (presionada) 0.978f else 1f,
        animationSpec = tween(Motion.DurationQuick, easing = Motion.Standard),
        label = "pulsacion-fila"
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = PmmBgSurface,
        border = BorderStroke(1.dp, if (destacada) Color(0xFFDBE7FF) else PmmNavyBorder),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 9.dp)
            .scale(escala)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaccion, indication = null, onClick = onClick)
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .then(
                    if (destacada) {
                        Modifier.background(
                            Brush.horizontalGradient(
                                listOf(Color.White, Color(0xFFF7FAFF), Color(0xFFEEF4FF))
                            )
                        )
                    } else Modifier
                )
                .padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .then(
                        if (fondoIcono != null) {
                            Modifier.background(fondoIcono)
                        } else {
                            Modifier.background(
                                Brush.linearGradient(listOf(PmmBlueDeep, PmmBlueMid))
                            )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icono, contentDescription = null, tint = tinteIcono, modifier = Modifier.size(19.dp))
            }

            Column(Modifier.weight(1f)) {
                Text(
                    titulo,
                    color = PmmTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    letterSpacing = (-0.1).sp
                )
                Spacer(Modifier.height(2.dp))
                Text(subtitulo, color = PmmTextSecondary, fontSize = 12.sp)
            }

            if (etiqueta != null) {
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = if (etiquetaOk) Color(0xFFF0FDF4) else Color(0xFFFFFBEB)
                ) {
                    Text(
                        etiqueta,
                        color = if (etiquetaOk) PmmGreenDark else PmmAmberDark,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            if (onClick != null) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = PmmTextMuted,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

private fun formatWhen(atMillis: Long): String? {
    if (atMillis <= 0L) return null
    val formatter = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
    return formatter.format(Date(atMillis))
}
