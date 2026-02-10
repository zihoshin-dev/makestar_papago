package ai.makestar.papago.controller

import ai.makestar.papago.service.TranslationResult
import ai.makestar.papago.service.TranslationService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/translate")
class TranslationController(
    private val translationService: TranslationService
) {
    @PostMapping
    fun translate(@Valid @RequestBody request: TranslationRequest): TranslationResult {
        return translationService.translate(request.text, request.targetLang, request.pageUrl, request.sourceLang)
    }

    @PostMapping("/batch")
    fun translateBatch(@Valid @RequestBody request: BatchTranslationRequest): Map<String, TranslationResult> {
        return translationService.translateBatch(request.text, request.targetLangs, request.pageUrl, request.sourceLang)
    }
}

data class TranslationRequest(
    val text: String,
    val targetLang: String,
    val pageUrl: String? = null,
    val sourceLang: String? = null  // null = auto-detect, "ko", "en", "ja", etc.
)

data class BatchTranslationRequest(
    val text: String,
    val targetLangs: List<String>,
    val pageUrl: String? = null,
    val sourceLang: String? = null
)
