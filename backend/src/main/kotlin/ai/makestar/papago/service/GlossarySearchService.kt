package ai.makestar.papago.service

import ai.makestar.papago.domain.Glossary
import ai.makestar.papago.domain.GlossaryMultiLangTokenRepository
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
    private val multiLangTokenRepository: GlossaryMultiLangTokenRepository,
    private val tokenizationService: TokenizationService,
    private val morphologyService: KoreanMorphologyService
) {

    /**
     * 4-tier relevance search:
     * 1. Exact match (score: 100)
     * 2. Stem-exact match (score: 90)
     * 3. Token-based match (score: up to 60, weighted by token length)
     * 4. LIKE fallback on individual words (score: 30)
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

        // Tier 2: Stem-exact match
        val words = tokenizationService.splitIntoWords(trimmedInput)
        val allStems = mutableSetOf<String>()
        for (word in words) {
            val stemCandidates = morphologyService.stem(word)
            allStems.addAll(stemCandidates)
        }
        if (allStems.isNotEmpty()) {
            val stemMatches = glossaryRepository.findByKoIn(allStems.toList())
            for (glossary in stemMatches) {
                val id = glossary.id ?: continue
                if (scoredResults.containsKey(id)) continue
                scoredResults[id] = ScoredGlossary(glossary, 90.0, "stem-exact")
            }
        }

        // Tier 3: Token-based match
        val inputTokens = tokenizationService.tokenize(trimmedInput)
        if (inputTokens.isNotEmpty()) {
            val matchedTokenEntries = glossaryTokenRepository.findByTokenIn(inputTokens)

            // Group by glossaryId, calculate score based on matched token lengths
            val glossaryTokenMatches = matchedTokenEntries.groupBy { it.glossaryId }

            // Batch load all glossaries to avoid N+1 queries
            val allGlossaryIds = glossaryTokenMatches.keys.filter { !scoredResults.containsKey(it) }
            val allGlossaries = glossaryRepository.findAllById(allGlossaryIds).associateBy { it.id }

            for ((glossaryId, tokens) in glossaryTokenMatches) {
                if (scoredResults.containsKey(glossaryId)) continue // already exact matched

                // Score: longest matching token ratio * 60
                val longestTokenLength = tokens.maxOf { it.tokenLength }
                val matchCount = tokens.map { it.token }.toSet().size
                val lengthRatio = longestTokenLength.toDouble() / trimmedInput.length.coerceAtLeast(1)
                val countBonus = (matchCount - 1).coerceAtMost(5) * 2.0

                val score = (lengthRatio * 60.0 + countBonus).coerceAtMost(60.0)

                val glossary = allGlossaries[glossaryId] ?: continue
                scoredResults[glossaryId] = ScoredGlossary(glossary, score, "token")
            }
        }

        // Tier 4: LIKE fallback on individual words (if not enough results)
        if (scoredResults.size < limit) {
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

        // Page context bonus (prefix match for sub-pages like /artist/:id)
        if (!pageUrl.isNullOrBlank()) {
            for ((id, scored) in scoredResults) {
                if (scored.glossary.pageUrl.startsWith(pageUrl) || pageUrl.startsWith(scored.glossary.pageUrl)) {
                    scoredResults[id] = scored.copy(score = scored.score + 15.0)
                }
            }
        }

        return scoredResults.values
            .sortedByDescending { it.score }
            .take(limit)
    }

    /**
     * Cross-language search: search glossary entries by non-Korean language.
     * 4-tier: exact -> lowercase -> token -> LIKE
     */
    fun searchByLanguage(inputText: String, searchLang: String, pageUrl: String? = null, limit: Int = 10): List<ScoredGlossary> {
        if (searchLang == "ko") return search(inputText, pageUrl, limit)

        val scoredResults = mutableMapOf<Long, ScoredGlossary>()
        val trimmedInput = inputText.trim()

        // Tier 1: Exact match on the language column
        val exactMatches = findExactByLang(trimmedInput, searchLang)
        for (glossary in exactMatches) {
            val id = glossary.id ?: continue
            scoredResults[id] = ScoredGlossary(glossary, 100.0, "exact")
        }

        // Tier 2: Lowercase match
        if (scoredResults.isEmpty()) {
            val lowerMatches = findByLangIgnoreCase(trimmedInput, searchLang)
            for (glossary in lowerMatches) {
                val id = glossary.id ?: continue
                if (scoredResults.containsKey(id)) continue
                scoredResults[id] = ScoredGlossary(glossary, 90.0, "lowercase")
            }
        }

        // Tier 3: Token-based match using multi-lang token index
        val inputTokens = tokenizationService.tokenizeMultiLang(trimmedInput, searchLang)
        if (inputTokens.isNotEmpty()) {
            val matchedTokenEntries = multiLangTokenRepository.findByTokenInAndLang(inputTokens, searchLang)
            val glossaryTokenMatches = matchedTokenEntries.groupBy { it.glossaryId }

            // Batch load all glossaries to avoid N+1 queries
            val allGlossaryIds = glossaryTokenMatches.keys.filter { !scoredResults.containsKey(it) }
            val allGlossaries = glossaryRepository.findAllById(allGlossaryIds).associateBy { it.id }

            for ((glossaryId, tokens) in glossaryTokenMatches) {
                if (scoredResults.containsKey(glossaryId)) continue

                val longestTokenLength = tokens.maxOf { it.tokenLength }
                val matchCount = tokens.map { it.token }.toSet().size
                val lengthRatio = longestTokenLength.toDouble() / trimmedInput.length.coerceAtLeast(1)
                val countBonus = (matchCount - 1).coerceAtMost(5) * 2.0
                val score = (lengthRatio * 60.0 + countBonus).coerceAtMost(60.0)

                val glossary = allGlossaries[glossaryId] ?: continue
                scoredResults[glossaryId] = ScoredGlossary(glossary, score, "token")
            }
        }

        // Tier 4: LIKE fallback
        if (scoredResults.size < limit && trimmedInput.length >= 2) {
            val likeMatches = findByLangLike(trimmedInput, searchLang)
            for (glossary in likeMatches) {
                val id = glossary.id ?: continue
                if (scoredResults.containsKey(id)) continue
                scoredResults[id] = ScoredGlossary(glossary, 30.0, "like")
            }
        }

        // Page context bonus
        if (!pageUrl.isNullOrBlank()) {
            for ((id, scored) in scoredResults) {
                if (scored.glossary.pageUrl.startsWith(pageUrl) || pageUrl.startsWith(scored.glossary.pageUrl)) {
                    scoredResults[id] = scored.copy(score = scored.score + 15.0)
                }
            }
        }

        return scoredResults.values
            .sortedByDescending { it.score }
            .take(limit)
    }

    private fun findExactByLang(text: String, lang: String): List<Glossary> {
        return when (lang.lowercase()) {
            "en" -> glossaryRepository.findByEn(text)
            "ja" -> glossaryRepository.findByJa(text)
            "zh-hans", "zh_hans" -> glossaryRepository.findByZhHans(text)
            "zh-hant", "zh_hant" -> glossaryRepository.findByZhHant(text)
            "es" -> glossaryRepository.findByEs(text)
            "de" -> glossaryRepository.findByDe(text)
            "fr" -> glossaryRepository.findByFr(text)
            else -> emptyList()
        }
    }

    private fun findByLangIgnoreCase(text: String, lang: String): List<Glossary> {
        return when (lang.lowercase()) {
            "en" -> glossaryRepository.findByEnIgnoreCase(text)
            "ja" -> glossaryRepository.findByJaIgnoreCase(text)
            "zh-hans", "zh_hans" -> glossaryRepository.findByZhHans(text) // CJK is case-insensitive
            "zh-hant", "zh_hant" -> glossaryRepository.findByZhHant(text)
            "es" -> glossaryRepository.findByEsIgnoreCase(text)
            "de" -> glossaryRepository.findByDeIgnoreCase(text)
            "fr" -> glossaryRepository.findByFrIgnoreCase(text)
            else -> emptyList()
        }
    }

    private fun findByLangLike(text: String, lang: String): List<Glossary> {
        return when (lang.lowercase()) {
            "en" -> glossaryRepository.findByEnLike(text)
            "ja" -> glossaryRepository.findByJaLike(text)
            "zh-hans", "zh_hans" -> glossaryRepository.findByZhHansLike(text)
            "zh-hant", "zh_hant" -> glossaryRepository.findByZhHantLike(text)
            "es" -> glossaryRepository.findByEsLike(text)
            "de" -> glossaryRepository.findByDeLike(text)
            "fr" -> glossaryRepository.findByFrLike(text)
            else -> emptyList()
        }
    }

    /**
     * Get the best translation for a given language, preferring business team corrections.
     */
    fun getBestTranslation(glossary: Glossary, targetLang: String): String {
        return when (targetLang.lowercase()) {
            "ko" -> glossary.ko
            "en" -> glossary.enNorthAmerica.ifBlank { glossary.en }
            "ja" -> glossary.jaJapan.ifBlank { glossary.ja }
            "zh-hans", "zh_hans" -> glossary.zhHansChina.ifBlank { glossary.zhHans }
            "zh-hant", "zh_hant" -> glossary.zhHantTaiwan.ifBlank { glossary.zhHant }
            "es" -> glossary.es
            "de" -> glossary.de
            "fr" -> glossary.fr
            else -> glossary.enNorthAmerica.ifBlank { glossary.en }
        }
    }
}
