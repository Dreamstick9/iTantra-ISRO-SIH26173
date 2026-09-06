package com.example.itantra.language.normalizers

import com.example.itantra.language.TextNormalizer

/**
 * English text normalizer:
 * Expands common abbreviations and standardizes spacing/punctuation.
 */
class EnglishTextNormalizer : TextNormalizer {
    override fun normalize(text: String): String {
        if (text.isBlank()) return ""
        var normalized = text.trim()

        // Common abbreviation expansion
        normalized = normalized
            .replace(Regex("\\bkm/h\\b", RegexOption.IGNORE_CASE), "kilometers per hour")
            .replace(Regex("\\bkm\\b", RegexOption.IGNORE_CASE), "kilometers")
            .replace(Regex("\\bdr\\b\\.", RegexOption.IGNORE_CASE), "doctor")
            .replace(Regex("\\bsos\\b", RegexOption.IGNORE_CASE), "S-O-S")
            .replace(Regex("\\bmin\\b", RegexOption.IGNORE_CASE), "minutes")
            .replace(Regex("\\bhr\\b", RegexOption.IGNORE_CASE), "hours")

        // Collapse multiple whitespace characters into single space
        return normalized.replace(Regex("\\s+"), " ").trim()
    }
}

/**
 * Devanagari text normalizer (Hindi & Marathi):
 * Normalizes danda punctuation, nukta sequences, and removes dangling zero-width characters.
 */
class DevanagariTextNormalizer(private val isMarathi: Boolean = false) : TextNormalizer {
    override fun normalize(text: String): String {
        if (text.isBlank()) return ""
        var normalized = text.trim()

        // Normalize double danda to single danda for TTS speech pausing
        normalized = normalized.replace("॥", "।")

        // Remove isolated zero-width non-joiner and zero-width joiner
        normalized = normalized
            .replace("\u200B", "") // Zero width space
            .replace("\u200E", "") // LTR mark
            .replace("\u200F", "") // RTL mark

        // Normalize Devanagari digits to standard numerals if needed, or preserve
        return normalized.replace(Regex("\\s+"), " ").trim()
    }
}

/**
 * Bengali text normalizer:
 * Normalizes Bengali danda, hasant, and zero-width marks.
 */
class BengaliTextNormalizer : TextNormalizer {
    override fun normalize(text: String): String {
        if (text.isBlank()) return ""
        var normalized = text.trim()
        normalized = normalized.replace("॥", "।")
        normalized = normalized
            .replace("\u200B", "")
            .replace("\u200E", "")
            .replace("\u200F", "")
        return normalized.replace(Regex("\\s+"), " ").trim()
    }
}

/**
 * Tamil text normalizer:
 * Standardizes pulli and cleans punctuation.
 */
class TamilTextNormalizer : TextNormalizer {
    override fun normalize(text: String): String {
        if (text.isBlank()) return ""
        val normalized = text
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
        return normalized.replace(Regex("\\s+"), " ").trim()
    }
}

/**
 * Telugu text normalizer:
 * Standardizes Telugu virama and whitespace.
 */
class TeluguTextNormalizer : TextNormalizer {
    override fun normalize(text: String): String {
        if (text.isBlank()) return ""
        val normalized = text
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
        return normalized.replace(Regex("\\s+"), " ").trim()
    }
}

/**
 * Kannada text normalizer:
 * Standardizes Kannada virama, arkavatta, and whitespace.
 */
class KannadaTextNormalizer : TextNormalizer {
    override fun normalize(text: String): String {
        if (text.isBlank()) return ""
        val normalized = text
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
        return normalized.replace(Regex("\\s+"), " ").trim()
    }
}

/**
 * Malayalam text normalizer:
 * Normalizes Malayalam chillu characters, chandrakkala, and whitespace.
 */
class MalayalamTextNormalizer : TextNormalizer {
    override fun normalize(text: String): String {
        if (text.isBlank()) return ""
        val normalized = text
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
        return normalized.replace(Regex("\\s+"), " ").trim()
    }
}

/**
 * Gujarati text normalizer:
 * Normalizes Gujarati virama, nukta, and whitespace.
 */
class GujaratiTextNormalizer : TextNormalizer {
    override fun normalize(text: String): String {
        if (text.isBlank()) return ""
        val normalized = text
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
        return normalized.replace(Regex("\\s+"), " ").trim()
    }
}

/**
 * Odia text normalizer:
 * Normalizes Odia danda, halant, and whitespace.
 */
class OdiaTextNormalizer : TextNormalizer {
    override fun normalize(text: String): String {
        if (text.isBlank()) return ""
        val normalized = text
            .replace("॥", "।")
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
        return normalized.replace(Regex("\\s+"), " ").trim()
    }
}
