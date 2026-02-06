package ai.makestar.papago.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
class TranslationService(
    private val glossarySearchService: GlossarySearchService,
    private val feedbackService: FeedbackService,
    private val inputValidationService: InputValidationService,
    private val contextDetectionService: ContextDetectionService,
    private val koreanSlangDictionaryService: KoreanSlangDictionaryService,
    private val toneDetectorService: ToneDetectorService,
    @Value("\${api.claude.key}") private val claudeApiKey: String
) {
    private val webClient = WebClient.builder().build()

    companion object {
        private val LANGUAGE_NAMES = mapOf(
            "en" to "English",
            "ja" to "Japanese",
            "zh-hans" to "Simplified Chinese",
            "zh-hant" to "Traditional Chinese",
            "es" to "Spanish",
            "de" to "German",
            "fr" to "French"
        )

        private const val UNTRANSLATABLE_MARKER = "[UNTRANSLATABLE]"
        private const val GLOSSARY_MIN_SCORE = 20.0
    }

    fun translate(text: String, targetLang: String, pageUrl: String? = null): TranslationResult {
        // 0. Input validation
        val validation = inputValidationService.validate(text)
        if (!validation.isValid) {
            val errorMessage = inputValidationService.getErrorMessage(validation.reason ?: "UNKNOWN")
            return TranslationResult(
                originalText = text,
                translatedText = errorMessage,
                targetLang = targetLang,
                contextUsed = "",
                isValidationError = true,
                translationStrategy = "VALIDATION_ERROR",
                detectedTone = null
            )
        }

        // 1. Check approved translation cache first
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
                isFromCache = true,
                translationStrategy = "APPROVED_CACHE",
                detectedTone = null
            )
        }

        // Detect tone for all strategies
        val tone = toneDetectorService.detectTone(text)
        val detectedTone = tone.name

        // STRATEGY A: SLANG_DECODE - Korean jamo-only slang lookup
        if (koreanSlangDictionaryService.isJamoOnly(text)) {
            val slangResult = koreanSlangDictionaryService.lookup(text, targetLang)
            if (slangResult != null) {
                val historyId = feedbackService.recordTranslation(
                    sourceText = text,
                    targetLang = targetLang,
                    translatedText = slangResult.translatedText,
                    matchedGlossaryCount = 0,
                    isFromCache = false
                )
                return TranslationResult(
                    originalText = text,
                    translatedText = slangResult.translatedText,
                    targetLang = targetLang,
                    contextUsed = "Korean slang dictionary lookup",
                    historyId = historyId,
                    isFromCache = false,
                    translationStrategy = "SLANG_DECODE",
                    detectedTone = detectedTone
                )
            }
        }

        // 2. RAG: Score-based glossary search with relevance threshold
        val detectedPageUrl = pageUrl ?: contextDetectionService.detectContext(text)
        val scoredMatches = glossarySearchService.search(text, detectedPageUrl)
            .filter { it.score >= GLOSSARY_MIN_SCORE }

        // STRATEGY B: GLOSSARY_DIRECT - Single exact match with target translation
        if (scoredMatches.size == 1 && scoredMatches.first().score == 100.0) {
            val exactMatch = scoredMatches.first().glossary
            val directTranslation = glossarySearchService.getBestTranslation(exactMatch, targetLang)

            // Only use direct if translation is meaningful (not just falling back to English)
            val hasTargetTranslation = when (targetLang.lowercase()) {
                "en" -> exactMatch.en.isNotBlank()
                "ja" -> exactMatch.ja.isNotBlank()
                "zh-hans", "zh_hans" -> exactMatch.zhHans.isNotBlank()
                "zh-hant", "zh_hant" -> exactMatch.zhHant.isNotBlank()
                "es" -> exactMatch.es.isNotBlank()
                else -> false
            }

            if (hasTargetTranslation) {
                val historyId = feedbackService.recordTranslation(
                    sourceText = text,
                    targetLang = targetLang,
                    translatedText = directTranslation,
                    matchedGlossaryCount = 1,
                    isFromCache = false
                )
                return TranslationResult(
                    originalText = text,
                    translatedText = directTranslation,
                    targetLang = targetLang,
                    contextUsed = "Exact glossary match: ${exactMatch.ko} -> $directTranslation",
                    matchedGlossaryIds = listOfNotNull(exactMatch.id),
                    matchScores = listOf(
                        MatchScore(
                            glossaryId = exactMatch.id ?: 0L,
                            ko = exactMatch.ko,
                            score = 100.0,
                            matchType = scoredMatches.first().matchType
                        )
                    ),
                    historyId = historyId,
                    isFromCache = false,
                    translationStrategy = "GLOSSARY_DIRECT",
                    detectedTone = detectedTone
                )
            }
        }

        val glossaryContext = if (scoredMatches.isNotEmpty()) {
            "Use the following Makestar-specific terminology if applicable:\n" +
            scoredMatches.joinToString("\n") {
                val bestTranslation = glossarySearchService.getBestTranslation(it.glossary, targetLang)
                "- \"${it.glossary.ko}\" -> \"$bestTranslation\" (Relevance: ${it.matchType}, Score: ${it.score.toInt()})"
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

        // 3. Generate tone hint for Claude
        val toneHint = when (detectedTone) {
            "FORMAL" -> "\n8. The input text tone is FORMAL. Maintain similar formality in the translation."
            "CASUAL" -> "\n8. The input text tone is CASUAL. Use casual/informal language in the translation."
            else -> null
        }

        // STRATEGY C: RAG_ASSISTED - Glossary context + Claude API
        // STRATEGY D: LLM_PRIMARY - No glossary matches, Claude API only
        val langName = LANGUAGE_NAMES[targetLang.lowercase()] ?: targetLang
        val rawTranslation = callClaudeApi(text, langName, glossaryContext, toneHint)

        // 4. Post-processing validation
        val translatedText = postProcessTranslation(rawTranslation, text, langName)

        // 5. Record translation history
        val historyId = feedbackService.recordTranslation(
            sourceText = text,
            targetLang = targetLang,
            translatedText = translatedText,
            matchedGlossaryCount = scoredMatches.size,
            isFromCache = false
        )

        val strategy = if (scoredMatches.isNotEmpty()) "RAG_ASSISTED" else "LLM_PRIMARY"

        return TranslationResult(
            originalText = text,
            translatedText = translatedText,
            targetLang = targetLang,
            contextUsed = glossaryContext.ifBlank { "No glossary matches (LLM-only translation)" },
            matchedGlossaryIds = matchedGlossaryIds,
            matchScores = matchScores,
            historyId = historyId,
            translationStrategy = strategy,
            detectedTone = detectedTone
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun postProcessTranslation(rawTranslation: String, originalText: String, targetLang: String): String {
        var result = rawTranslation.trim()

        // Remove wrapping quotes if present
        if ((result.startsWith("\"") && result.endsWith("\"")) ||
            (result.startsWith("'") && result.endsWith("'"))) {
            result = result.substring(1, result.length - 1).trim()
        }

        // Handle [UNTRANSLATABLE] marker (but not for Korean jamo input - those are K-Pop slang)
        val isKoreanJamo = originalText.trim().all { it.isWhitespace() || it in '\u3131'..'\u318E' }
        if (result.contains(UNTRANSLATABLE_MARKER, ignoreCase = true) && !isKoreanJamo) {
            return "번역할 수 없는 텍스트예요."
        }
        // If Claude returned UNTRANSLATABLE for jamo, provide a fallback
        if (result.contains(UNTRANSLATABLE_MARKER, ignoreCase = true) && isKoreanJamo) {
            return originalText.trim()
        }

        // Handle empty or error responses
        if (result.isBlank() || result == "Translation Failed") {
            return "번역에 실패했어요. 다시 시도해 주세요."
        }

        // Detect when translation equals original (Claude returned input as-is)
        // But allow short Korean consonant/vowel expressions (K-Pop internet slang)
        val normalizedResult = result.replace("\\s+".toRegex(), "").lowercase()
        val normalizedOriginal = originalText.replace("\\s+".toRegex(), "").lowercase()
        if (normalizedResult == normalizedOriginal && originalText.length > 1 && !isKoreanJamo) {
            return "번역할 수 없는 텍스트예요."
        }

        return result
    }

    private fun callClaudeApi(
        text: String,
        targetLangName: String,
        context: String,
        toneHint: String? = null
    ): String {
        val systemPrompt = buildString {
            append("""You are a professional Korean-to-$targetLangName translator specializing in K-Pop fandom and e-commerce (Makestar platform).

Rules:
1. Translate the Korean text accurately into natural $targetLangName.
2. If Makestar-specific terminology is provided, use those exact translations.
3. Maintain the tone and nuance appropriate for K-Pop fans.
4. Return ONLY the translated text. No explanations, labels, or quotes.
5. Korean internet slang using consonants/vowels MUST be translated naturally:
   - ㅋㅋ / ㅋㅋㅋ = laughter (hahaha, lol)
   - ㅠㅠ / ㅜㅜ = crying/sadness (so sad, *cries*)
   - ㅇㅇ / ㅇㅇㅇ = agreement/acknowledgment (yeah, yes, yep)
   - ㅎㅎ = soft laughter (hehe)
   - ㄱㅅ = thanks (short for 감사)
   - ㄴㄴ = no no (short for 노노)
   - ㅇㅋ = ok (short for 오케이)
   - ㄷㄷ = trembling/shocked (wow, omg)
   - ㅂㅂ = bye bye
   - ㅁㅊ = crazy (abbreviation)
   IMPORTANT: More repeated characters = more emphasis (e.g., ㅋㅋㅋㅋㅋ = lots of laughter, ㅠㅠㅠㅠ = very sad, ㅇㅇㅇ = emphatic yes). ANY input made entirely of Korean consonants or vowels is K-Pop fan slang and MUST be translated. NEVER mark them as untranslatable.
6. If the input is truly meaningless random NON-KOREAN characters, respond with exactly: $UNTRANSLATABLE_MARKER
7. Never return the original Korean text as the translation.""".trimIndent())

            if (toneHint != null) {
                append(toneHint)
            }
        }

        val userMessage = buildString {
            if (context.isNotBlank()) {
                appendLine(context)
                appendLine()
            }
            append("Translate to $targetLangName: $text")
        }

        return try {
            val response = webClient.post()
                .uri("https://api.anthropic.com/v1/messages")
                .header("x-api-key", claudeApiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .bodyValue(mapOf(
                    "model" to "claude-sonnet-4-20250514",
                    "max_tokens" to 4096,
                    "system" to systemPrompt,
                    "messages" to listOf(
                        mapOf("role" to "user", "content" to userMessage)
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
    val isFromCache: Boolean = false,
    val isValidationError: Boolean = false,
    val translationStrategy: String = "RAG_ASSISTED",
    val detectedTone: String? = null
)
