package com.example.pmmovil.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.ui.theme.*

/**
 * Tipos de toast semánticos.
 * El texto del mensaje puede llevar un prefijo especial para determinar el tipo:
 *   "✓ …"  → SUCCESS   (verde)
 *   "⚠ …"  → WARNING   (ámbar)
 *   "✗ …"  → ERROR     (rojo)
 *   sin prefijo o "ℹ …" → INFO  (azul PMM)
 */
private enum class ToastType {
    SUCCESS, WARNING, ERROR, INFO
}

private fun String.toToastType(): ToastType = when {
    startsWith("✓") || startsWith("✅") -> ToastType.SUCCESS
    startsWith("⚠") || startsWith("⚠️") -> ToastType.WARNING
    startsWith("✗") || startsWith("❌") || startsWith("Error") || startsWith("error") -> ToastType.ERROR
    else -> ToastType.INFO
}

private val ToastType.bgColor: Color
    get() = when (this) {
        ToastType.SUCCESS -> PmmGreen
        ToastType.WARNING -> PmmAmber
        ToastType.ERROR   -> PmmRed
        ToastType.INFO    -> PmmNavyDark
    }

private val ToastType.icon: ImageVector
    get() = when (this) {
        ToastType.SUCCESS -> Icons.Default.CheckCircle
        ToastType.WARNING -> Icons.Default.Warning
        ToastType.ERROR   -> Icons.Default.Cancel
        ToastType.INFO    -> Icons.Default.Info
    }

@Composable
fun PmmSnackbarHost(hostState: SnackbarHostState) {
    // imePadding(): sin esto, el toast queda tapado detrás del teclado cuando
    // se dispara mientras el usuario todavía tiene un campo enfocado (la app
    // corre en modo edge-to-edge, así que el inset del teclado no se aplica solo).
    SnackbarHost(
        hostState = hostState,
        modifier  = Modifier.imePadding()
    ) { data ->
        val type  = data.visuals.message.toToastType()
        val text  = data.visuals.message.trimStart('✓', '⚠', '✗', 'ℹ', '✅', '❌', ' ')

        Snackbar(
            modifier        = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            containerColor  = type.bgColor,
            contentColor    = Color.White,
            shape           = RoundedCornerShape(16.dp),
            dismissAction   = data.visuals.actionLabel?.let {
                {
                    TextButton(onClick = { data.performAction() }) {
                        Text(it, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector        = type.icon,
                    contentDescription = null,
                    modifier           = Modifier.size(20.dp),
                    tint               = Color.White
                )
                Text(
                    text       = text.ifBlank { data.visuals.message },
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 14.sp,
                    color      = Color.White
                )
            }
        }
    }
}
