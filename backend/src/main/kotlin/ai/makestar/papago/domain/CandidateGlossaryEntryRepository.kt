package ai.makestar.papago.domain

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CandidateGlossaryEntryRepository : JpaRepository<CandidateGlossaryEntry, Long> {

    fun findByStatus(status: CandidateStatus, pageable: Pageable): Page<CandidateGlossaryEntry>

    fun findByStatusAndConfidenceScoreGreaterThanEqual(
        status: CandidateStatus,
        minConfidence: Double,
        pageable: Pageable
    ): Page<CandidateGlossaryEntry>

    fun findBySourceKoAndTargetLang(sourceKo: String, targetLang: String): CandidateGlossaryEntry?

    fun findByIdIn(ids: List<Long>): List<CandidateGlossaryEntry>
}
