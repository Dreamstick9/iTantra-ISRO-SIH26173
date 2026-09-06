package com.example.itantra.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

enum class LogLevel(val priority: Int, val shortCode: String) {
    DEBUG(3, "D"),
    INFO(4, "I"),
    WARN(5, "W"),
    ERROR(6, "E");

    fun isAtLeast(other: LogLevel): Boolean = this.priority >= other.priority
}

data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
) {
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun formatted(): String {
        val dateStr = timeFormat.format(Date(timestamp))
        val base = "[$dateStr] [${level.shortCode}] [$tag] $message"
        return if (throwable != null) {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            "$base\n${sw.toString().trimEnd()}"
        } else {
            base
        }
    }
}

class LogBuffer(val capacity: Int = 1000) {
    private val lock = ReentrantLock()
    private val buffer = ArrayDeque<LogEntry>(capacity)
    private var nextId = 1L

    fun append(level: LogLevel, tag: String, message: String, throwable: Throwable? = null): LogEntry {
        lock.withLock {
            if (buffer.size >= capacity) {
                buffer.removeFirst()
            }
            val entry = LogEntry(
                id = nextId++,
                timestamp = System.currentTimeMillis(),
                level = level,
                tag = tag,
                message = message,
                throwable = throwable
            )
            buffer.addLast(entry)
            return entry
        }
    }

    fun getEntries(): List<LogEntry> = lock.withLock { buffer.toList() }

    fun filter(
        minLevel: LogLevel? = null,
        tagFilter: String? = null,
        query: String? = null
    ): List<LogEntry> = lock.withLock {
        buffer.filter { entry ->
            val matchesLevel = minLevel == null || entry.level.isAtLeast(minLevel)
            val matchesTag = tagFilter.isNullOrBlank() || entry.tag.contains(tagFilter, ignoreCase = true)
            val matchesQuery = query.isNullOrBlank() || entry.message.contains(query, ignoreCase = true)
            matchesLevel && matchesTag && matchesQuery
        }
    }

    fun exportAsText(): String = lock.withLock {
        if (buffer.isEmpty()) return "--- EMPTY DIAGNOSTIC LOG BUFFER ---"
        val sb = StringBuilder()
        sb.appendLine("=== iTantra On-Device Diagnostics Log Dump ===")
        sb.appendLine("Total Entries: ${buffer.size} / $capacity")
        sb.appendLine("Dump Timestamp: ${System.currentTimeMillis()}")
        sb.appendLine("----------------------------------------------")
        buffer.forEach { sb.appendLine(it.formatted()) }
        sb.append("==============================================")
        sb.toString()
    }

    fun clear() = lock.withLock {
        buffer.clear()
        nextId = 1L
    }

    val size: Int get() = lock.withLock { buffer.size }
}

interface LogSink {
    fun log(entry: LogEntry)
}

class ConsoleLogSink : LogSink {
    override fun log(entry: LogEntry) {
        if (entry.level == LogLevel.ERROR) {
            System.err.println(entry.formatted())
        } else {
            println(entry.formatted())
        }
    }
}

object Logger {
    private const val DEFAULT_TAG_PREFIX = "iTantra:"
    private const val MAX_TAG_LENGTH = 23

    var minLevel: LogLevel = LogLevel.DEBUG
    var tagPrefix: String = DEFAULT_TAG_PREFIX
    var isEnabled: Boolean = true

    val buffer = LogBuffer(capacity = 1000)

    private val _logStream = MutableSharedFlow<LogEntry>(replay = 0, extraBufferCapacity = 64)
    val logStream: SharedFlow<LogEntry> = _logStream.asSharedFlow()

    private val sinks = mutableListOf<LogSink>(ConsoleLogSink())

    fun addSink(sink: LogSink) {
        synchronized(sinks) {
            if (!sinks.contains(sink)) sinks.add(sink)
        }
    }

    fun removeSink(sink: LogSink) {
        synchronized(sinks) { sinks.remove(sink) }
    }

    fun clearSinks() {
        synchronized(sinks) { sinks.clear() }
    }

    fun formatTag(tag: String?): String {
        val rawTag = tag?.takeIf { it.isNotBlank() } ?: "App"
        val fullTag = "$tagPrefix$rawTag"
        return if (fullTag.length > MAX_TAG_LENGTH) {
            fullTag.substring(0, MAX_TAG_LENGTH)
        } else {
            fullTag
        }
    }

    private fun logInternal(level: LogLevel, tag: String?, message: String, throwable: Throwable? = null) {
        if (!isEnabled || !level.isAtLeast(minLevel)) return

        val formattedTag = formatTag(tag)
        val entry = buffer.append(level, formattedTag, message, throwable)
        _logStream.tryEmit(entry)

        val activeSinks = synchronized(sinks) { sinks.toList() }
        for (sink in activeSinks) {
            sink.log(entry)
        }
    }

    fun d(tag: String? = null, message: String, throwable: Throwable? = null) =
        logInternal(LogLevel.DEBUG, tag, message, throwable)

    fun i(tag: String? = null, message: String, throwable: Throwable? = null) =
        logInternal(LogLevel.INFO, tag, message, throwable)

    fun w(tag: String? = null, message: String, throwable: Throwable? = null) =
        logInternal(LogLevel.WARN, tag, message, throwable)

    fun e(tag: String? = null, message: String, throwable: Throwable? = null) =
        logInternal(LogLevel.ERROR, tag, message, throwable)

    fun exportDiagnostics(): String = buffer.exportAsText()

    fun clearBuffer() = buffer.clear()
}
