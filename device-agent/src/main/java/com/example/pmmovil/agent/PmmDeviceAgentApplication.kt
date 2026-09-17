package com.example.pmmovil.agent

import android.app.Application
import com.example.pmmovil.agent.update.AgentWorkScheduler

class PmmDeviceAgentApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AgentWorkScheduler.ensurePeriodicWork(this)
    }
}
