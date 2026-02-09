package ai.makestar.papago.service

import ai.makestar.papago.domain.ApprovedTranslation
import ai.makestar.papago.domain.ApprovedTranslationRepository
import org.springframework.stereotype.Service

@Service
class TranslationExampleService(
    private val approvedTranslationRepository: ApprovedTranslationRepository
) {

    fun findSimilarExamples(sourceText: String, targetLang: String, limit: Int = 5): List<TranslationExample> {
        val candidates = approvedTranslationRepository.findByTargetLangOrderByUsageCountDesc(targetLang)
        if (candidates.isEmpty()) return emptyList()

        val inputWords = sourceText.split(Regex("\\s+")).filter { it.length >= 2 }.toSet()
        if (inputWords.isEmpty()) return candidates.take(limit).map { it.toExample() }

        return candidates
            .map { candidate ->
                val candidateWords = candidate.sourceText.split(Regex("\\s+")).filter { it.length >= 2 }.toSet()
                val overlap = inputWords.intersect(candidateWords).size
                val score = if (candidateWords.isNotEmpty()) {
                    overlap.toDouble() / candidateWords.size
                } else 0.0
                candidate to score
            }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first.toExample() }
    }

    fun formatExamplesForPrompt(examples: List<TranslationExample>): String {
        if (examples.isEmpty()) return ""
        return buildString {
            appendLine("Here are approved translation examples for reference:")
            for (example in examples) {
                append("- \"${example.sourceText}\" -> \"${example.approvedText}\"")
                if (example.pageUrl.isNotBlank()) {
                    append(" (context: ${example.pageUrl})")
                }
                appendLine()
            }
        }
    }

    private fun ApprovedTranslation.toExample() = TranslationExample(
        sourceText = sourceText,
        approvedText = approvedText,
        pageUrl = ""
    )
}

data class TranslationExample(
    val sourceText: String,
    val approvedText: String,
    val pageUrl: String
)
