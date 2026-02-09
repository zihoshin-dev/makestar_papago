package ai.makestar.papago.domain

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ApprovedTranslationRepository : JpaRepository<ApprovedTranslation, Long> {
    fun findBySourceTextHashAndTargetLang(sourceTextHash: String, targetLang: String): ApprovedTranslation?

    fun findByTargetLangOrderByUsageCountDesc(targetLang: String): List<ApprovedTranslation>

    fun findByTargetLangOrderByUsageCountDesc(targetLang: String, pageable: Pageable): List<ApprovedTranslation>
}
