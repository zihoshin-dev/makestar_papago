package ai.makestar.papago.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface GlossaryTokenRepository : JpaRepository<GlossaryToken, Long> {
    fun findByTokenIn(tokens: Collection<String>): List<GlossaryToken>
}
