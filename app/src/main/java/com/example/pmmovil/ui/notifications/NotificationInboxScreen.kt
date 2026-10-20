package com.example.pmmovil.ui.notifications

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pmmovil.data.model.NotificationInboxItem
import com.example.pmmovil.data.preferences.ModePreferences
import com.example.pmmovil.ui.revision.EtiquetaPerfilViewModel
import com.example.pmmovil.ui.revision.RevisionModeSelectorContent
import com.example.pmmovil.ui.theme.PmmAmber
import com.example.pmmovil.ui.theme.PmmAmberDark
import com.example.pmmovil.ui.theme.PmmBgCard
import com.example.pmmovil.ui.theme.PmmBgPrimary
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmBlueLight
import com.example.pmmovil.ui.theme.PmmBlueMid
import com.example.pmmovil.ui.theme.PmmGreen
import com.example.pmmovil.ui.theme.PmmGreenDark
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmNavyDark
import com.example.pmmovil.ui.theme.PmmNavyVariant
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.PmmYellow
import java.time.LocalDate

/**
 * Contenido de la bandeja que se muestra en el menú anclado a la campana del
 * Home. Conserva el callback de navegación para que también pueda reutilizarse
 * desde otra superficie contextual si hace falta.
 */
@Composable
fun NotificationInboxScreen(
    onNavigateBack: () -> Unit,
    onOpenRevision: (String, ModePreferences.Mode) -> Unit,
    viewModel: NotificationInboxViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var selectedNotificationId by rememberSaveable { mutableStateOf<Long?>(null) }
    var isOpeningRevision by remember { mutableStateOf(false) }
    val selectedNotification = state.items.firstOrNull { it.id == selectedNotificationId }
        ?.takeIf { !it.crefAgrupada.isNullOrBlank() }

    BackHandler {
        if (selectedNotification != null) {
            selectedNotificationId = null
            isOpeningRevision = false
        } else {
            onNavigateBack()
        }
    }

    if (selectedNotification != null) {
        NotificationRevisionSelectionPanel(
            selectedNotification = selectedNotification,
            preferredMode = viewModel.lastModeFor(selectedNotification.crefAgrupada.orEmpty()),
            isOpeningRevision = isOpeningRevision,
            onBack = {
                selectedNotificationId = null
                isOpeningRevision = false
            },
            onModeSelected = { mode ->
                isOpeningRevision = true
                viewModel.rememberMode(selectedNotification.crefAgrupada.orEmpty(), mode)
                onOpenRevision(selectedNotification.crefAgrupada.orEmpty(), mode)
            }
        )
    } else {
        NotificationListPanel(
            state = state,
            onMarkAllRead = viewModel::markAllRead,
            onRefresh = viewModel::refresh,
            onSelect = { item ->
                if (item.readAt == null) viewModel.markRead(item.id)
                if (!item.crefAgrupada.isNullOrBlank()) {
                    selectedNotificationId = item.id
                }
            },
            onClose = onNavigateBack
        )
    }
}

@Composable
private fun NotificationRevisionSelectionPanel(
    selectedNotification: NotificationInboxItem,
    preferredMode: ModePreferences.Mode,
    isOpeningRevision: Boolean,
    onBack: () -> Unit,
    onModeSelected: (ModePreferences.Mode) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp),
        shape = RoundedCornerShape(24.dp),
        color = PmmBgSurface,
        tonalElevation = 4.dp,
        shadowElevation = 12.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PmmNavyDark,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(start = 8.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar a notificaciones",
                            tint = Color.White
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp)
                    ) {
                        Text(
                            text = "Iniciar Revisión",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Selecciona el método de captura",
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            val perfilEtiquetas: EtiquetaPerfilViewModel = hiltViewModel()
            LaunchedEffect(selectedNotification.crefAgrupada) {
                perfilEtiquetas.resolver(selectedNotification.crefAgrupada)
            }
            val grupoDeEtiquetas by perfilEtiquetas.grupo.collectAsState()

            RevisionModeSelectorContent(
                crefAgrupada = selectedNotification.crefAgrupada.orEmpty(),
                contextLabel = selectedNotification.title,
                supportingText = selectedNotification.body,
                preferredMode = preferredMode,
                isOpening = isOpeningRevision,
                onModeSelected = onModeSelected,
                onBack = null,
                grupoDeEtiquetas = grupoDeEtiquetas
            )
        }
    }
}

