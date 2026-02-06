package ai.makestar.papago.service

import ai.makestar.papago.domain.Glossary
import ai.makestar.papago.domain.GlossaryRepository
import ai.makestar.papago.domain.GlossaryTokenRepository
import org.springframework.stereotype.Service

data class ScoredGlossary(
    val glossary: Glossary,
    val score: Double,
    val matchType: String
)

@Service
class GlossarySearchService(
    private val glossaryRepository: GlossaryRepository,
    private val glossaryTokenRepository: GlossaryTokenRepository,
    private val tokenizationService: TokenizationService
) {

    /**
     * 3-tier relevance search:
     * 1. Exact match (score: 100)
     * 2. Token-based match (score: up to 60, weighted by token length)
     * 3. LIKE fallback on individual words (score: 30)
     *
     * Page context bonus: +15 if pageUrl matches
     */
    fun search(inputText: String, pageUrl: String? = null, limit: Int = 10): List<ScoredGlossary> {
        val scoredResults = mutableMapOf<Long, ScoredGlossary>()
        val trimmedInput = inputText.trim()

        // Tier 1: Exact match
        val exactMatches = glossaryRepository.findByKo(trimmedInput)
        for (glossary in exactMatches) {
            val id = glossary.id ?: continue
            scoredResults[id] = ScoredGlossary(glossary, 100.0, "exact")
        }

        // Tier 2: Token-based match
        val inputTokens = tokenizationService.tokenize(trimmedInput)
        if (inputTokens.isNotEmpty()) {
            val matchedTokenEntries = glossaryTokenRepository.findByTokenIn(inputTokens)

            // Group by glossaryId, calculate score based on matched token lengths
            val glossaryTokenMatches = matchedTokenEntries.groupBy { it.glossaryId }

            for ((glossaryId, tokens) in glossaryTokenMatches) {
                if (scoredResults.containsKey(glossaryId)) continue // already exact matched

                // Score: longest matching token ratio * 60
                val longestTokenLength = tokens.maxOf { it.tokenLength }
                val matchCount = tokens.map { it.token }.toSet().size
                val lengthRatio = longestTokenLength.toDouble() / trimmedInput.length.coerceAtLeast(1)
                val countBonus = (matchCount - 1).coerceAtMost(5) * 2.0

                val score = (lengthRatio * 60.0 + countBonus).coerceAtMost(60.0)

                val glossary = glossaryRepository.findById(glossaryId).orElse(null) ?: continue
                scoredResults[glossaryId] = ScoredGlossary(glossary, score, "token")
            }
        }

        // Tier 3: LIKE fallback on individual words (if not enough results)
        if (scoredResults.size < limit) {
            val words = tokenizationService.splitIntoWords(trimmedInput)
            for (word in words) {
                if (word.length < 2) continue
                val likeMatches = glossaryRepository.findByKoLike(word)
                for (glossary in likeMatches) {
                    val id = glossary.id ?: continue
                    if (scoredResults.containsKey(id)) continue
                    scoredResults[id] = ScoredGlossary(glossary, 30.0, "like")
                }
                if (scoredResults.size >= limit * 2) break
            }
        }

        // Page context bonus
        if (!pageUrl.isNullOrBlank()) {
            for ((id, scored) in scoredResults) {
                if (scored.glossary.pageUrl == pageUrl) {
                    scoredResults[id] = scored.copy(score = scored.score + 15.0)
                }
            }
        }

        return scoredResults.values
            .sortedByDescending { it.score }
            .take(limit)
    }

    /**
     * Get the best translation for a given language, preferring business team corrections.
     */
    fun getBestTranslation(glossary: Glossary, targetLang: String): String {
        return when (targetLang.lowercase()) {
            "en" -> glossary.enNorthAmerica.ifBlank { glossary.en }
            "ja" -> glossary.jaJapan.ifBlank { glossary.ja }
            "zh-hans", "zh_hans" -> glossary.zhHansChina.ifBlank { glossary.zhHans }
            "zh-hant", "zh_hant" -> glossary.zhHantTaiwan.ifBlank { glossary.zhHant }
            "es" -> glossary.es
            else -> glossary.enNorthAmerica.ifBlank { glossary.en }
        }
    }
}
