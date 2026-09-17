package com.example.pmmovil.agent.update

import org.json.JSONObject

data class ReleaseManifest(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val mandatory: Boolean,
    val releaseNotes: String
) {

    companion object {
        fun fromJson(json: String): ReleaseManifest {
            val source = JSONObject(json)
            return ReleaseManifest(
                packageName = source.getString("packageName"),
                versionCode = source.getLong("versionCode"),
                versionName = source.getString("versionName"),
                apkUrl = source.getString("apkUrl"),
                sha256 = source.getString("sha256"),
                mandatory = source.optBoolean("mandatory", false),
                releaseNotes = source.optString("releaseNotes", "")
            )
        }
    }
}
