package com.itantra.voice.feedback

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FeedbackRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var repository: FeedbackRepository

    @Before
    fun setUp() {
        filesDir = tempFolder.newFolder("feedback_test")
        repository = FeedbackRepository(filesDir)
    }

    @After
    fun tearDown() {
        tempFolder.delete()
    }

    @Test
    fun `count returns zero when no entries exist`() {
        assertEquals(0, repository.count())
    }

    @Test
    fun `save and readAll returns one positive entry`() {
        repository.save(
            sourceLanguage = "hi-IN",
            targetLanguage = "en-IN",
            sourceText = "मुझे स्टेशन जाना है",
            translatedText = "I want to go to the station.",
            isPositive = true
        )

        val entries = repository.readAll()
        assertEquals(1, entries.size)

        val entry = entries.first()
        assertEquals("hi-IN", entry.sourceLanguage)
        assertEquals("en-IN", entry.targetLanguage)
        assertEquals("मुझे स्टेशन जाना है", entry.sourceText)
        assertEquals("I want to go to the station.", entry.translatedText)
        assertEquals("positive", entry.feedback)
        assertTrue("timestamp should not be blank", entry.timestamp.isNotBlank())
    }

    @Test
    fun `save negative feedback stores correct feedback value`() {
        repository.save(
            sourceLanguage = "en-IN",
            targetLanguage = "mr-IN",
            sourceText = "Good morning",
            translatedText = "शुभ प्रभात",
            isPositive = false
        )

        val entries = repository.readAll()
        assertEquals(1, entries.size)
        assertEquals("negative", entries.first().feedback)
    }

    @Test
    fun `multiple saves accumulate entries in order`() {
        repeat(5) { i ->
            repository.save(
                sourceLanguage = "hi-IN",
                targetLanguage = "en-IN",
                sourceText = "source $i",
                translatedText = "translation $i",
                isPositive = i % 2 == 0
            )
        }

        val entries = repository.readAll()
        assertEquals(5, entries.size)
        assertEquals("source 0", entries[0].sourceText)
        assertEquals("source 4", entries[4].sourceText)
        assertEquals("positive", entries[0].feedback)
        assertEquals("negative", entries[1].feedback)
    }

    @Test
    fun `count matches number of saved entries`() {
        repository.save("ta-IN", "hi-IN", "hello", "नमस्ते", true)
        repository.save("kn-IN", "en-IN", "world", "world", false)
        assertEquals(2, repository.count())
    }

    @Test
    fun `readAll returns empty list if file does not exist`() {
        val freshRepo = FeedbackRepository(tempFolder.newFolder("empty"))
        assertEquals(emptyList<FeedbackEntry>(), freshRepo.readAll())
    }

    @Test
    fun `special characters are escaped and restored correctly`() {
        val sourceWithQuotes = "She said \"Hello\""
        val translWithBackslash = "उसने \\नमस्ते\\ कहा"
        repository.save("en-IN", "hi-IN", sourceWithQuotes, translWithBackslash, true)

        val entry = repository.readAll().first()
        assertEquals(sourceWithQuotes, entry.sourceText)
        assertEquals(translWithBackslash, entry.translatedText)
    }

    @Test
    fun `feedback file is named correctly`() {
        repository.save("en-IN", "ta-IN", "test", "சோதனை", true)
        val feedbackFile = File(filesDir, FeedbackRepository.FEEDBACK_FILE_NAME)
        assertTrue("Feedback file should exist after save", feedbackFile.exists())
        assertNotNull("Feedback file should contain JSON", feedbackFile.readText())
    }

    @Test
    fun `all 10 supported language codes are saved correctly`() {
        val languages = listOf("en-IN", "hi-IN", "bn-IN", "ta-IN", "te-IN",
            "kn-IN", "ml-IN", "mr-IN", "gu-IN", "od-IN")

        languages.forEach { lang ->
            repository.save(lang, "en-IN", "text", "translation", true)
        }

        val entries = repository.readAll()
        assertEquals(languages.size, entries.size)
        languages.forEachIndexed { i, lang ->
            assertEquals(lang, entries[i].sourceLanguage)
        }
    }
}
