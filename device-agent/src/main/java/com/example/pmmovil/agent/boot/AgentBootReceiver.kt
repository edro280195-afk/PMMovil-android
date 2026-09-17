package com.example.pmmovil.agent.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.pmmovil.agent.update.AgentWorkScheduler

class AgentBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> AgentWorkScheduler.enqueueNow(context)
        }
    }
}
