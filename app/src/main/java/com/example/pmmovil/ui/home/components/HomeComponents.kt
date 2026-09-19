package com.example.pmmovil.ui.home.components

import androidx.annotation.StringRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.data.model.MobileModuleIds
import com.example.pmmovil.R
import com.example.pmmovil.ui.home.HomeModuleRouteKeys
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.composed
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.Spring
import com.example.pmmovil.ui.components.StatusKind
import com.example.pmmovil.ui.home.smartGreetingRes
import com.example.pmmovil.ui.theme.Motion
import com.example.pmmovil.ui.theme.PmmAmberDark
import com.example.pmmovil.ui.theme.PmmBgCard
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmBlueLight
import com.example.pmmovil.ui.theme.PmmBlueMid
import com.example.pmmovil.ui.theme.PmmGreen
import com.example.pmmovil.ui.theme.PmmGreenDark
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmNavyDark
import com.example.pmmovil.ui.theme.PmmNavyVariant
import com.example.pmmovil.ui.theme.PmmRed
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.PmmYellow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ═════════════════════════════════════════════════════════════════════════════
// 1) BRANDED HERO HEADER & HELPERS
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun PatioPill(lugar: String) {
    Surface(
        color = Color.Transparent,
        border = BorderStroke(1.dp, PmmYellow),
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(PmmYellow)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = lugar.uppercase(),
                color = PmmYellow,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun CompactStatusIndicator(kind: StatusKind) {
    val color = when (kind) {
        StatusKind.Online -> PmmGreen
        StatusKind.Offline -> PmmRed
        StatusKind.Syncing -> PmmYellow
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun BrandedHeroHeader(
    userName: String?,
    username: String?,
    notificationUnreadCount: Int = 0,
    onOpenNotifications: () -> Unit = {},
    onOpenPrinterSettings: () -> Unit,
    onOpenSettings: () -> Unit = {},
    showSystemTools: Boolean = false,
    onLogout: () -> Unit
) {
    val displayName = userName
        ?.trim()
        ?.ifBlank { null }
        ?: username?.trim()?.ifBlank { null }
        ?: stringResource(R.string.home_user_default)

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp

    if (screenWidth >= 900) {
        // Landscape Tablet Layout (Rounded top banner card)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .shadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(20.dp),
                    clip = false,
                    ambientColor = PmmBlueDeep.copy(alpha = 0.12f),
                    spotColor = PmmBlueDeep.copy(alpha = 0.20f)
                ),
            shape = RoundedCornerShape(20.dp),
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                PmmNavyDark,
                                PmmBlueDeep,
                                PmmBlueMid
                            )
                        )
                    )
            ) {
                AmbientOrbs()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PmmBrandingCompact()

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${stringResource(smartGreetingRes())},",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.2.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = displayName,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.4).sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NotificationBubble(
                            unreadCount = notificationUnreadCount,
                            onClick = onOpenNotifications
                        )
                        Spacer(Modifier.width(8.dp))
                        if (showSystemTools) {
                            IconBubble(icon = Icons.Default.Settings, contentDescription = stringResource(R.string.home_action_printers), onClick = onOpenPrinterSettings)
                            Spacer(Modifier.width(8.dp))
                        }
                        IconBubble(icon = Icons.Default.Nfc, contentDescription = stringResource(R.string.home_action_identity), onClick = onOpenSettings)
                        Spacer(Modifier.width(8.dp))
                        IconBubble(icon = Icons.Default.Logout, contentDescription = stringResource(R.string.home_action_logout), onClick = onLogout)
                    }
                }
            }
        }
    } else if (screenWidth >= 600) {
        // Portrait Tablet Layout
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            PmmNavyDark,
                            PmmBlueDeep,
                            PmmBlueMid
                        )
                    )
                )
        ) {
            AmbientOrbs()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PmmBrandingCompact()

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NotificationBubble(
                            unreadCount = notificationUnreadCount,
                            onClick = onOpenNotifications
                        )
                        Spacer(Modifier.width(8.dp))
                        if (showSystemTools) {
                            IconBubble(icon = Icons.Default.Settings, contentDescription = stringResource(R.string.home_action_printers), onClick = onOpenPrinterSettings)
                            Spacer(Modifier.width(8.dp))
                        }
                        IconBubble(icon = Icons.Default.Nfc, contentDescription = stringResource(R.string.home_action_identity), onClick = onOpenSettings)
                        Spacer(Modifier.width(8.dp))
                        IconBubble(icon = Icons.Default.Logout, contentDescription = stringResource(R.string.home_action_logout), onClick = onLogout)
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(smartGreetingRes()),
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = displayName,
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.8).sp
                )
            }
        }
    } else {
        // Mobile Layout (Compact)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            PmmNavyDark,
                            PmmBlueDeep,
                            PmmBlueMid
                        )
                    )
                )
        ) {
            AmbientOrbs()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PmmBrandingCompact()

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NotificationBubble(
                            unreadCount = notificationUnreadCount,
                            onClick = onOpenNotifications
                        )
                        Spacer(Modifier.width(8.dp))
                        if (showSystemTools) {
                            IconBubble(icon = Icons.Default.Settings, contentDescription = stringResource(R.string.home_action_printers), onClick = onOpenPrinterSettings)
                            Spacer(Modifier.width(8.dp))
                        }
                        IconBubble(icon = Icons.Default.Nfc, contentDescription = stringResource(R.string.home_action_identity), onClick = onOpenSettings)
                        Spacer(Modifier.width(8.dp))
                        IconBubble(icon = Icons.Default.Logout, contentDescription = stringResource(R.string.home_action_logout), onClick = onLogout)
                    }
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = stringResource(smartGreetingRes()),
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = displayName,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.6).sp
                )
            }
        }
    }
}

