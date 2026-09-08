package com.itantra.voice.feedback

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import java.util.logging.Logger
import kotlin.concurrent.withLock

/**
 * A single feedback entry recording user evaluation of one translation interaction.
 */
data class FeedbackEntry(
    val timestamp: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val sourceText: String,
    val translatedText: String,
    val feedback: String // "positive" or "negative"
)

/**
 * Local JSON feedback logger. Persists translation quality evaluations atomically
 * to a newline-delimited JSON file in the app's private files directory.
 *
 * File format: one JSON object per line (JSON Lines / NDJSON), e.g.:
 * {"timestamp":"2026-09-07T15:30:00+05:30","sourceLanguage":"hi-IN","targetLanguage":"en-IN",...}
 *
 * No cloud, no login, no account. Purely local.
 */
class FeedbackRepository(filesDir: File) {

    private val feedbackFile = File(filesDir, FEEDBACK_FILE_NAME)
    private val lock = ReentrantLock()
    // SimpleDateFormat is not thread-safe and was previously formatted outside the
    // lock, so concurrent feedback writes could corrupt each other's timestamps.
    private val dateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
    }

    /**
     * Saves a feedback entry atomically to the local JSON Lines file.
     *
     * @param sourceLanguage BCP-47 code of the source language (e.g. "hi-IN")
     * @param targetLanguage BCP-47 code of the target language (e.g. "en-IN")
     * @param sourceText Original transcribed text from STT
     * @param translatedText Translated text from Sarvam Translate
     * @param isPositive true for 👍, false for 👎
     */
    fun save(
        sourceLanguage: String,
        targetLanguage: String,
        sourceText: String,
        translatedText: String,
        isPositive: Boolean
    ) {
        val entry = buildJsonLine(
            timestamp = dateFormat.get().format(Date()),
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            sourceText = sourceText,
            translatedText = translatedText,
            feedback = if (isPositive) "positive" else "negative"
        )

        lock.withLock {
            try {
                feedbackFile.appendText(entry + "\n", Charsets.UTF_8)
                log.fine("Feedback saved: ${if (isPositive) "positive" else "negative"} | $sourceLanguage→$targetLanguage")
            } catch (e: Exception) {
                log.severe("Failed to write feedback entry: ${e.message}")
            }
        }
    }

    /**
     * Reads all stored feedback entries from disk. Returns an empty list on any error.
     */
    fun readAll(): List<FeedbackEntry> {
        if (!feedbackFile.exists()) return emptyList()

        return lock.withLock {
            try {
                feedbackFile.readLines(Charsets.UTF_8)
                    .filter { it.isNotBlank() }
                    .mapNotNull { line -> parseJsonLine(line) }
            } catch (e: Exception) {
                log.severe("Failed to read feedback file: ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * Returns the number of feedback entries stored on disk.
     */
    fun count(): Int = readAll().size

    // ─── Simple hand-rolled JSON serialization (no external deps) ─────────────

    private fun buildJsonLine(
        timestamp: String,
        sourceLanguage: String,
        targetLanguage: String,
        sourceText: String,
        translatedText: String,
        feedback: String
    ): String = buildString {
        append("{")
        append("\"timestamp\":${jsonString(timestamp)},")
        append("\"sourceLanguage\":${jsonString(sourceLanguage)},")
        append("\"targetLanguage\":${jsonString(targetLanguage)},")
        append("\"sourceText\":${jsonString(sourceText)},")
        append("\"translatedText\":${jsonString(translatedText)},")
        append("\"feedback\":${jsonString(feedback)}")
        append("}")
    }

    private fun jsonString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    private fun parseJsonLine(line: String): FeedbackEntry? {
        return try {
            fun extractField(json: String, key: String): String {
                val pattern = Regex("\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                return pattern.find(json)?.groupValues?.get(1)
                    ?.replace("\\\"", "\"")
                    ?.replace("\\\\", "\\")
                    ?.replace("\\n", "\n")
                    ?.replace("\\r", "\r")
                    ?.replace("\\t", "\t")
                    ?: ""
            }

            FeedbackEntry(
                timestamp = extractField(line, "timestamp"),
                sourceLanguage = extractField(line, "sourceLanguage"),
                targetLanguage = extractField(line, "targetLanguage"),
                sourceText = extractField(line, "sourceText"),
                translatedText = extractField(line, "translatedText"),
                feedback = extractField(line, "feedback")
            )
        } catch (e: Exception) {
            log.warning("Skipping malformed feedback line: $line")
            null
        }
    }

    companion object {
        private const val LOGGER_NAME = "FeedbackRepository"
        const val FEEDBACK_FILE_NAME = "itantra_feedback.jsonl"
    }

    private val log: Logger = Logger.getLogger(LOGGER_NAME)
}
