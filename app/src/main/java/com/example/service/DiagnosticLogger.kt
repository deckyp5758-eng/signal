package com.example.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.example.config.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel(val badge: String) {
    INFO("INFO"),
    WARN("WARN"),
    ERROR("ERR"),
    NETWORK("NET")
}

data class DiagnosticLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
}

object DiagnosticLogger {
    private val _logs = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    val logs: StateFlow<List<DiagnosticLogEntry>> = _logs.asStateFlow()

    private val logBuffer = mutableListOf<DiagnosticLogEntry>()

    fun log(level: LogLevel, tag: String, message: String) {
        synchronized(logBuffer) {
            val entry = DiagnosticLogEntry(level = level, tag = tag, message = message)
            logBuffer.add(0, entry)
            if (logBuffer.size > AppConfig.MAX_DIAGNOSTIC_LOGS) {
                logBuffer.removeAt(logBuffer.size - 1)
            }
            _logs.value = ArrayList(logBuffer)
        }
    }

    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String) = log(LogLevel.WARN, tag, message)
    fun e(tag: String, message: String) = log(LogLevel.ERROR, tag, message)
    fun net(tag: String, message: String) = log(LogLevel.NETWORK, tag, message)

    fun clearLogs() {
        synchronized(logBuffer) {
            logBuffer.clear()
            _logs.value = emptyList()
        }
    }

    fun generateReportText(): String {
        val sb = StringBuilder()
        sb.appendLine("=== SCALPSIGNAL PRO DIAGNOSTIC REPORT ===")
        sb.appendLine("App Version: ${AppConfig.APP_VERSION} (${AppConfig.BUILD_NUMBER})")
        sb.appendLine("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        sb.appendLine("Total Logs Recorded: ${logBuffer.size}")
        sb.appendLine("----------------------------------------")
        synchronized(logBuffer) {
            logBuffer.forEach { log ->
                sb.appendLine("[${log.formattedTime}] [${log.level.badge}] [${log.tag}] ${log.message}")
            }
        }
        sb.appendLine("========================================")
        return sb.toString()
    }

    fun copyLogsToClipboard(context: Context) {
        val report = generateReportText()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Diagnostic Report", report)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "✅ Laporan Diagnostik tersalin ke Clipboard!", Toast.LENGTH_SHORT).show()
    }
}
