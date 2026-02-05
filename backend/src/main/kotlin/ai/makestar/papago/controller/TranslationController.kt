package ai.makestar.papago.controller

import ai.makestar.papago.service.TranslationResult
import ai.makestar.papago.service.TranslationService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/translate")
class TranslationController(
    private val translationService: TranslationService
) {
    @PostMapping
    fun translate(@RequestBody request: TranslationRequest): TranslationResult {
        return translationService.translate(request.text, request.targetLang)
    }
}

data class TranslationRequest(
    val text: String,
    val targetLang: String
)
