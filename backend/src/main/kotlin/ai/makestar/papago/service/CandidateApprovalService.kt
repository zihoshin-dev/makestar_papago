package ai.makestar.papago.service

import ai.makestar.papago.domain.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class CandidateApprovalService(
    private val candidateRepository: CandidateGlossaryEntryRepository,
    private val glossaryRepository: GlossaryRepository,
    private val tokenIndexService: TokenIndexService
) {

    fun getCandidates(
        status: CandidateStatus = CandidateStatus.PENDING,
        minConfidence: Double = 0.0,
        page: Int = 0,
        size: Int = 50
    ): Page<CandidateGlossaryEntry> {
        val pageable = PageRequest.of(page, size.coerceAtMost(100), Sort.by(Sort.Direction.DESC, "confidenceScore"))

        return if (minConfidence > 0.0) {
            candidateRepository.findByStatusAndConfidenceScoreGreaterThanEqual(status, minConfidence, pageable)
        } else {
            candidateRepository.findByStatus(status, pageable)
        }
    }

    @Transactional
    fun approve(candidateId: Long): CandidateGlossaryEntry {
        val candidate = candidateRepository.findById(candidateId)
            .orElseThrow { RuntimeException("Candidate not found: $candidateId") }

        // Create Glossary entry
        val keyName = candidate.sourceKo.lowercase().replace(Regex("[^a-zA-Z0-9가-힣]"), "_")
        val glossary = glossaryRepository.save(
            Glossary(
                pageUrl = candidate.pageUrl.ifBlank { "/auto-extracted" },
                keyName = keyName,
                ko = candidate.sourceKo,
                en = if (candidate.targetLang == "en") candidate.proposedTranslation else "",
                ja = if (candidate.targetLang == "ja") candidate.proposedTranslation else "",
                zhHans = if (candidate.targetLang in listOf("zh-hans", "zh_hans")) candidate.proposedTranslation else "",
                zhHant = if (candidate.targetLang in listOf("zh-hant", "zh_hant")) candidate.proposedTranslation else "",
                es = if (candidate.targetLang == "es") candidate.proposedTranslation else "",
                de = if (candidate.targetLang == "de") candidate.proposedTranslation else "",
                fr = if (candidate.targetLang == "fr") candidate.proposedTranslation else ""
            )
        )

        // Build token index for new entry
        tokenIndexService.addToIndex(glossary)

        // Update candidate status
        candidate.status = CandidateStatus.MERGED
        candidate.updatedAt = LocalDateTime.now()
        return candidateRepository.save(candidate)
    }

    @Transactional
    fun reject(candidateId: Long): CandidateGlossaryEntry {
        val candidate = candidateRepository.findById(candidateId)
            .orElseThrow { RuntimeException("Candidate not found: $candidateId") }

        candidate.status = CandidateStatus.REJECTED
        candidate.updatedAt = LocalDateTime.now()
        return candidateRepository.save(candidate)
    }

    @Transactional
    fun batchProcess(ids: List<Long>, action: String): List<CandidateGlossaryEntry> {
        val candidates = candidateRepository.findByIdIn(ids)
        return candidates.map { candidate ->
            when (action.uppercase()) {
                "APPROVE" -> approve(candidate.id!!)
                "REJECT" -> reject(candidate.id!!)
                else -> throw IllegalArgumentException("Invalid action: $action. Use APPROVE or REJECT.")
            }
        }
    }
}
