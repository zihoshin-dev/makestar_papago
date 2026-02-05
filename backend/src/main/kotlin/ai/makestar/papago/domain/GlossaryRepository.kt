package ai.makestar.papago.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface GlossaryRepository : JpaRepository<Glossary, Long> {
    fun findByKoContaining(query: String): List<Glossary>
}
