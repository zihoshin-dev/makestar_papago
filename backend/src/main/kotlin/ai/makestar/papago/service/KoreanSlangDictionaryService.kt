package ai.makestar.papago.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import jakarta.annotation.PostConstruct

data class SlangEntry(
    val input: String,
    val meaning: String,
    val en: String,
    val ja: String,
    val zhHans: String,
    val zhHant: String,
    val es: String,
    val intensity: String
)

data class SlangResult(
    val entry: SlangEntry,
    val translatedText: String,
    val intensity: String
)

@Service
class KoreanSlangDictionaryService(
    private val objectMapper: ObjectMapper
) {
    private val slangMap = mutableMapOf<String, SlangEntry>()
    // Map of base patterns for repeated character matching (e.g., ㅋ -> ㅋㅋ entries)
    private val repeatPatterns = mutableMapOf<Char, List<SlangEntry>>()

    @PostConstruct
    fun init() {
        try {
            val resource = ClassPathResource("data/slang_dictionary.json")
            val entries: List<SlangEntry> = objectMapper.readValue(resource.inputStream)
            for (entry in entries) {
                slangMap[entry.input] = entry
            }
            // Build repeat patterns: group entries by their first character
            // e.g., 'ㅋ' -> [ㅋㅋ(normal), ㅋㅋㅋ(normal), ㅋㅋㅋㅋ(high), ...]
            slangMap.values
                .filter { it.input.length >= 2 && it.input.all { c -> c == it.input[0] } }
                .groupBy { it.input[0] }
                .forEach { (char, entries) ->
                    repeatPatterns[char] = entries.sortedByDescending { it.input.length }
                }
        } catch (e: Exception) {
            // Log but don't fail - slang dictionary is optional
            println("Warning: Could not load slang dictionary: ${e.message}")
        }
    }

    /**
     * Check if the input is composed entirely of Korean jamo (consonants/vowels).
     */
    fun isJamoOnly(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        return trimmed.all { it.isWhitespace() || it in '\u3131'..'\u318E' }
    }

    /**
     * Look up slang in the dictionary. Handles repeated characters.
     * @param text The input text to look up
     * @param targetLang The target language code (en, ja, zh-hans, zh-hant, es)
     * @return SlangResult if found, null otherwise
     */
    fun lookup(text: String, targetLang: String): SlangResult? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        // Direct match first
        val directMatch = slangMap[trimmed]
        if (directMatch != null) {
            val translation = getTranslation(directMatch, targetLang)
            return SlangResult(directMatch, translation, directMatch.intensity)
        }

        // Try repeated character matching
        // e.g., "ㅋㅋㅋㅋㅋㅋㅋ" -> find the longest matching ㅋ pattern
        if (trimmed.length >= 2 && trimmed.all { it == trimmed[0] }) {
            val baseChar = trimmed[0]
            val patterns = repeatPatterns[baseChar]
            if (patterns != null) {
                // Find the closest match by length (prefer longer)
                val bestMatch = patterns.firstOrNull { it.input.length <= trimmed.length }
                    ?: patterns.lastOrNull()
                if (bestMatch != null) {
                    // If input is longer than longest pattern, use high intensity
                    val intensity = if (trimmed.length > (patterns.firstOrNull()?.input?.length ?: 0)) "high" else bestMatch.intensity
                    val translation = getTranslation(bestMatch, targetLang)
                    return SlangResult(bestMatch, translation, intensity)
                }
            }
        }

        return null
    }

    private fun getTranslation(entry: SlangEntry, targetLang: String): String {
        return when (targetLang.lowercase()) {
            "en" -> entry.en
            "ja" -> entry.ja
            "zh-hans", "zh_hans" -> entry.zhHans
            "zh-hant", "zh_hant" -> entry.zhHant
            "es" -> entry.es
            else -> entry.en
        }
    }
}
