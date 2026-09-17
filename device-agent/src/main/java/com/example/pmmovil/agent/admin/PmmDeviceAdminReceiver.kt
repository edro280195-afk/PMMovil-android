package com.example.pmmovil.agent.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.example.pmmovil.agent.update.AgentStateStore
import com.example.pmmovil.agent.update.AgentWorkScheduler

class PmmDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        AgentStateStore(context).recordResult("device_admin_enabled")
        AgentWorkScheduler.enqueueNow(context)
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        AgentStateStore(context).recordResult("provisioning_complete")
        AgentWorkScheduler.enqueueNow(context)
    }
}
