package ai.makestar.papago.domain

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
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

    fun findByKoIn(koList: Collection<String>): List<Glossary>

    // Paginated queries for glossary API
    fun findByKoContaining(query: String, pageable: Pageable): Page<Glossary>

    fun findByPageUrl(pageUrl: String, pageable: Pageable): Page<Glossary>

    fun findByPageUrlStartingWith(pageUrlPrefix: String, pageable: Pageable): Page<Glossary>

    fun findByKoContainingAndPageUrl(query: String, pageUrl: String, pageable: Pageable): Page<Glossary>

    fun findByKoContainingAndPageUrlStartingWith(query: String, pageUrlPrefix: String, pageable: Pageable): Page<Glossary>

    // Multi-language search queries
    fun findByEn(en: String): List<Glossary>

    fun findByJa(ja: String): List<Glossary>

    @Query("SELECT g FROM Glossary g WHERE g.zhHans = :value")
    fun findByZhHans(value: String): List<Glossary>

    @Query("SELECT g FROM Glossary g WHERE g.zhHant = :value")
    fun findByZhHant(value: String): List<Glossary>

    fun findByEs(es: String): List<Glossary>

    fun findByDe(de: String): List<Glossary>

    fun findByFr(fr: String): List<Glossary>

    @Query("SELECT g FROM Glossary g WHERE LOWER(g.en) LIKE LOWER(CONCAT('%', :token, '%'))")
    fun findByEnLike(token: String): List<Glossary>

    @Query("SELECT g FROM Glossary g WHERE LOWER(g.ja) LIKE LOWER(CONCAT('%', :token, '%'))")
    fun findByJaLike(token: String): List<Glossary>

    @Query("SELECT g FROM Glossary g WHERE g.zhHans LIKE CONCAT('%', :token, '%')")
    fun findByZhHansLike(token: String): List<Glossary>

    @Query("SELECT g FROM Glossary g WHERE g.zhHant LIKE CONCAT('%', :token, '%')")
    fun findByZhHantLike(token: String): List<Glossary>

    @Query("SELECT g FROM Glossary g WHERE LOWER(g.es) LIKE LOWER(CONCAT('%', :token, '%'))")
    fun findByEsLike(token: String): List<Glossary>

    @Query("SELECT g FROM Glossary g WHERE LOWER(g.de) LIKE LOWER(CONCAT('%', :token, '%'))")
    fun findByDeLike(token: String): List<Glossary>

    @Query("SELECT g FROM Glossary g WHERE LOWER(g.fr) LIKE LOWER(CONCAT('%', :token, '%'))")
    fun findByFrLike(token: String): List<Glossary>

    @Query("SELECT g FROM Glossary g WHERE LOWER(g.en) = LOWER(:value)")
    fun findByEnIgnoreCase(value: String): List<Glossary>

    @Query("SELECT g FROM Glossary g WHERE LOWER(g.ja) = LOWER(:value)")
    fun findByJaIgnoreCase(value: String): List<Glossary>

    @Query("SELECT g FROM Glossary g WHERE LOWER(g.es) = LOWER(:value)")
    fun findByEsIgnoreCase(value: String): List<Glossary>

    @Query("SELECT g FROM Glossary g WHERE LOWER(g.de) = LOWER(:value)")
    fun findByDeIgnoreCase(value: String): List<Glossary>

    @Query("SELECT g FROM Glossary g WHERE LOWER(g.fr) = LOWER(:value)")
    fun findByFrIgnoreCase(value: String): List<Glossary>
}
