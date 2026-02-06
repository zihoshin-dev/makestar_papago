package ai.makestar.papago.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface GlossaryRepository : JpaRepository<Glossary, Long> {
    fun findByKoContaining(query: String): List<Glossary>

    fun findByKo(ko: String): List<Glossary>

    fun findByPageUrl(pageUrl: String): List<Glossary>

    @Query("SELECT g FROM Glossary g WHERE g.ko LIKE %:token%")
    fun findByKoLike(token: String): List<Glossary>
}
