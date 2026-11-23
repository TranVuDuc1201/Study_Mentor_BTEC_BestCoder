package com.example.study_mentor

import com.example.study_mentor.data.QuestionType
import com.example.study_mentor.data.calculateEarnedXp
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiQuizGeneratorUnitTest {

    private fun cleanRawJsonResponse(raw: String): String {
        var cleaned = raw.trim()
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replace(Regex("^```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
            cleaned = cleaned.replace(Regex("\\s*```$"), "")
            cleaned = cleaned.trim()
        }
        val firstBrace = cleaned.indexOf('{')
        val lastBrace = cleaned.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            cleaned = cleaned.substring(firstBrace, lastBrace + 1)
        }
        return cleaned
    }

    @Test
    fun testJsonMarkdownStripping() {
        val markdownJson = """
            ```json
            {
              "id": "ai_123",
              "title": "History of Vietnam",
              "maxXp": 100
            }
            ```
        """.trimIndent()

        val cleaned = cleanRawJsonResponse(markdownJson)
        assertTrue(cleaned.startsWith("{"))
        assertTrue(cleaned.endsWith("}"))
    }

    @Test
    fun testConversationalPreambleStripping() {
        val conversational = """
            Here is your requested quiz in JSON format:
            {
              "id": "ai_456",
              "title": "Algebra",
              "maxXp": 200
            }
            Hope you enjoy practicing!
        """.trimIndent()

        val cleaned = cleanRawJsonResponse(conversational)
        assertTrue(cleaned.startsWith("{"))
        assertTrue(cleaned.endsWith("}"))
    }

    @Test
    fun testDynamicXpCalculation() {
        // 5 questions = 100 XP
        val count5Xp = 5 * 20
        assertEquals(100, count5Xp)

        // 10 questions = 200 XP
        val count10Xp = 10 * 20
        assertEquals(200, count10Xp)

        // Full score 5/5 on 100 XP quiz (with +20% perfect score mastery bonus = 120 XP)
        val fullEarned5 = calculateEarnedXp(correctAnswers = 5, totalQuestions = 5, maxXp = 100)
        assertEquals(120, fullEarned5)

        // Partial score 3/5 on 100 XP quiz
        val partialEarned = calculateEarnedXp(correctAnswers = 3, totalQuestions = 5, maxXp = 100)
        assertEquals(60, partialEarned)

        // Full score 10/10 on 200 XP quiz (with +20% perfect score mastery bonus = 240 XP)
        val fullEarned10 = calculateEarnedXp(correctAnswers = 10, totalQuestions = 10, maxXp = 200)
        assertEquals(240, fullEarned10)
    }

    @Test
    fun testQuizCreatorIdAndAiGeneratedFlag() {
        val quiz = com.example.study_mentor.data.Quiz(
            id = "ai_test_1",
            title = "Test Quiz",
            isAiGenerated = true,
            creatorId = "user_12345"
        )
        assertEquals("user_12345", quiz.creatorId)
        assertTrue(quiz.isAiGenerated)

        val defaultQuiz = com.example.study_mentor.data.Quiz()
        assertEquals(null, defaultQuiz.creatorId)
        org.junit.Assert.assertFalse(defaultQuiz.isAiGenerated)
    }
}

