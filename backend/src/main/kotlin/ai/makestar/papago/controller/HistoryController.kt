package ai.makestar.papago.controller

import ai.makestar.papago.domain.TranslationHistory
import ai.makestar.papago.domain.TranslationHistoryRepository
import ai.makestar.papago.domain.TranslationStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/history")
class HistoryController(
    private val historyRepository: TranslationHistoryRepository
) {
    @GetMapping
    fun getHistory(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) status: String?
    ): Page<HistoryResponse> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))

        val historyPage = if (status != null) {
            historyRepository.findByStatus(TranslationStatus.valueOf(status.uppercase()), pageable)
        } else {
            historyRepository.findAllByOrderByCreatedAtDesc(pageable)
        }

        return historyPage.map { it.toResponse() }
    }
}

data class HistoryResponse(
    val id: Long,
    val sourceText: String,
    val targetLang: String,
    val translatedText: String,
    val status: String,
    val userCorrectedText: String?,
    val rating: Int?,
    val createdAt: String,
    val reviewedAt: String?,
    val isFromCache: Boolean
)

private fun TranslationHistory.toResponse() = HistoryResponse(
    id = this.id!!,
    sourceText = this.sourceText,
    targetLang = this.targetLang,
    translatedText = this.translatedText,
    status = this.status.name,
    userCorrectedText = this.userCorrectedText,
    rating = this.rating,
    createdAt = this.createdAt.toString(),
    reviewedAt = this.reviewedAt?.toString(),
    isFromCache = this.isFromCache
)
