package com.example.pmmovil.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Suppress("DEPRECATION")
@Composable
fun PmmStatusBar(
    color: Color,
    darkIcons: Boolean
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context.findActivity()

    DisposableEffect(activity, view, color, darkIcons) {
        if (activity == null) {
            onDispose { }
        } else {
            val window = activity.window
            val previousColor = window.statusBarColor
            val controller = WindowCompat.getInsetsController(window, view)
            val previousDarkIcons = controller.isAppearanceLightStatusBars

            window.statusBarColor = color.toArgb()
            controller.isAppearanceLightStatusBars = darkIcons

            onDispose {
                window.statusBarColor = previousColor
                controller.isAppearanceLightStatusBars = previousDarkIcons
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
