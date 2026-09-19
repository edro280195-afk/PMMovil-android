package com.example.pmmovil.ui.localization

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Texto que puede cambiar de idioma mientras una ViewModel sigue viva.
 * Los mensajes que vienen del servidor se conservan como [Raw] porque no
 * deben traducirse ni alterarse automáticamente.
 */
sealed interface UiText {
    data class Resource(
        @StringRes val id: Int,
        val args: List<Any> = emptyList()
    ) : UiText

    data class Raw(val value: String) : UiText
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Resource -> stringResource(id, *args.toTypedArray())
    is UiText.Raw -> value
}

fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Resource -> context.getString(id, *args.toTypedArray())
    is UiText.Raw -> value
}
