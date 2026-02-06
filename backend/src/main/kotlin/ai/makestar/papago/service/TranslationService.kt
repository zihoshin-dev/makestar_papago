package ai.makestar.papago.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
class TranslationService(
    private val glossarySearchService: GlossarySearchService,
    private val feedbackService: FeedbackService,
    @Value("\${api.claude.key}") private val claudeApiKey: String
) {
    private val webClient = WebClient.builder().build()

    fun translate(text: String, targetLang: String, pageUrl: String? = null): TranslationResult {
        // 0. Check approved translation cache first
        val approved = feedbackService.findApprovedTranslation(text, targetLang)
        if (approved != null) {
            val historyId = feedbackService.recordTranslation(
                sourceText = text,
                targetLang = targetLang,
                translatedText = approved.approvedText,
                matchedGlossaryCount = 0,
                isFromCache = true
            )
            return TranslationResult(
                originalText = text,
                translatedText = approved.approvedText,
                targetLang = targetLang,
                contextUsed = "Approved translation (used ${approved.usageCount} times)",
                historyId = historyId,
                isFromCache = true
            )
        }

        // 1. RAG: Score-based glossary search
        val scoredMatches = glossarySearchService.search(text, pageUrl)

        val glossaryContext = if (scoredMatches.isNotEmpty()) {
            "Use the following Makestar-specific terminology if applicable:\n" +
            scoredMatches.joinToString("\n") {
                val bestTranslation = glossarySearchService.getBestTranslation(it.glossary, targetLang)
                "- Original: ${it.glossary.ko} -> Target: $bestTranslation (Context: ${it.glossary.pageUrl}, Relevance: ${it.matchType})"
            }
        } else ""

        val matchedGlossaryIds = scoredMatches.mapNotNull { it.glossary.id }
        val matchScores = scoredMatches.map {
            MatchScore(
                glossaryId = it.glossary.id ?: 0L,
                ko = it.glossary.ko,
                score = it.score,
                matchType = it.matchType
            )
        }

        // 2. Call Claude API with RAG context
        val translatedText = callClaudeApi(text, targetLang, glossaryContext)

        // 3. Record translation history
        val historyId = feedbackService.recordTranslation(
            sourceText = text,
            targetLang = targetLang,
            translatedText = translatedText,
            matchedGlossaryCount = scoredMatches.size,
            isFromCache = false
        )

        return TranslationResult(
            originalText = text,
            translatedText = translatedText,
            targetLang = targetLang,
            contextUsed = glossaryContext,
            matchedGlossaryIds = matchedGlossaryIds,
            matchScores = matchScores,
            historyId = historyId
        )
    }

    private fun callClaudeApi(text: String, targetLang: String, context: String): String {
        val prompt = """
            You are a professional translator specializing in K-Pop fandom and commerce (Makestar).
            Translate the following Korean text to $targetLang.

            $context

            Instructions:
            1. Maintain the tone and style appropriate for K-Pop fans.
            2. If specific terminology is provided in the context, use it.
            3. Return ONLY the translated text without any explanation.

            Text to translate: $text
        """.trimIndent()

        return try {
            val response = webClient.post()
                .uri("https://api.anthropic.com/v1/messages")
                .header("x-api-key", claudeApiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .bodyValue(mapOf(
                    "model" to "claude-sonnet-4-20250514",
                    "max_tokens" to 4096,
                    "messages" to listOf(
                        mapOf("role" to "user", "content" to prompt)
                    )
                ))
                .retrieve()
                .bodyToMono(Map::class.java)
                .block()

            @Suppress("UNCHECKED_CAST")
            val content = response?.get("content") as? List<Map<String, Any>>
            content?.firstOrNull()?.get("text") as? String ?: "Translation Failed"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

data class MatchScore(
    val glossaryId: Long,
    val ko: String,
    val score: Double,
    val matchType: String
)

data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val targetLang: String,
    val contextUsed: String,
    val matchedGlossaryIds: List<Long> = emptyList(),
    val matchScores: List<MatchScore> = emptyList(),
    val historyId: Long? = null,
    val isFromCache: Boolean = false
)
