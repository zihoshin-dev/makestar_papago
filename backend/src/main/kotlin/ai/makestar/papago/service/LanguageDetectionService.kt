package ai.makestar.papago.service

import org.springframework.stereotype.Service

@Service
class LanguageDetectionService {

    fun detectLanguage(text: String): String {
        val cleaned = text.filter { !it.isWhitespace() && !it.isDigit() && it !in PUNCTUATION_CHARS }
        if (cleaned.isEmpty()) return "en"

        var hangulCount = 0
        var cjkCount = 0
        var hiraganaKatakanaCount = 0
        var latinCount = 0

        for (ch in cleaned) {
            when {
                isHangul(ch) -> hangulCount++
                isHiraganaOrKatakana(ch) -> hiraganaKatakanaCount++
                isCJK(ch) -> cjkCount++
                isLatin(ch) -> latinCount++
            }
        }

        val total = cleaned.length.coerceAtLeast(1)

        return when {
            hangulCount.toDouble() / total > 0.3 -> "ko"
            hiraganaKatakanaCount.toDouble() / total > 0.2 -> "ja"
            cjkCount.toDouble() / total > 0.3 -> "zh"
            else -> "en"
        }
    }

    private fun isHangul(ch: Char): Boolean {
        return ch in '\uAC00'..'\uD7AF' || ch in '\u3131'..'\u318E' || ch in '\u1100'..'\u11FF'
    }

    private fun isHiraganaOrKatakana(ch: Char): Boolean {
        return ch in '\u3040'..'\u309F' || ch in '\u30A0'..'\u30FF'
    }

    private fun isCJK(ch: Char): Boolean {
        return ch in '\u4E00'..'\u9FFF' || ch in '\u3400'..'\u4DBF'
    }

    private fun isLatin(ch: Char): Boolean {
        return ch in 'A'..'Z' || ch in 'a'..'z' || ch in '\u00C0'..'\u024F'
    }

    companion object {
        private val PUNCTUATION_CHARS = setOf(
            '.', ',', '!', '?', ';', ':', '(', ')', '[', ']', '{', '}',
            '"', '\'', '-', '~', '/', '\\', '|', '@', '#', '$', '%',
            '^', '&', '*', '+', '=', '<', '>'
        )
    }
}
