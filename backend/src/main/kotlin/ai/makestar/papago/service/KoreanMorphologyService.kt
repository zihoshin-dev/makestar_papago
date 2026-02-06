package ai.makestar.papago.service

import org.springframework.stereotype.Service

/**
 * Rule-based Korean morphology service for particle stripping and verb normalization.
 * Uses Hangul Unicode structure (U+AC00..U+D7A3) for character analysis.
 */
@Service
class KoreanMorphologyService {

    companion object {
        // Hangul syllable structure constants
        private const val HANGUL_BASE = 0xAC00
        private const val HANGUL_END = 0xD7A3
        private const val CHOSUNG_COUNT = 19
        private const val JUNGSUNG_COUNT = 21
        private const val JONGSUNG_COUNT = 28

        // Particles (sorted by length descending for greedy matching)
        private val COMPLEX_PARTICLES = listOf(
            "에서는", "으로부터", "에게는", "까지는", "에서도", "처럼", "같이", "대로", "보다", "밖에", "에게서",
            "부터", "까지", "마저", "조차"
        )

        private val PARTICLES_NO_BATCHIM = listOf(
            "에서", "에게", "를", "가", "는", "로", "와", "야", "여", "도", "만", "의", "라"
        )

        private val PARTICLES_WITH_BATCHIM = listOf(
            "에서", "에게", "을", "이", "은", "으로", "과", "아", "도", "만", "의"
        )

        // Verb/adjective suffixes (sorted by length descending)
        private val VERB_SUFFIXES = listOf(
            // -하다 variations
            "합니다", "하세요", "했던", "하기", "하는", "하게", "해서", "하여", "하고", "하면", "한다", "해요", "할",
            // -되다 variations
            "됩니다", "되다", "되는", "되어", "되고", "되면", "돼요", "된",
            // Adjectival/nominal suffixes
            "스러운", "스럽게", "적",
            // Status markers
            "중", "시"
        ).sortedByDescending { it.length }

        private const val MIN_STEM_LENGTH = 1
    }

    /**
     * Check if a Hangul syllable has a final consonant (종성).
     * @param char The character to check
     * @return true if the character has a 종성, false otherwise
     */
    fun hasBatchim(char: Char): Boolean {
        if (char.code !in HANGUL_BASE..HANGUL_END) {
            return false
        }
        val syllableIndex = char.code - HANGUL_BASE
        val jongsung = syllableIndex % JONGSUNG_COUNT
        return jongsung != 0
    }

    /**
     * Decompose a Hangul syllable into 초성/중성/종성 indices.
     * @param char The Hangul character to decompose
     * @return Triple of (초성 index, 중성 index, 종성 index) or null if not Hangul
     */
    fun decomposeHangul(char: Char): Triple<Int, Int, Int>? {
        if (char.code !in HANGUL_BASE..HANGUL_END) {
            return null
        }
        val syllableIndex = char.code - HANGUL_BASE
        val chosung = syllableIndex / (JUNGSUNG_COUNT * JONGSUNG_COUNT)
        val jungsung = (syllableIndex % (JUNGSUNG_COUNT * JONGSUNG_COUNT)) / JONGSUNG_COUNT
        val jongsung = syllableIndex % JONGSUNG_COUNT
        return Triple(chosung, jungsung, jongsung)
    }

    /**
     * Strip Korean particles from the end of a word.
     * @param word The word to process
     * @return List of possible stems after particle removal
     */
    fun stripParticles(word: String): List<String> {
        if (word.isEmpty()) {
            return emptyList()
        }

        val results = mutableSetOf<String>()

        // Try complex particles first (longest match)
        for (particle in COMPLEX_PARTICLES) {
            if (word.endsWith(particle)) {
                val stem = word.dropLast(particle.length)
                if (stem.length >= MIN_STEM_LENGTH) {
                    results.add(stem)
                }
            }
        }

        // Check if last character has batchim
        val lastChar = word.last()
        val hasBatchim = hasBatchim(lastChar)

        // Try appropriate particle list based on batchim
        val particlesToTry = if (hasBatchim) PARTICLES_WITH_BATCHIM else PARTICLES_NO_BATCHIM

        for (particle in particlesToTry.sortedByDescending { it.length }) {
            if (word.endsWith(particle)) {
                val stem = word.dropLast(particle.length)
                if (stem.length >= MIN_STEM_LENGTH) {
                    results.add(stem)
                }
            }
        }

        return results.toList()
    }

    /**
     * Normalize verb/adjective forms by extracting the root.
     * @param word The word to normalize
     * @return List of possible verb roots
     */
    fun normalizeVerb(word: String): List<String> {
        if (word.isEmpty()) {
            return emptyList()
        }

        val results = mutableSetOf<String>()

        // Try all verb suffixes (already sorted by length descending)
        for (suffix in VERB_SUFFIXES) {
            if (word.endsWith(suffix)) {
                val root = word.dropLast(suffix.length)
                if (root.length >= MIN_STEM_LENGTH) {
                    results.add(root)
                }
            }
        }

        return results.toList()
    }

    /**
     * Main stemming function that applies both particle stripping and verb normalization.
     * @param word The word to stem
     * @return List of all possible stem candidates (deduplicated)
     */
    fun stem(word: String): List<String> {
        if (word.isEmpty()) {
            return emptyList()
        }

        val results = mutableSetOf<String>()

        // Always include the original word
        results.add(word)

        // Strip particles
        val particleStripped = stripParticles(word)
        results.addAll(particleStripped)

        // Normalize verbs on original word
        val verbNormalized = normalizeVerb(word)
        results.addAll(verbNormalized)

        // Normalize verbs on particle-stripped forms
        for (stripped in particleStripped) {
            val verbForms = normalizeVerb(stripped)
            results.addAll(verbForms)
        }

        // Filter out empty strings
        return results.filter { it.isNotEmpty() }.toList()
    }
}