@Composable
private fun NotificationListPanel(
    state: NotificationInboxUiState,
    onMarkAllRead: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (NotificationInboxItem) -> Unit,
    onClose: () -> Unit
) {
    val visibleItems = state.items

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 560.dp),
        shape = RoundedCornerShape(24.dp),
        color = PmmBgSurface,
        tonalElevation = 4.dp,
        shadowElevation = 12.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PmmNavyDark,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 15.dp, bottom = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(PmmYellow),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = PmmNavyDark,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Notificaciones",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                            if (state.unreadCount > 0) {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = PmmYellow,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (state.unreadCount > 99) "99+" else state.unreadCount.toString(),
                                        color = PmmNavyDark,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = notificationSummary(state),
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar notificaciones",
                            tint = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = PmmNavyVariant,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (visibleItems.size == 1) "1 aviso" else "${visibleItems.size} avisos",
                        color = PmmBlueDeep,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                if (state.unreadCount > 0) {
                    TextButton(onClick = onMarkAllRead) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Marcar leídas", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Actualizar notificaciones",
                            tint = PmmBlueDeep
                        )
                    }
                }
            }

            if (state.isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = PmmYellow,
                    trackColor = PmmNavyVariant
                )
            }

            HorizontalDivider(color = PmmNavyBorder)

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PmmBlueDeep, strokeWidth = 2.dp)
                    }
                }

                state.errorMessage != null -> {
                    NotificationErrorState(
                        message = state.errorMessage,
                        onRetry = onRefresh
                    )
                }

                visibleItems.isEmpty() -> NotificationEmptyState()

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        items(visibleItems, key = { it.id }) { item ->
                            NotificationInboxCard(
                                item = item,
                                onClick = { onSelect(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationInboxCard(
    item: NotificationInboxItem,
    onClick: () -> Unit
) {
    val isUnread = item.readAt == null
    val visual = notificationVisual(item)

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (isUnread) PmmBgSurface else PmmBgCard,
        tonalElevation = if (isUnread) 2.dp else 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (isUnread) visual.accent.copy(alpha = 0.28f) else PmmNavyBorder
        )
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(visual.accent.copy(alpha = if (isUnread) 0.14f else 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = visual.icon,
                    contentDescription = null,
                    tint = visual.accentDark.copy(alpha = if (isUnread) 1f else 0.62f),
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = visual.eyebrow,
                        color = visual.accentDark.copy(alpha = if (isUnread) 1f else 0.72f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.7.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isUnread) {
                        Spacer(Modifier.width(7.dp))
                        Surface(
                            color = PmmYellow,
                            shape = RoundedCornerShape(7.dp)
                        ) {
                            Text(
                                text = "NUEVA",
                                color = PmmNavyDark,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                Text(
                    text = item.title,
                    color = if (isUnread) PmmTextPrimary else PmmTextSecondary,
                    fontWeight = if (isUnread) FontWeight.Black else FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Text(
                    text = item.body,
                    color = PmmTextSecondary.copy(alpha = if (isUnread) 1f else 0.82f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(
                    modifier = Modifier.padding(top = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = PmmBgPrimary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = notificationTimeLabel(item.createdAt),
                            color = PmmTextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        )
                    }
                    if (!item.crefAgrupada.isNullOrBlank()) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "Ver entrada",
                            color = PmmBlueDeep,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = PmmBlueDeep,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

private data class NotificationVisual(
    val icon: ImageVector,
    val accent: Color,
    val accentDark: Color,
    val eyebrow: String
)

private fun notificationVisual(item: NotificationInboxItem): NotificationVisual {
    val type = item.type.lowercase()
    return when {
        type.contains("asign") || type.contains("revision") -> NotificationVisual(
            icon = Icons.AutoMirrored.Filled.Assignment,
            accent = PmmBlueLight,
            accentDark = PmmBlueDeep,
            eyebrow = "REVISIÓN"
        )

        type.contains("autor") || type.contains("complet") -> NotificationVisual(
            icon = Icons.Default.TaskAlt,
            accent = PmmGreen,
            accentDark = PmmGreenDark,
            eyebrow = "OPERACIÓN"
        )

        type.contains("bloque") || type.contains("alert") -> NotificationVisual(
            icon = Icons.Default.WarningAmber,
            accent = PmmAmber,
            accentDark = PmmAmberDark,
            eyebrow = "ATENCIÓN"
        )

        type.contains("salida") || type.contains("relacion") -> NotificationVisual(
            icon = Icons.Default.Inventory2,
            accent = PmmBlueMid,
            accentDark = PmmBlueDeep,
            eyebrow = "MOVIMIENTO"
        )

        else -> NotificationVisual(
            icon = Icons.Default.NotificationsActive,
            accent = PmmBlueMid,
            accentDark = PmmBlueDeep,
            eyebrow = "AVISO"
        )
    }
}

private fun notificationSummary(state: NotificationInboxUiState): String {
    return when {
        state.isLoading -> "Consultando avisos de tu operación..."
        state.unreadCount > 0 -> "${state.unreadCount} pendientes de tu operación"
        state.items.isNotEmpty() -> "Todo revisado · ${state.items.size} avisos guardados"
        else -> "Tu operación está al día"
    }
}

@Composable
private fun NotificationEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(PmmNavyVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = null,
                tint = PmmBlueDeep,
                modifier = Modifier.size(29.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Tu bandeja está limpia",
            color = PmmTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Cuando haya una entrada nueva o una acción importante, aparecerá aquí.",
            color = PmmTextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun NotificationErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            color = PmmTextSecondary,
            textAlign = TextAlign.Center,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) {
            Text("Reintentar", fontWeight = FontWeight.Bold)
        }
    }
}

private fun notificationTimeLabel(createdAt: String?): String {
    val value = createdAt?.trim()?.takeIf { it.isNotBlank() } ?: return "Ahora"
    val normalized = value.replace('T', ' ').removeSuffix("Z")
    val date = normalized.substringBefore(' ')
    val time = normalized.substringAfter(' ', "").take(5)

    return when {
        date == LocalDate.now().toString() && time.isNotBlank() -> "Hoy, $time"
        date.isNotBlank() && time.isNotBlank() -> "$date · $time"
        else -> normalized.take(16)
    }
}
