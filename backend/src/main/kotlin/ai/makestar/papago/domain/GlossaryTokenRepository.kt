package ai.makestar.papago.domain

import org.springframework.cache.annotation.Cacheable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface GlossaryTokenRepository : JpaRepository<GlossaryToken, Long> {
    @Cacheable("glossaryTokens")
    fun findByTokenIn(tokens: Collection<String>): List<GlossaryToken>
}
