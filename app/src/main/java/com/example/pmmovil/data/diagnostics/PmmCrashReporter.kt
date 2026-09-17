package com.example.pmmovil.data.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.pmmovil.BuildConfig
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PmmCrashReporter {
    private const val SENTRY_DSN_META = "io.sentry.dsn"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val dsn = readSentryDsn(appContext)
        if (dsn.isNotBlank()) {
            SentryAndroid.init(appContext) { options ->
                options.dsn = dsn
                options.environment = if (BuildConfig.DEBUG) "debug" else "release"
                options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
                options.isDebug = BuildConfig.DEBUG
                options.isSendDefaultPii = false
                options.tracesSampleRate = 0.05
            }
        }

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            persistCrash(appContext, thread, throwable)
            if (dsn.isNotBlank()) {
                Sentry.captureException(throwable)
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    fun crashDirectory(context: Context): File =
        File(context.filesDir, "crash_reports").apply { mkdirs() }

    private fun persistCrash(
        context: Context,
        thread: Thread,
        throwable: Throwable
    ) {
        runCatching {
            val timestamp = SimpleDateFormat(
                "yyyyMMdd_HHmmss_SSS",
                Locale.US
            ).format(Date())
            val stackTrace = StringWriter().use { writer ->
                PrintWriter(writer).use { printer ->
                    throwable.printStackTrace(printer)
                }
                writer.toString()
            }
            val report = buildString {
                appendLine("version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("buildType=${BuildConfig.BUILD_TYPE}")
                appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
                appendLine("thread=${thread.name}")
                appendLine("error=${throwable::class.java.name}: ${throwable.message.orEmpty()}")
                appendLine()
                append(stackTrace)
            }
            crashDirectory(context)
                .resolve("crash_$timestamp.txt")
                .writeText(report)
        }
    }

    private fun readSentryDsn(context: Context): String {
        return runCatching {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            appInfo.metaData?.getString(SENTRY_DSN_META).orEmpty()
        }.getOrDefault("")
    }
}
