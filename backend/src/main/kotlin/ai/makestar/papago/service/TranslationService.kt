package ai.makestar.papago.service

import ai.makestar.papago.domain.GlossaryRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
class TranslationService(
    private val glossaryRepository: GlossaryRepository,
    @Value("\${api.claude.key}") private val claudeApiKey: String,
    @Value("\${api.deepl.key}") private val deeplApiKey: String
) {
    private val webClient = WebClient.builder().build()

    fun translate(text: String, targetLang: String): TranslationResult {
        // 1. RAG: 메이크스타 용어집에서 관련 용어 검색 (최대 5개)
        val matchedTerms = glossaryRepository.findByKoContaining(text).take(10)
        
        val glossaryContext = if (matchedTerms.isNotEmpty()) {
            "Use the following Makestar-specific terminology if applicable:\n" +
            matchedTerms.joinToString("\n") { 
                "- Original: ${it.ko} -> Target: ${getTranslationByLang(it, targetLang)} (Context: ${it.pageUrl})"
            }
        } else ""

        // 2. Claude API 호출 (RAG 적용)
        val translatedText = callClaudeApi(text, targetLang, glossaryContext)

        return TranslationResult(
            originalText = text,
            translatedText = translatedText,
            targetLang = targetLang,
            contextUsed = glossaryContext
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
                    "model" to "claude-3-5-sonnet-20241022",
                    "max_tokens" to 1024,
                    "messages" to listOf(
                        mapOf("role" to "user", "content" to prompt)
                    )
                ))
                .retrieve()
                .bodyToMono(Map::class.java)
                .block()

            val content = response?.get("content") as? List<Map<String, Any>>
            content?.firstOrNull()?.get("text") as? String ?: "Translation Failed"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun getTranslationByLang(glossary: ai.makestar.papago.domain.Glossary, lang: String): String {
        return when (lang.lowercase()) {
            "en" -> glossary.en
            "ja" -> glossary.ja
            "zh-hans", "zh_hans" -> glossary.zhHans
            "zh-hant", "zh_hant" -> glossary.zhHant
            "es" -> glossary.es
            else -> glossary.en
        }
    }
}
