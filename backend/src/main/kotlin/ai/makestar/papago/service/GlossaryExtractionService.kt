package ai.makestar.papago.service

import ai.makestar.papago.domain.CandidateGlossaryEntry
import ai.makestar.papago.domain.CandidateGlossaryEntryRepository
import ai.makestar.papago.domain.GlossaryRepository
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class GlossaryExtractionService(
    private val candidateRepository: CandidateGlossaryEntryRepository,
    private val glossaryRepository: GlossaryRepository,
    private val tokenizationService: TokenizationService
) {

    @Async
    fun extractCandidates(
        sourceText: String,
        translatedText: String,
        targetLang: String,
        pageUrl: String?
    ) {
        val words = tokenizationService.splitIntoWords(sourceText)
        if (words.isEmpty()) return

        // Get existing glossary ko terms for quick lookup
        val existingTerms = glossaryRepository.findAll().map { it.ko.lowercase() }.toSet()

        for (word in words) {
            if (word.length < 2) continue
            if (word.lowercase() in existingTerms) continue
            if (isParticleOrSuffix(word)) continue

            val confidenceScore = calculateConfidence(word, sourceText, pageUrl)
            if (confidenceScore < 0.3) continue

            // Check if candidate already exists
            val existing = candidateRepository.findBySourceKoAndTargetLang(word, targetLang)
            if (existing != null) {
                existing.occurrenceCount++
                existing.confidenceScore = (existing.confidenceScore + 0.2).coerceAtMost(1.0)
                existing.updatedAt = LocalDateTime.now()
                candidateRepository.save(existing)
            } else {
                candidateRepository.save(
                    CandidateGlossaryEntry(
                        sourceKo = word,
                        targetLang = targetLang,
                        proposedTranslation = translatedText,
                        confidenceScore = confidenceScore,
                        context = sourceText,
                        pageUrl = pageUrl ?: "",
                        occurrenceCount = 1
                    )
                )
            }
        }
    }

    private fun calculateConfidence(word: String, context: String, pageUrl: String?): Double {
        var score = 0.3 // Base score for unmatched term

        // +0.1 page context available
        if (!pageUrl.isNullOrBlank()) score += 0.1

        // +0.1 Korean 2+ syllables (not a particle)
        if (word.length >= 2 && word.any { it in '\uAC00'..'\uD7AF' }) score += 0.1

        // +0.1 likely noun (doesn't end with verb endings)
        if (!endsWithVerbSuffix(word)) score += 0.1

        // +0.1 complete word (appears with word boundaries in context)
        val wordBoundaryPattern = Regex("(?<=\\s|^)${Regex.escape(word)}(?=\\s|$|[,.!?;:])")
        if (wordBoundaryPattern.containsMatchIn(context)) score += 0.1

        return score.coerceAtMost(1.0)
    }

    private fun isParticleOrSuffix(word: String): Boolean {
        val particles = setOf(
            "은", "는", "이", "가", "을", "를", "에", "에서", "에게",
            "의", "와", "과", "도", "만", "로", "으로", "부터", "까지",
            "처럼", "보다", "한테", "에게서", "라고", "하고"
        )
        return word in particles
    }

    private fun endsWithVerbSuffix(word: String): Boolean {
        val verbEndings = listOf(
            "다", "하다", "되다", "시다", "이다",
            "해요", "해", "합니다", "했어요", "했다",
            "하는", "하게", "하여", "하면"
        )
        return verbEndings.any { word.endsWith(it) }
    }
}
