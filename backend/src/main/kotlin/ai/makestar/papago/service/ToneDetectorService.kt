package ai.makestar.papago.service

import org.springframework.stereotype.Service

enum class Tone {
    FORMAL,
    CASUAL,
    NEUTRAL
}

@Service
class ToneDetectorService {

    companion object {
        // Formal speech endings (높임말)
        private val FORMAL_ENDINGS = listOf(
            "합니다", "입니다", "습니다", "십시오", "세요", "주세요",
            "하십시오", "겠습니다", "였습니다", "됩니다", "있습니다",
            "없습니다", "봅니다", "드립니다", "올립니다"
        ).sortedByDescending { it.length }

        // Casual speech endings (반말)
        private val CASUAL_ENDINGS = listOf(
            "잖아", "거야", "하냐", "이야", "인데", "는데",
            "야", "해", "지", "냐", "나", "네",
            "어", "아", "래", "다", "함", "임"
        ).sortedByDescending { it.length }
    }

    /**
     * Detect the tone/formality of Korean text from sentence endings.
     * @param text The Korean text to analyze
     * @return Detected tone: FORMAL, CASUAL, or NEUTRAL
     */
    fun detectTone(text: String): Tone {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Tone.NEUTRAL

        // Split into sentences (by common sentence-ending punctuation or newlines)
        val sentences = trimmed.split(Regex("[.!?\\n]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (sentences.isEmpty()) return Tone.NEUTRAL

        var formalCount = 0
        var casualCount = 0

        for (sentence in sentences) {
            val cleaned = sentence.replace(Regex("[\\s,.!?;:()\\[\\]{}\"'~·…]+$"), "")
            if (cleaned.isEmpty()) continue

            when {
                FORMAL_ENDINGS.any { cleaned.endsWith(it) } -> formalCount++
                CASUAL_ENDINGS.any { cleaned.endsWith(it) } -> casualCount++
            }
        }

        return when {
            formalCount > casualCount -> Tone.FORMAL
            casualCount > formalCount -> Tone.CASUAL
            formalCount > 0 -> Tone.FORMAL // Tie-break: prefer formal
            else -> Tone.NEUTRAL
        }
    }
}
