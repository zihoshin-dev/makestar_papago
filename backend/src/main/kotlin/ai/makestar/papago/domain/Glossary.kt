package ai.makestar.papago.domain

import jakarta.persistence.*

@Entity
@Table(name = "glossary")
class Glossary(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val pageUrl: String,
    val keyName: String,

    @Column(columnDefinition = "TEXT")
    val ko: String,

    @Column(columnDefinition = "TEXT")
    val en: String,

    @Column(columnDefinition = "TEXT")
    val zhHans: String,

    @Column(columnDefinition = "TEXT")
    val ja: String,

    @Column(columnDefinition = "TEXT")
    val es: String,

    @Column(columnDefinition = "TEXT")
    val zhHant: String
)
