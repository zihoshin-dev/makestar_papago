package ai.makestar.papago.service

import ai.makestar.papago.domain.Glossary
import ai.makestar.papago.domain.GlossaryMultiLangToken
import ai.makestar.papago.domain.GlossaryMultiLangTokenRepository
import ai.makestar.papago.domain.GlossaryToken
import ai.makestar.papago.domain.GlossaryTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class TokenIndexService(
    private val glossaryTokenRepository: GlossaryTokenRepository,
    private val multiLangTokenRepository: GlossaryMultiLangTokenRepository,
    private val tokenizationService: TokenizationService
) {
    private val logger = LoggerFactory.getLogger(TokenIndexService::class.java)

    companion object {
        private val MULTI_LANG_COLUMNS = listOf("en", "ja", "zh-hans", "zh-hant", "es", "de", "fr")
    }

    fun buildTokenIndex(glossaries: List<Glossary>) {
        val tokens = mutableListOf<GlossaryToken>()

        for (glossary in glossaries) {
            val glossaryId = glossary.id ?: continue
            val koTokens = tokenizationService.tokenizeForIndex(glossary.ko)

            for (token in koTokens) {
                tokens.add(
                    GlossaryToken(
                        token = token,
                        glossaryId = glossaryId,
                        tokenLength = token.length
                    )
                )
            }
        }

        glossaryTokenRepository.saveAll(tokens)
        logger.info("Built token index: ${tokens.size} tokens for ${glossaries.size} glossary items.")
    }

    fun buildMultiLangIndex(glossaries: List<Glossary>) {
        val tokens = mutableListOf<GlossaryMultiLangToken>()

        for (glossary in glossaries) {
            val glossaryId = glossary.id ?: continue

            for (lang in MULTI_LANG_COLUMNS) {
                val text = getTextForLang(glossary, lang)
                if (text.isBlank()) continue

                val langTokens = tokenizationService.tokenizeMultiLang(text, lang)
                for (token in langTokens) {
                    tokens.add(
                        GlossaryMultiLangToken(
                            token = token,
                            lang = lang,
                            glossaryId = glossaryId,
                            tokenLength = token.length
                        )
                    )
                }
            }
        }

        multiLangTokenRepository.saveAll(tokens)
        logger.info("Built multi-lang token index: ${tokens.size} tokens for ${glossaries.size} glossary items.")
    }

    fun addToIndex(glossary: Glossary) {
        val glossaryId = glossary.id ?: return
        val koTokens = tokenizationService.tokenizeForIndex(glossary.ko)

        val tokens = koTokens.map { token ->
            GlossaryToken(
                token = token,
                glossaryId = glossaryId,
                tokenLength = token.length
            )
        }
        glossaryTokenRepository.saveAll(tokens)

        // Also build multi-lang tokens
        val multiTokens = mutableListOf<GlossaryMultiLangToken>()
        for (lang in MULTI_LANG_COLUMNS) {
            val text = getTextForLang(glossary, lang)
            if (text.isBlank()) continue

            val langTokens = tokenizationService.tokenizeMultiLang(text, lang)
            for (token in langTokens) {
                multiTokens.add(
                    GlossaryMultiLangToken(
                        token = token,
                        lang = lang,
                        glossaryId = glossaryId,
                        tokenLength = token.length
                    )
                )
            }
        }
        if (multiTokens.isNotEmpty()) {
            multiLangTokenRepository.saveAll(multiTokens)
        }
    }

    private fun getTextForLang(glossary: Glossary, lang: String): String {
        return when (lang) {
            "en" -> glossary.enNorthAmerica.ifBlank { glossary.en }
            "ja" -> glossary.jaJapan.ifBlank { glossary.ja }
            "zh-hans" -> glossary.zhHansChina.ifBlank { glossary.zhHans }
            "zh-hant" -> glossary.zhHantTaiwan.ifBlank { glossary.zhHant }
            "es" -> glossary.es
            "de" -> glossary.de
            "fr" -> glossary.fr
            else -> ""
        }
    }
}
