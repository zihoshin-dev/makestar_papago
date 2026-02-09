package ai.makestar.papago.domain

import jakarta.persistence.*

@Entity
@Table(
    name = "glossary_multi_lang_token",
    indexes = [
        Index(name = "idx_multi_token_token_lang", columnList = "token,lang"),
        Index(name = "idx_multi_token_lang", columnList = "lang")
    ]
)
class GlossaryMultiLangToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, columnDefinition = "TEXT")
    val token: String,

    @Column(nullable = false, length = 10)
    val lang: String,

    @Column(nullable = false)
    val glossaryId: Long,

    @Column(nullable = false)
    val tokenLength: Int = token.length
)
