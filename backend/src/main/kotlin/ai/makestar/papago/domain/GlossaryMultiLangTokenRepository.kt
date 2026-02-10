package ai.makestar.papago.domain

import org.springframework.cache.annotation.Cacheable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface GlossaryMultiLangTokenRepository : JpaRepository<GlossaryMultiLangToken, Long> {

    @Cacheable("glossaryMultiLangTokens")
    fun findByTokenInAndLang(tokens: Collection<String>, lang: String): List<GlossaryMultiLangToken>

    fun deleteByGlossaryId(glossaryId: Long)
}
