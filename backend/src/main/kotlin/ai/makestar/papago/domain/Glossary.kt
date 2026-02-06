package ai.makestar.papago.domain

import jakarta.persistence.*

@Entity
@Table(
    name = "glossary",
    indexes = [
        Index(name = "idx_glossary_ko", columnList = "ko"),
        Index(name = "idx_glossary_page_url", columnList = "pageUrl"),
        Index(name = "idx_glossary_key_name", columnList = "keyName")
    ]
)
class Glossary(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val pageUrl: String,
    val keyName: String,

    // System DB (기본 번역)
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
    val zhHant: String,

    // DeepL 번역 변형
    @Column(columnDefinition = "TEXT")
    val zhHantFromEn: String = "",

    @Column(columnDefinition = "TEXT")
    val zhHantFromKo: String = "",

    @Column(columnDefinition = "TEXT")
    val zhHansFromEn: String = "",

    @Column(columnDefinition = "TEXT")
    val zhHansFromKo: String = "",

    // 비즈니스팀 수정본
    @Column(columnDefinition = "TEXT")
    val zhHantTaiwan: String = "",

    @Column(columnDefinition = "TEXT")
    val zhHansChina: String = "",

    @Column(columnDefinition = "TEXT")
    val enNorthAmerica: String = "",

    @Column(columnDefinition = "TEXT")
    val jaJapan: String = ""
)
