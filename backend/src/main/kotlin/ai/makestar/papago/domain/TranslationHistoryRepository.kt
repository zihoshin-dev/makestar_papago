package ai.makestar.papago.domain

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TranslationHistoryRepository : JpaRepository<TranslationHistory, Long> {
    fun findByStatus(status: TranslationStatus, pageable: Pageable): Page<TranslationHistory>
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<TranslationHistory>
}
