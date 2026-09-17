package com.example.pmmovil.agent.update

import android.content.Context
import androidx.core.content.edit
import java.text.DateFormat
import java.util.Date

class AgentStateStore(context: Context) {

    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    val lastResult: String
        get() = preferences.getString(KEY_LAST_RESULT, "sin ejecuciones") ?: "sin ejecuciones"

    val lastError: String?
        get() = preferences.getString(KEY_LAST_ERROR, null)

    val installationInProgressVersionCode: Long?
        get() = if (preferences.contains(KEY_INSTALLATION_IN_PROGRESS_VERSION_CODE)) {
            preferences.getLong(KEY_INSTALLATION_IN_PROGRESS_VERSION_CODE, 0L)
        } else {
            null
        }

    fun recordCheckStarted() {
        preferences.edit {
            putLong(KEY_LAST_CHECK, System.currentTimeMillis())
            .putString(KEY_LAST_RESULT, "revisando")
            .remove(KEY_LAST_ERROR)
        }
    }

    fun recordResult(result: String) {
        preferences.edit {
            putLong(KEY_LAST_CHECK, System.currentTimeMillis())
            .putString(KEY_LAST_RESULT, result)
            .remove(KEY_LAST_ERROR)
        }
    }

    fun recordInstallationRequested(versionCode: Long) {
        preferences.edit {
            putLong(KEY_INSTALLATION_IN_PROGRESS_VERSION_CODE, versionCode)
            .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
            .putString(KEY_LAST_RESULT, "instalación solicitada")
            .remove(KEY_LAST_ERROR)
        }
    }

    fun clearInstallationInProgress() {
        preferences.edit {
            remove(KEY_INSTALLATION_IN_PROGRESS_VERSION_CODE)
        }
    }

    fun recordError(message: String) {
        preferences.edit {
            putLong(KEY_LAST_CHECK, System.currentTimeMillis())
            .putString(KEY_LAST_RESULT, "error")
            .putString(KEY_LAST_ERROR, message.take(MAX_ERROR_LENGTH))
        }
    }

    fun lastCheckLabel(): String {
        val timestamp = preferences.getLong(KEY_LAST_CHECK, 0L)
        if (timestamp == 0L) return "nunca"
        return DateFormat.getDateTimeInstance().format(Date(timestamp))
    }

    companion object {
        private const val FILE_NAME = "agent_state"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_LAST_RESULT = "last_result"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_INSTALLATION_IN_PROGRESS_VERSION_CODE = "installation_in_progress_version_code"
        private const val MAX_ERROR_LENGTH = 500
    }
}