@Composable
private fun PmmBrandingCompact() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "PMM",
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            letterSpacing = (-0.5).sp
        )
        Text(
            text = "ovil",
            color = PmmYellow,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            letterSpacing = (-0.5).sp
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(16.dp)
                .background(Color.White.copy(alpha = 0.25f))
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "IMEX",
            color = Color.White.copy(alpha = 0.8f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun IconBubble(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    sizeDp: Int = 32
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size((sizeDp - 14).dp)
        )
    }
}

@Composable
private fun NotificationBubble(
    unreadCount: Int,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Box {
        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
            modifier = Modifier
                .size(36.dp)
                .shadow(
                    elevation = if (unreadCount > 0) 5.dp else 0.dp,
                    shape = CircleShape,
                    clip = false
                ),
            color = if (unreadCount > 0) {
                PmmBlueDeep.copy(alpha = 0.42f)
            } else {
                Color.White.copy(alpha = 0.12f)
            },
            shape = CircleShape,
            border = if (unreadCount > 0) {
                BorderStroke(1.dp, PmmYellow.copy(alpha = 0.72f))
            } else {
                null
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = stringResource(R.string.home_action_notifications),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (unreadCount > 0) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 5.dp, y = (-5).dp),
                color = PmmYellow,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, PmmNavyDark.copy(alpha = 0.55f))
            ) {
                Text(
                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                    color = PmmNavyDark,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun AmbientOrbs() {
    val transition = rememberInfiniteTransition(label = "orbs")
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = Motion.Standard),
            repeatMode = RepeatMode.Reverse
        ),
        label = "x"
    )
    val y by transition.animateFloat(
        initialValue = 0f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(22000, easing = Motion.Standard),
            repeatMode = RepeatMode.Reverse
        ),
        label = "y"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(PmmYellow.copy(alpha = 0.18f), Color.Transparent),
                radius = 320f
            ),
            center = Offset(size.width * 0.85f + x, -50f + y)
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF60A5FA).copy(alpha = 0.18f), Color.Transparent),
                radius = 360f
            ),
            center = Offset(-50f + x, size.height * 0.6f - y)
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 6) SECTION HEADER
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun SectionHeader(
    title: String,
    badge: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = PmmTextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.2).sp
        )
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(PmmNavyVariant)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badge,
                    color = PmmBlueDeep,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                text = action,
                color = PmmTextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 7) MODULE CARD V2
// ═════════════════════════════════════════════════════════════════════════════

data class ModuleItem(
    val moduleId: Int,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: ImageVector,
    val accent: Color,
    val accentContainer: Color,
    val enabled: Boolean,
    val badge: String? = null,
    val routeKey: String = ""
)

