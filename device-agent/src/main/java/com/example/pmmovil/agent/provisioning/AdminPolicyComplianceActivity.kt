package com.example.pmmovil.agent.provisioning

import android.app.Activity
import android.os.Bundle
import com.example.pmmovil.agent.update.AgentStateStore
import com.example.pmmovil.agent.update.AgentWorkScheduler

class AdminPolicyComplianceActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AgentStateStore(this).recordResult("admin_policy_compliance")
        AgentWorkScheduler.enqueueNow(this)
        setResult(RESULT_OK)
        finish()
    }
}
