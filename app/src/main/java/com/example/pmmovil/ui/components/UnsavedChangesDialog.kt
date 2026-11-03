package com.example.pmmovil.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmRed
import com.example.pmmovil.ui.theme.PmmTextSecondary

/**
 * Pregunta antes de tirar lo capturado (al volver atrás o al empezar otra captura). «Seguir capturando» es el botón
 * principal, y también lo que hace tocar fuera o el gesto de atrás: descartar es la opción que hay que elegir a
 * propósito, porque no tiene vuelta. Lo usan Entrada de Bodega y Entrada a Caseta.
 *
 * @param discardLabel texto del botón que descarta («Salir sin guardar», «Empezar de nuevo»…).
 */
@Composable
fun UnsavedChangesDialog(
    title: String,
    message: String,
    discardLabel: String,
    onDiscard: () -> Unit,
    onStay: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onStay,
        title = { Text(title, fontWeight = FontWeight.Black) },
        text = { Text(message, color = PmmTextSecondary) },
        confirmButton = {
            Button(
                onClick = onStay,
                colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep)
            ) {
                Text("Seguir capturando", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) {
                Text(discardLabel, color = PmmRed, fontWeight = FontWeight.Bold)
            }
        }
    )
}
