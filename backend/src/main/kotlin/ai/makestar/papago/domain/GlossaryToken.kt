package ai.makestar.papago.domain

import jakarta.persistence.*

@Entity
@Table(
    name = "glossary_token",
    indexes = [
        Index(name = "idx_glossary_token_token", columnList = "token")
    ]
)
class GlossaryToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val token: String,

    @Column(nullable = false)
    val glossaryId: Long,

    @Column(nullable = false)
    val tokenLength: Int = token.length
)
