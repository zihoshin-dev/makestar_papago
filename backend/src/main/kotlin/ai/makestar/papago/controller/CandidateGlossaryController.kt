package ai.makestar.papago.controller

import ai.makestar.papago.domain.CandidateGlossaryEntry
import ai.makestar.papago.domain.CandidateStatus
import ai.makestar.papago.service.CandidateApprovalService
import org.springframework.web.bind.annotation.*

data class CandidateResponse(
    val id: Long,
    val sourceKo: String,
    val targetLang: String,
    val proposedTranslation: String,
    val confidenceScore: Double,
    val context: String,
    val pageUrl: String,
    val occurrenceCount: Int,
    val status: String
)

data class CandidatePage(
    val content: List<CandidateResponse>,
    val totalPages: Int,
    val totalElements: Long,
    val number: Int,
    val size: Int
)

data class BatchRequest(
    val ids: List<Long>,
    val action: String
)

@RestController
@RequestMapping("/api/glossary/candidates")
class CandidateGlossaryController(
    private val candidateApprovalService: CandidateApprovalService
) {

    @GetMapping
    fun getCandidates(
        @RequestParam(defaultValue = "PENDING") status: String,
        @RequestParam(defaultValue = "0.0") minConfidence: Double,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int
    ): CandidatePage {
        val candidateStatus = try {
            CandidateStatus.valueOf(status.uppercase())
        } catch (_: IllegalArgumentException) {
            CandidateStatus.PENDING
        }

        val result = candidateApprovalService.getCandidates(candidateStatus, minConfidence, page, size)

        return CandidatePage(
            content = result.content.map { it.toResponse() },
            totalPages = result.totalPages,
            totalElements = result.totalElements,
            number = result.number,
            size = result.size
        )
    }

    @PostMapping("/{id}/approve")
    fun approve(@PathVariable id: Long): CandidateResponse {
        return candidateApprovalService.approve(id).toResponse()
    }

    @PostMapping("/{id}/reject")
    fun reject(@PathVariable id: Long): CandidateResponse {
        return candidateApprovalService.reject(id).toResponse()
    }

    @PostMapping("/batch")
    fun batchProcess(@RequestBody request: BatchRequest): List<CandidateResponse> {
        return candidateApprovalService.batchProcess(request.ids, request.action).map { it.toResponse() }
    }

    private fun CandidateGlossaryEntry.toResponse() = CandidateResponse(
        id = id ?: 0L,
        sourceKo = sourceKo,
        targetLang = targetLang,
        proposedTranslation = proposedTranslation,
        confidenceScore = confidenceScore,
        context = context,
        pageUrl = pageUrl,
        occurrenceCount = occurrenceCount,
        status = status.name
    )
}
