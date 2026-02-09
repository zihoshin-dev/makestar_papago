package ai.makestar.papago.service

import org.springframework.stereotype.Service

@Service
class TranslationVerificationService(
    private val glossarySearchService: GlossarySearchService,
    private val languageDetectionService: LanguageDetectionService
) {

    fun verify(
        originalText: String,
        translatedText: String,
        targetLang: String,
        matchedTerms: List<MatchedTerm>
    ): VerificationResult {
        val issues = mutableListOf<VerificationIssue>()

        // 1. Empty result check
        if (translatedText.isBlank()) {
            issues.add(VerificationIssue("EMPTY_RESULT", "Translation result is empty"))
            return VerificationResult(passed = false, issues = issues, shouldRetry = true)
        }

        // 2. Original text returned as-is
        val normalizedOriginal = originalText.replace("\\s+".toRegex(), "").lowercase()
        val normalizedTranslated = translatedText.replace("\\s+".toRegex(), "").lowercase()
        if (normalizedOriginal == normalizedTranslated && originalText.length > 3) {
            issues.add(VerificationIssue("ORIGINAL_RETURNED", "Translation is identical to original text"))
            return VerificationResult(passed = false, issues = issues, shouldRetry = true)
        }

        // 3. Language mismatch check
        val detectedLang = languageDetectionService.detectLanguage(translatedText)
        if (!isLanguageCompatible(detectedLang, targetLang)) {
            issues.add(
                VerificationIssue(
                    "LANGUAGE_MISMATCH",
                    "Expected $targetLang but detected $detectedLang in output"
                )
            )
        }

        // 4. Glossary term compliance check
        for (term in matchedTerms) {
            if (term.expectedTranslation.isNotBlank()) {
                val translatedLower = translatedText.lowercase()
                val expectedLower = term.expectedTranslation.lowercase()
                if (!translatedLower.contains(expectedLower)) {
                    issues.add(
                        VerificationIssue(
                            "GLOSSARY_MISMATCH",
                            "Expected \"${term.expectedTranslation}\" for \"${term.sourceKo}\" but not found in translation"
                        )
                    )
                }
            }
        }

        val passed = issues.none { it.type in BLOCKING_ISSUES }
        val shouldRetry = issues.any { it.type in RETRIABLE_ISSUES }

        return VerificationResult(passed = passed, issues = issues, shouldRetry = shouldRetry)
    }

    private fun isLanguageCompatible(detected: String, target: String): Boolean {
        val targetNorm = target.lowercase().split("-").first()
        return when {
            detected == targetNorm -> true
            detected == "zh" && targetNorm in listOf("zh", "zh_hans", "zh_hant") -> true
            targetNorm.startsWith("zh") && detected == "zh" -> true
            // Latin-script languages may be detected as "en" when short
            detected == "en" && targetNorm in listOf("en", "es", "de", "fr") -> true
            else -> false
        }
    }

    companion object {
        private val BLOCKING_ISSUES = setOf("EMPTY_RESULT", "ORIGINAL_RETURNED")
        private val RETRIABLE_ISSUES = setOf("EMPTY_RESULT", "ORIGINAL_RETURNED", "GLOSSARY_MISMATCH")
    }
}

data class MatchedTerm(
    val sourceKo: String,
    val expectedTranslation: String
)

data class VerificationIssue(
    val type: String,
    val message: String
)

data class VerificationResult(
    val passed: Boolean,
    val issues: List<VerificationIssue>,
    val shouldRetry: Boolean
)