@Composable
fun ModuleCardV2(
    item: ModuleItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = stringResource(item.titleRes)
    val subtitle = stringResource(item.subtitleRes)
    val haptic = LocalHapticFeedback.current
    val isEnabled = item.enabled
    val baseColor = if (isEnabled) PmmBgSurface else PmmBgCard
    val iconBg = if (isEnabled) item.accentContainer else PmmNavyBorder.copy(alpha = 0.3f)
    val iconTint = if (isEnabled) item.accent else PmmTextMuted
    val titleColor = if (isEnabled) PmmTextPrimary else PmmTextMuted
    val subColor = if (isEnabled) PmmTextSecondary else PmmTextMuted.copy(alpha = 0.7f)

    val screenWidth = LocalConfiguration.current.screenWidthDp
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val isTablet = screenWidth >= 600

    val cardMinHeight = when {
        screenWidth >= 900 -> (screenHeight * 0.20f).dp.coerceIn(140.dp, 200.dp)
        screenWidth >= 600 -> (screenHeight * 0.16f).dp.coerceIn(120.dp, 170.dp)
        else -> 104.dp
    }

    val iconBoxSize = if (isTablet) 64.dp else 52.dp
    val iconSize = if (isTablet) 38.dp else 30.dp
    val titleSize = if (isTablet) 19.sp else 16.sp
    val subSize = if (isTablet) 13.sp else 11.sp
    val paddingH = if (isTablet) 20.dp else 14.dp
    val paddingV = if (isTablet) 18.dp else 14.dp

    Surface(
        modifier = modifier
            .heightIn(min = cardMinHeight)
            .bounceClick(enabled = isEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .shadow(
                elevation = if (isEnabled) 1.dp else 0.dp,
                shape = RoundedCornerShape(18.dp),
                clip = false,
                ambientColor = if (isEnabled) Color(0xFF0F172A).copy(alpha = 0.04f) else Color.Transparent,
                spotColor = if (isEnabled) Color(0xFF0F172A).copy(alpha = 0.06f) else Color.Transparent
            ),
        shape = RoundedCornerShape(18.dp),
        color = baseColor,
        border = BorderStroke(1.dp, if (isEnabled) PmmNavyBorder else PmmNavyBorder.copy(alpha = 0.5f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isEnabled) Brush.horizontalGradient(
                        colors = listOf(
                            item.accentContainer.copy(alpha = 0.35f),
                            Color.White
                        )
                    ) else SolidColor(PmmBgCard)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = cardMinHeight)
                    .padding(horizontal = paddingH, vertical = paddingV),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(iconBoxSize)
                        .clip(RoundedCornerShape(14.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(iconSize)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = titleColor,
                        fontSize = titleSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        color = subColor,
                        fontSize = subSize,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (item.badge != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(PmmYellow)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.badge,
                            color = PmmBlueDeep,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModuleGrid(
    items: List<ModuleItem>,
    onItemClick: (ModuleItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        items.chunked(3).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                rowItems.forEach { item ->
                    ModuleCardV2(
                        item = item,
                        onClick = { onItemClick(item) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// Catalogo de modulos visibles. Solo se muestran opciones listas para usarse.
fun defaultModules(): List<ModuleItem> = listOf(
    ModuleItem(MobileModuleIds.REVIEW, R.string.home_module_review_title, R.string.home_module_review_subtitle, Icons.Default.QrCode, PmmBlueDeep, Color(0xFFEEF3FF), enabled = true),
    ModuleItem(MobileModuleIds.WAREHOUSE_OPERATIONS, R.string.home_module_entries_title, R.string.home_module_entries_subtitle, Icons.Default.Login, PmmBlueMid, Color(0xFFE0EBFF), enabled = true, routeKey = HomeModuleRouteKeys.WAREHOUSE_ENTRIES),
    ModuleItem(MobileModuleIds.WAREHOUSE_EXIT, R.string.home_module_load_title, R.string.home_module_load_subtitle, Icons.Default.Logout, PmmBlueLight, Color(0xFFDBEAFE), enabled = true, routeKey = HomeModuleRouteKeys.WAREHOUSE_LOADS),
    ModuleItem(MobileModuleIds.WAREHOUSE_RELATION, R.string.home_module_relation_title, R.string.home_module_relation_subtitle, Icons.Default.ViewModule, PmmNavyDark, Color(0xFFE0E7FF), enabled = true, routeKey = HomeModuleRouteKeys.WAREHOUSE_RELATION),
    ModuleItem(MobileModuleIds.WAREHOUSE_OPERATIONS, R.string.home_module_map_title, R.string.home_module_map_subtitle, Icons.Default.Map, PmmGreenDark, Color(0xFFD1FAE5), enabled = true, routeKey = HomeModuleRouteKeys.WAREHOUSE_MAP),
    ModuleItem(MobileModuleIds.GATE, R.string.home_module_gate_title, R.string.home_module_gate_subtitle, Icons.Default.Storefront, PmmAmberDark, Color(0xFFFEF3C7), enabled = true, routeKey = HomeModuleRouteKeys.GATE)
)

// Caseta (vigilancia): registro de entrada y salida en la caseta de seguridad.
fun casetaModules(): List<ModuleItem> = listOf(
    ModuleItem(
        moduleId = MobileModuleIds.GATE,
        titleRes = R.string.home_module_gate_card_title,
        subtitleRes = R.string.home_module_gate_card_subtitle,
        icon = Icons.Default.LocalShipping,
        accent = PmmGreenDark,
        accentContainer = Color(0xFFD1FAE5),
        enabled = true,
        routeKey = HomeModuleRouteKeys.GATE_HUB
    )
)

fun adminModules(): List<ModuleItem> = listOf(
    ModuleItem(
        moduleId = MobileModuleIds.SYSTEMS_MONITOR,
        titleRes = R.string.home_module_systems_title,
        subtitleRes = R.string.home_module_systems_subtitle,
        icon = Icons.Default.Settings,
        accent = PmmRed,
        accentContainer = Color(0xFFFEE2E2),
        enabled = true,
        routeKey = HomeModuleRouteKeys.SYSTEMS_ADMIN
    )
)

// ═════════════════════════════════════════════════════════════════════════════
// 9) SYSTEM FOOTER
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun SystemFooter(
    lastSyncMillis: Long?,
    onTap: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val sdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeStr = lastSyncMillis?.let { sdf.format(Date(it)) } ?: "--:--"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onTap()
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = PmmGreen,
            modifier = Modifier.size(11.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.home_sync, timeStr),
            color = PmmTextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(2.dp)
                .clip(CircleShape)
                .background(PmmTextMuted)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.home_version),
            color = PmmTextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 10) 2026 MODERN FLOW COMPONENTS & MODIFIERS
// ═════════════════════════════════════════════════════════════════════════════

fun Modifier.bounceClick(
    enabled: Boolean = true,
    onClick: () -> Unit
) = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bounceScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

fun Modifier.dashedBorder(
    width: Dp,
    color: Color,
    cornerRadius: Dp
) = composed {
    this.drawWithContent {
        drawContent()
        val stroke = Stroke(
            width = width.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
        )
        drawRoundRect(
            color = color,
            style = stroke,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius.toPx())
        )
    }
}

@Composable
fun PlaceholderDottedCard(
    modifier: Modifier = Modifier
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val cardMinHeight = when {
        screenWidth >= 900 -> (screenHeight * 0.20f).dp.coerceIn(140.dp, 200.dp)
        screenWidth >= 600 -> (screenHeight * 0.16f).dp.coerceIn(120.dp, 170.dp)
        else -> 104.dp
    }
    val iconSize = if (screenWidth >= 600) 22.sp else 20.sp
    val labelSize = if (screenWidth >= 600) 12.sp else 11.sp

    Box(
        modifier = modifier
            .heightIn(min = cardMinHeight)
            .dashedBorder(width = 1.dp, color = PmmNavyBorder.copy(alpha = 0.7f), cornerRadius = 18.dp)
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "+",
                color = PmmTextMuted,
                fontSize = iconSize,
                fontWeight = FontWeight.Light
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_coming_soon_label),
                color = PmmTextMuted,
                fontSize = labelSize,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun HorizontalModuleCard(
    item: ModuleItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = stringResource(item.titleRes)
    val subtitle = stringResource(item.subtitleRes)
    val haptic = LocalHapticFeedback.current
    val isEnabled = item.enabled

    val screenWidth = LocalConfiguration.current.screenWidthDp
    val isTablet = screenWidth >= 600

    val cardHeight = if (isTablet) 110.dp else 88.dp
    val iconBoxSize = if (isTablet) 64.dp else 52.dp
    val iconSize = if (isTablet) 38.dp else 30.dp
    val titleSize = if (isTablet) 19.sp else 16.sp
    val subSize = if (isTablet) 13.sp else 11.sp
    val paddingH = if (isTablet) 20.dp else 14.dp

    Surface(
        modifier = modifier
            .height(cardHeight)
            .bounceClick(enabled = isEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .shadow(
                elevation = 1.dp,
                shape = RoundedCornerShape(18.dp),
                clip = false,
                ambientColor = Color(0xFF0F172A).copy(alpha = 0.04f),
                spotColor = Color(0xFF0F172A).copy(alpha = 0.06f)
            ),
        shape = RoundedCornerShape(18.dp),
        color = PmmBgSurface,
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            item.accentContainer.copy(alpha = 0.35f),
                            Color.White
                        )
                    )
                )
                .padding(horizontal = paddingH),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(iconBoxSize)
                    .clip(RoundedCornerShape(14.dp))
                    .background(item.accentContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = item.accent,
                    modifier = Modifier.size(iconSize)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = PmmTextPrimary,
                    fontSize = titleSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    color = PmmTextSecondary,
                    fontSize = subSize,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(PmmBgCard),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = PmmTextMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

enum class CasetaLayoutType {
    HorizontalCompact,
    HorizontalWithButton,
    VerticalLarge
}

@Composable
fun CasetaFeaturedCard(
    item: ModuleItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    layoutType: CasetaLayoutType = CasetaLayoutType.HorizontalCompact
) {
    val title = stringResource(item.titleRes)
    val subtitle = stringResource(item.subtitleRes)
    val haptic = LocalHapticFeedback.current
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val transition = rememberInfiniteTransition(label = "casetaGlow")
    val glowAlpha by transition.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = Motion.Standard),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    val pmmGradient = Brush.linearGradient(
        colors = listOf(
            PmmNavyDark,
            PmmBlueDeep,
            PmmBlueMid
        )
    )

    val surfaceModifier = when (layoutType) {
        CasetaLayoutType.HorizontalCompact -> modifier
        CasetaLayoutType.HorizontalWithButton -> modifier.height((screenHeight * 0.20f).dp.coerceIn(150.dp, 240.dp))
        CasetaLayoutType.VerticalLarge -> modifier.fillMaxSize()
    }

    Surface(
        modifier = surfaceModifier
            .bounceClick(enabled = item.enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(20.dp),
                clip = false,
                ambientColor = PmmBlueDeep.copy(alpha = 0.18f),
                spotColor = PmmBlueDeep.copy(alpha = 0.28f)
            ),
        shape = RoundedCornerShape(20.dp),
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
                        colors = listOf(PmmYellow.copy(alpha = glowAlpha), Color.Transparent),
                        radius = size.width * 0.55f
                    ),
                    center = Offset(size.width * 0.85f, size.height * 0.25f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF60A5FA).copy(alpha = 0.10f), Color.Transparent),
                        radius = size.width * 0.35f
                    ),
                    center = Offset(size.width * 0.95f, size.height * 0.75f)
                )
            }

            when (layoutType) {
                CasetaLayoutType.HorizontalCompact -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(PmmYellow),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = PmmBlueDeep,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.home_gate_label),
                                color = PmmYellow,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = subtitle,
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = PmmYellow,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                CasetaLayoutType.HorizontalWithButton -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(PmmYellow),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = PmmBlueDeep,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(Modifier.width(18.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.home_gate_security_label),
                                color = PmmYellow,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = subtitle,
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Surface(
                            color = Color.White.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.home_open),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = PmmYellow,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
                CasetaLayoutType.VerticalLarge -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(PmmYellow),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = PmmBlueDeep,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Column {
                            Text(
                                text = stringResource(R.string.home_gate_security_label),
                                color = PmmYellow,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.home_gate_description_full),
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(20.dp))
                            Surface(
                                color = Color.White.copy(alpha = 0.10f),
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.home_open_module),
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = PmmYellow,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 11) BENTO GRID 2026 ADAPTIVE SYSTEM
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun BentoHeroCard(
    item: ModuleItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLandscapeTablet: Boolean = false
) {
    val title = stringResource(item.titleRes)
    val subtitle = stringResource(item.subtitleRes)
    val haptic = LocalHapticFeedback.current
    val transition = rememberInfiniteTransition(label = "bentoHeroGlow")
    val glowAlpha by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = Motion.Standard),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heroGlow"
    )

    val pmmGradient = Brush.linearGradient(
        colors = listOf(
            PmmNavyDark,
            PmmBlueDeep,
            PmmBlueMid
        )
    )

    Surface(
        modifier = modifier
            .bounceClick(enabled = item.enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(22.dp),
                clip = false,
                ambientColor = PmmBlueDeep.copy(alpha = 0.22f),
                spotColor = PmmBlueDeep.copy(alpha = 0.32f)
            ),
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pmmGradient)
        ) {
            // Ambient Orbs
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(PmmYellow.copy(alpha = glowAlpha), Color.Transparent),
                        radius = size.width * 0.65f
                    ),
                    center = Offset(size.width * 0.88f, size.height * 0.22f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF60A5FA).copy(alpha = 0.14f), Color.Transparent),
                        radius = size.width * 0.45f
                    ),
                    center = Offset(size.width * 0.95f, size.height * 0.85f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isLandscapeTablet) 24.dp else 20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        color = PmmYellow.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, PmmYellow),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(PmmYellow)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(
                                    if (item.moduleId == MobileModuleIds.GATE) {
                                        R.string.home_gate_security_label
                                    } else {
                                        R.string.home_main_module_label
                                    }
                                ),
                                color = PmmYellow,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(if (isLandscapeTablet) 48.dp else 40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(if (isLandscapeTablet) 26.dp else 22.dp)
                        )
                    }
                }

                // Main Info
                Column(modifier = Modifier.padding(vertical = if (isLandscapeTablet) 12.dp else 6.dp)) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = if (isLandscapeTablet) 32.sp else 24.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.6).sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when (item.moduleId) {
                            MobileModuleIds.REVIEW -> stringResource(R.string.home_review_hero_description)
                            MobileModuleIds.GATE -> stringResource(R.string.home_gate_hero_description)
                            else -> stringResource(
                                R.string.home_operation_hero_description,
                                title
                            )
                        },
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = if (isLandscapeTablet) 14.sp else 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Action Bar
                Surface(
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.home_open_workflow),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = PmmYellow,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BentoSatelliteCard(
    item: ModuleItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = stringResource(item.titleRes)
    val subtitle = stringResource(item.subtitleRes)
    val haptic = LocalHapticFeedback.current
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val isTablet = screenWidth >= 600

    val cardMinHeight = if (isTablet) 130.dp else 100.dp
    val iconBoxSize = if (isTablet) 54.dp else 44.dp
    val iconSize = if (isTablet) 30.dp else 24.dp
    val titleSize = if (isTablet) 18.sp else 15.sp
    val subSize = if (isTablet) 12.sp else 11.sp
    val paddingH = if (isTablet) 18.dp else 14.dp
    val paddingV = if (isTablet) 16.dp else 12.dp

    Surface(
        modifier = modifier
            .heightIn(min = cardMinHeight)
            .bounceClick(enabled = item.enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(18.dp),
                clip = false,
                ambientColor = Color(0xFF0F172A).copy(alpha = 0.04f),
                spotColor = Color(0xFF0F172A).copy(alpha = 0.08f)
            ),
        shape = RoundedCornerShape(18.dp),
        color = PmmBgSurface,
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            item.accentContainer.copy(alpha = 0.40f),
                            Color.White
                        )
                    )
                )
                .padding(horizontal = paddingH, vertical = paddingV)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(iconBoxSize)
                        .clip(RoundedCornerShape(14.dp))
                        .background(item.accentContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = item.accent,
                        modifier = Modifier.size(iconSize)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = PmmTextPrimary,
                        fontSize = titleSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = PmmTextSecondary,
                        fontSize = subSize,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(PmmNavyVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = item.accent,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}
