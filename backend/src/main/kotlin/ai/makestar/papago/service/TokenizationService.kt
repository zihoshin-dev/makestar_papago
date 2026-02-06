package ai.makestar.papago.service

import org.springframework.stereotype.Service

@Service
class TokenizationService {

    /**
     * Tokenize input text into searchable tokens.
     * 1. Split by whitespace and punctuation
     * 2. Generate adjacent word combinations (bigrams, trigrams)
     * 3. Generate character n-grams (2-8 chars) for each word
     */
    fun tokenize(text: String): Set<String> {
        val words = splitIntoWords(text)
        if (words.isEmpty()) return emptySet()

        val tokens = mutableSetOf<String>()

        // Add individual words
        tokens.addAll(words)

        // Add bigrams (adjacent 2-word combinations)
        for (i in 0 until words.size - 1) {
            tokens.add(words[i] + words[i + 1])
        }

        // Add trigrams (adjacent 3-word combinations)
        for (i in 0 until words.size - 2) {
            tokens.add(words[i] + words[i + 1] + words[i + 2])
        }

        // Add character n-grams (2-8 chars) for each word longer than 2 chars
        for (word in words) {
            tokens.addAll(generateNgrams(word, 2, 8))
        }

        return tokens.filter { it.length >= 2 }.toSet()
    }

    /**
     * Tokenize glossary Korean text for indexing.
     * More aggressive: includes the full text as a token too.
     */
    fun tokenizeForIndex(text: String): Set<String> {
        val tokens = tokenize(text).toMutableSet()
        val trimmed = text.trim()
        if (trimmed.length >= 2) {
            tokens.add(trimmed)
        }
        return tokens
    }

    fun splitIntoWords(text: String): List<String> {
        return text.split(SPLIT_PATTERN)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun generateNgrams(word: String, minN: Int, maxN: Int): Set<String> {
        val ngrams = mutableSetOf<String>()
        for (n in minN..minOf(maxN, word.length)) {
            for (i in 0..word.length - n) {
                ngrams.add(word.substring(i, i + n))
            }
        }
        return ngrams
    }

    companion object {
        private val SPLIT_PATTERN = Regex("[\\s,.!?;:()\\[\\]{}\"'~·…/\\\\|@#\$%^&*+=<>]+")
    }
}
