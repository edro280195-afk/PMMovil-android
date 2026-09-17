package com.example.pmmovil.data.scanner

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable

data class DataWedgeProfileStatus(
    val configured: Boolean,
    val message: String,
    val result: String? = null,
    val details: String? = null
)

object DataWedgeProfileManager {
    const val ACTION_API = "com.symbol.datawedge.api.ACTION"
    const val ACTION_RESULT = "com.symbol.datawedge.api.RESULT_ACTION"
    const val PROFILE_NAME = "PMMovil Barcode"

    private const val EXTRA_SET_CONFIG = "com.symbol.datawedge.api.SET_CONFIG"
    private const val COMMAND_IDENTIFIER = "PMMOVIL_DATAWEDGE_SET_CONFIG"

    fun configure(context: Context) {
        val pluginConfigs = arrayListOf(
            barcodeInputConfig(),
            intentOutputConfig(context.packageName),
            keystrokeOutputConfig()
        )
        val appAssociation = Bundle().apply {
            putString("PACKAGE_NAME", context.packageName)
            putStringArray("ACTIVITY_LIST", arrayOf("*"))
        }
        val mainBundle = Bundle().apply {
            putString("PROFILE_NAME", PROFILE_NAME)
            putString("PROFILE_ENABLED", "true")
            putString("CONFIG_MODE", "CREATE_IF_NOT_EXIST")
            putParcelableArrayList("PLUGIN_CONFIG", pluginConfigs)
            putParcelableArray("APP_LIST", arrayOf<Parcelable>(appAssociation))
        }

        val intent = Intent(ACTION_API).apply {
            putExtra(EXTRA_SET_CONFIG, mainBundle)
            putExtra("SEND_RESULT", "COMPLETE_RESULT")
            putExtra("COMMAND_IDENTIFIER", COMMAND_IDENTIFIER)
        }
        context.sendBroadcast(intent)
    }

    fun isResultIntent(intent: Intent): Boolean =
        intent.action == ACTION_RESULT ||
            intent.hasExtra("COMMAND_IDENTIFIER") ||
            intent.hasExtra("RESULT_INFO")

    fun parseResult(intent: Intent?): DataWedgeProfileStatus? {
        if (intent == null || intent.action != ACTION_RESULT) return null
        val command = intent.getStringExtra("COMMAND_IDENTIFIER")
        if (command != null && command != COMMAND_IDENTIFIER) return null

        val result = intent.getStringExtra("RESULT")
        val details = intent.extras?.get("RESULT_INFO")?.toString()
        val success = result.equals("SUCCESS", ignoreCase = true)
        return DataWedgeProfileStatus(
            configured = success,
            message = if (success) {
                "Perfil DataWedge listo"
            } else {
                "DataWedge no aceptó la configuración"
            },
            result = result,
            details = details
        )
    }

    private fun barcodeInputConfig(): Bundle {
        val params = Bundle().apply {
            putString("scanner_selection", "auto")
            putString("scanner_input_enabled", "true")
        }
        return Bundle().apply {
            putString("PLUGIN_NAME", "BARCODE")
            putString("RESET_CONFIG", "false")
            putBundle("PARAM_LIST", params)
        }
    }

    private fun intentOutputConfig(packageName: String): Bundle {
        val componentInfo = Bundle().apply {
            putString("PACKAGE_NAME", packageName)
        }
        val params = Bundle().apply {
            putString("intent_output_enabled", "true")
            putString("intent_action", HardwareScannerIntents.ACTION_PMMOVIL_SCAN)
            putString("intent_category", Intent.CATEGORY_DEFAULT)
            putString("intent_delivery", "2")
            putParcelableArray(
                "intent_component_info",
                arrayOf<Parcelable>(componentInfo)
            )
        }
        return Bundle().apply {
            putString("PLUGIN_NAME", "INTENT")
            putString("RESET_CONFIG", "true")
            putBundle("PARAM_LIST", params)
        }
    }

    private fun keystrokeOutputConfig(): Bundle {
        val params = Bundle().apply {
            putString("keystroke_output_enabled", "false")
        }
        return Bundle().apply {
            putString("PLUGIN_NAME", "KEYSTROKE")
            putString("RESET_CONFIG", "false")
            putBundle("PARAM_LIST", params)
        }
    }
}
