package ai.makestar.papago.service

import ai.makestar.papago.domain.ApprovedTranslationRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class ConsistencyService(
    private val approvedTranslationRepository: ApprovedTranslationRepository
) {

    fun checkConsistency(
        sourceText: String,
        translatedText: String,
        targetLang: String
    ): List<ConsistencyIssue> {
        val issues = mutableListOf<ConsistencyIssue>()

        val sourceWords = sourceText.split(Regex("\\s+")).filter { it.length >= 2 }
        if (sourceWords.isEmpty()) return issues

        val pageable = PageRequest.of(0, 100)
        val approvedTranslations = approvedTranslationRepository.findByTargetLangOrderByUsageCountDesc(targetLang, pageable)
        if (approvedTranslations.isEmpty()) return issues

        for (approved in approvedTranslations) {
            // Find similar source texts (overlap > 70%)
            val approvedWords = approved.sourceText.split(Regex("\\s+")).filter { it.length >= 2 }
            if (approvedWords.isEmpty()) continue

            val overlap = sourceWords.toSet().intersect(approvedWords.toSet()).size
            val similarity = overlap.toDouble() / maxOf(sourceWords.size, approvedWords.size)

            if (similarity >= 0.7 && approved.sourceText != sourceText) {
                val normalizedApproved = approved.approvedText.replace("\\s+".toRegex(), "").lowercase()
                val normalizedNew = translatedText.replace("\\s+".toRegex(), "").lowercase()

                if (normalizedApproved != normalizedNew) {
                    issues.add(
                        ConsistencyIssue(
                            existingSource = approved.sourceText,
                            existingTranslation = approved.approvedText,
                            newSource = sourceText,
                            newTranslation = translatedText,
                            similarity = similarity
                        )
                    )
                }
            }
        }

        return issues.sortedByDescending { it.similarity }.take(3)
    }
}

data class ConsistencyIssue(
    val existingSource: String,
    val existingTranslation: String,
    val newSource: String,
    val newTranslation: String,
    val similarity: Double
)
