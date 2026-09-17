package com.example.pmmovil.agent.update

import android.content.Context
import java.util.UUID

class AgentIdentityStore(context: Context) {

    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    val deviceId: String
        get() = preferences.getString(KEY_DEVICE_ID, null)
            ?: UUID.randomUUID().toString().also { generatedId ->
                preferences.edit()
                    .putString(KEY_DEVICE_ID, generatedId)
                    .apply()
            }

    companion object {
        private const val FILE_NAME = "agent_identity"
        private const val KEY_DEVICE_ID = "device_id"
    }
}
