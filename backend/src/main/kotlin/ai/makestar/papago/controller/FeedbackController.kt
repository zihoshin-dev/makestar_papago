package ai.makestar.papago.controller

import ai.makestar.papago.domain.TranslationStatus
import ai.makestar.papago.service.FeedbackService
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/feedback")
class FeedbackController(
    private val feedbackService: FeedbackService
) {
    @PostMapping("/{historyId}")
    fun submitFeedback(
        @PathVariable historyId: Long,
        @Valid @RequestBody request: FeedbackRequest
    ): ResponseEntity<Map<String, String>> {
        feedbackService.submitFeedback(
            historyId = historyId,
            status = TranslationStatus.valueOf(request.status.uppercase()),
            rating = request.rating,
            correctedText = request.correctedText
        )
        return ResponseEntity.ok(mapOf("message" to "Feedback submitted"))
    }
}

data class FeedbackRequest(
    val status: String,
    @field:Min(1) @field:Max(5)
    val rating: Int? = null,
    val correctedText: String? = null
)
