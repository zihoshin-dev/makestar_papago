package ai.makestar.papago.service

import org.springframework.stereotype.Service

data class ValidationResult(
    val isValid: Boolean,
    val reason: String? = null
)

@Service
class InputValidationService {

    companion object {
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

        // Check purely numeric/symbol input (no actual letters including Korean jamo)
        val nonSpaceChars = trimmed.filter { !it.isWhitespace() }
        val hasLetterOrKorean = nonSpaceChars.any {
            it.isLetter() || it in '\u3131'..'\u318E' // Korean jamo range (consonants + vowels)
        }
        if (!hasLetterOrKorean) {
            return ValidationResult(false, "NO_LETTERS")
        }

        return ValidationResult(true)
    }

    fun getErrorMessage(reason: String): String {
        return when (reason) {
            "EMPTY" -> "텍스트를 입력해 주세요."
            "TOO_SHORT" -> "번역할 수 있는 텍스트를 입력해 주세요."
            "NO_LETTERS" -> "번역할 수 있는 텍스트를 입력해 주세요."
            else -> "번역할 수 없는 텍스트예요."
        }
    }
}
