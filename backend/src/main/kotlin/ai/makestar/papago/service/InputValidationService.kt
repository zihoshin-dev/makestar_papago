package ai.makestar.papago.service

import org.springframework.stereotype.Service

data class ValidationResult(
    val isValid: Boolean,
    val reason: String? = null
)

@Service
class InputValidationService {

    companion object {
        // Korean consonants (jamo)
        private val KOREAN_CONSONANTS = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ".toSet()
        // Korean vowels (jamo)
        private val KOREAN_VOWELS = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ".toSet()
        // Minimum meaningful text length
        private const val MIN_MEANINGFUL_LENGTH = 2
    }

    fun validate(text: String): ValidationResult {
        val trimmed = text.trim()

        if (trimmed.isEmpty()) {
            return ValidationResult(false, "EMPTY")
        }

        if (trimmed.length < MIN_MEANINGFUL_LENGTH) {
            return ValidationResult(false, "TOO_SHORT")
        }

        // Check consonant-only input (e.g., ㅇㅇㅇ, ㅋㅋㅋ)
        val nonSpaceChars = trimmed.filter { !it.isWhitespace() }
        if (nonSpaceChars.all { it in KOREAN_CONSONANTS }) {
            return ValidationResult(false, "CONSONANTS_ONLY")
        }

        // Check vowel-only input (e.g., ㅏㅏㅏ)
        if (nonSpaceChars.all { it in KOREAN_VOWELS }) {
            return ValidationResult(false, "VOWELS_ONLY")
        }

        // Check single repeated character (e.g., aaaa, ㅋㅋㅋㅋ, 1111)
        if (nonSpaceChars.length >= 3 && nonSpaceChars.toSet().size == 1) {
            return ValidationResult(false, "REPEATED_CHAR")
        }

        // Check purely numeric/symbol input (no actual letters)
        if (nonSpaceChars.none { it.isLetter() }) {
            return ValidationResult(false, "NO_LETTERS")
        }

        return ValidationResult(true)
    }

    fun getErrorMessage(reason: String): String {
        return when (reason) {
            "EMPTY" -> "텍스트를 입력해 주세요."
            "TOO_SHORT" -> "번역할 수 있는 텍스트를 입력해 주세요."
            "CONSONANTS_ONLY" -> "자음만으로는 번역할 수 없어요."
            "VOWELS_ONLY" -> "모음만으로는 번역할 수 없어요."
            "REPEATED_CHAR" -> "의미 있는 텍스트를 입력해 주세요."
            "NO_LETTERS" -> "번역할 수 있는 텍스트를 입력해 주세요."
            else -> "번역할 수 없는 텍스트예요."
        }
    }
}
